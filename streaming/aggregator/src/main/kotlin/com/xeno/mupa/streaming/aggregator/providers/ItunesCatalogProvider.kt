package com.xeno.mupa.streaming.aggregator.providers

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.streaming.api.CatalogProvider
import com.xeno.mupa.streaming.api.StreamingError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * CatalogProvider metadata-only berbasis Apple iTunes Search API (`itunes.apple.com/search?country=ID`).
 * - Tanpa login, tanpa API key, tanpa DRM; implementasi clean-room Apache-2.0.
 * - Memberikan cover art HD 600x600, durasi milidetik presisi, dan cakupan katalog Indonesia + global yang kuat.
 * - TIDAK melakukan streaming audio penuh (hanya katalog & metadata).
 */
class ItunesCatalogProvider(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://itunes.apple.com",
    private val countryCode: String = "ID",
) : CatalogProvider {

    override val providerId: String = PROVIDER_ID
    override val catalogPriority: Int = 20

    override suspend fun searchTracks(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/search?term=$encoded&country=$countryCode&media=music&entity=song&limit=${limit.coerceIn(1, 25)}"
            fetchJson(url).fold(
                onSuccess = { parseTracksResponse(it, limit) },
                onFailure = { Result.failure(it) },
            )
        }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext getTrendingArtists(limit)
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            // Mengambil entity=song memberi nama artis sekaligus artwork album HD untuk fallback foto artis
            val url = "$baseUrl/search?term=$encoded&country=$countryCode&media=music&entity=song&limit=${limit.coerceIn(1, 25)}"
            fetchJson(url).fold(
                onSuccess = { parseArtistsFromTracksResponse(it, limit) },
                onFailure = { Result.failure(it) },
            )
        }

    override suspend fun getArtistTopTracks(artistId: String, artistName: String): Result<List<Track>> =
        searchTracks(artistName, limit = 12)

    override suspend fun getTrendingArtists(limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/search?term=Pop+Indonesia&country=$countryCode&media=music&entity=song&limit=${limit.coerceIn(1, 25)}"
            fetchJson(url).fold(
                onSuccess = { parseArtistsFromTracksResponse(it, limit) },
                onFailure = { Result.failure(it) },
            )
        }

    private fun fetchJson(url: String): Result<String> {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()
            .build()
        return try {
            httpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                when (resp.code) {
                    in 200..299 -> Result.success(body)
                    429 -> Result.failure(StreamingError.RateLimited(resp.header("Retry-After")?.toLongOrNull()))
                    else -> Result.failure(StreamingError.Network("iTunes HTTP ${resp.code}"))
                }
            }
        } catch (io: IOException) {
            Result.failure(StreamingError.Network("Koneksi iTunes gagal: ${io.message}", io))
        }
    }

    companion object {
        const val PROVIDER_ID = "itunes"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        private val sizeRegex = Regex("""/\d+x\d+bb\.""")

        fun upscaleArtwork(rawUrl: String?): String? {
            val url = rawUrl?.trim()?.takeIf { it.startsWith("http") } ?: return null
            return if (sizeRegex.containsMatchIn(url)) {
                sizeRegex.replace(url, "/600x600bb.")
            } else {
                url
            }
        }

        fun parseTracksResponse(rawJson: String, limit: Int = 20): Result<List<Track>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON iTunes tidak valid", it)) }
            val results = root["results"]?.asArr().orEmpty()
            val tracks = results.mapNotNull { item ->
                val obj = item.asObj() ?: return@mapNotNull null
                val trackId = obj["trackId"]?.asStr()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = obj["trackName"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artist = obj["artistName"]?.asStr()?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Artis Tidak Diketahui"
                val album = obj["collectionName"]?.asStr()?.trim()?.takeIf { it.isNotBlank() }
                val durationMs = obj["trackTimeMillis"]?.asLong() ?: 0L
                val artwork = upscaleArtwork(obj["artworkUrl100"]?.asStr() ?: obj["artworkUrl60"]?.asStr())
                Track(
                    id = "it_$trackId",
                    title = title,
                    artistName = artist,
                    albumTitle = album,
                    durationMs = durationMs,
                    artworkUrl = artwork,
                    sourceType = AudioSourceType.STREAMING,
                )
            }.distinctBy { it.id }.take(limit)
            return Result.success(tracks)
        }

        fun parseArtistsFromTracksResponse(rawJson: String, limit: Int = 20): Result<List<Artist>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON artis iTunes tidak valid", it)) }
            val results = root["results"]?.asArr().orEmpty()
            val seen = HashSet<String>()
            val artists = mutableListOf<Artist>()
            for (item in results) {
                val obj = item.asObj() ?: continue
                val artistId = obj["artistId"]?.asStr()?.takeIf { it.isNotBlank() } ?: continue
                val name = obj["artistName"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: continue
                if (!seen.add(name.lowercase())) continue
                val artwork = upscaleArtwork(obj["artworkUrl100"]?.asStr())
                val genre = obj["primaryGenreName"]?.asStr()
                artists.add(
                    Artist(
                        id = "it_artist_$artistId",
                        name = name,
                        artworkUrl = artwork,
                        genre = genre,
                        sourceType = AudioSourceType.STREAMING,
                    ),
                )
                if (artists.size >= limit) break
            }
            return Result.success(artists)
        }

        private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
        private fun JsonElement.asArr(): JsonArray? = this as? JsonArray
        private fun JsonElement.asStr(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
        private fun JsonElement.asLong(): Long? = runCatching { jsonPrimitive.longOrNull }.getOrNull()
    }
}
