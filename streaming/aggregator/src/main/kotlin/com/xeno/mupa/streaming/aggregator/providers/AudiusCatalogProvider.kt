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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * CatalogProvider ke-4 berbasis Audius Public Discovery API (`discoveryprovider.audius.co/v1`).
 * - 100% publik, tanpa login, tanpa API key, bebas DRM (implementasi clean-room).
 * - Kuat untuk katalog indie, Phonk, EDM, remix elektronik, dan rilisan komunitas yang jarang ada di label besar.
 */
class AudiusCatalogProvider(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://discoveryprovider.audius.co/v1",
) : CatalogProvider {

    override val providerId: String = PROVIDER_ID
    override val catalogPriority: Int = 35

    override suspend fun searchTracks(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/tracks/search?query=$encoded&app_name=Mupa"
            fetchJson(url).fold(
                onSuccess = { parseTracksResponse(it, limit.coerceIn(1, 30)) },
                onFailure = { Result.failure(it) },
            )
        }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/users/search?query=$encoded&app_name=Mupa"
            fetchJson(url).fold(
                onSuccess = { parseArtistsResponse(it, limit.coerceIn(1, 20)) },
                onFailure = { Result.failure(it) },
            )
        }

    override suspend fun getArtistTopTracks(artistId: String, artistName: String): Result<List<Track>> =
        searchTracks(artistName, limit = 15)

    override suspend fun getTrendingArtists(limit: Int): Result<List<Artist>> =
        Result.success(emptyList())

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
                    else -> Result.failure(StreamingError.Network("Audius HTTP ${resp.code}"))
                }
            }
        } catch (io: IOException) {
            Result.failure(StreamingError.Network("Koneksi Audius gagal: ${io.message}", io))
        }
    }

    companion object {
        const val PROVIDER_ID = "audius"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parseTracksResponse(rawJson: String, limit: Int = 20): Result<List<Track>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON Audius tidak valid", it)) }
            val data = root["data"]?.asArr().orEmpty()
            val tracks = data.mapNotNull { item ->
                val obj = item.asObj() ?: return@mapNotNull null
                val id = obj["id"]?.asStr()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = obj["title"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val userObj = obj["user"]?.asObj()
                val artistName = userObj?.get("name")?.asStr()?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Artis Tidak Diketahui"
                val durationSec = obj["duration"]?.asInt() ?: 0
                val artworkObj = obj["artwork"]?.asObj()
                val artwork = artworkObj?.get("480x480")?.asStr()
                    ?: artworkObj?.get("1000x1000")?.asStr()
                    ?: artworkObj?.get("150x150")?.asStr()
                Track(
                    id = "au_$id",
                    title = title,
                    artistName = artistName,
                    albumTitle = obj["genre"]?.asStr()?.takeIf { it.isNotBlank() },
                    durationMs = durationSec * 1_000L,
                    artworkUrl = artwork?.takeIf { it.startsWith("http") },
                    sourceType = AudioSourceType.STREAMING,
                )
            }.distinctBy { it.id }.take(limit)
            return Result.success(tracks)
        }

        fun parseArtistsResponse(rawJson: String, limit: Int = 15): Result<List<Artist>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON artis Audius tidak valid", it)) }
            val data = root["data"]?.asArr().orEmpty()
            val artists = data.mapNotNull { item ->
                val obj = item.asObj() ?: return@mapNotNull null
                val id = obj["id"]?.asStr()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj["name"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val picObj = obj["profile_picture"]?.asObj()
                val pic = picObj?.get("480x480")?.asStr()
                    ?: picObj?.get("1000x1000")?.asStr()
                    ?: picObj?.get("150x150")?.asStr()
                Artist(
                    id = "au_artist_$id",
                    name = name,
                    artworkUrl = pic?.takeIf { it.startsWith("http") },
                    sourceType = AudioSourceType.STREAMING,
                )
            }.distinctBy { it.name.lowercase() }.take(limit)
            return Result.success(artists)
        }

        private fun JsonElement.asObj(): JsonObject? = this as? JsonObject
        private fun JsonElement.asArr(): JsonArray? = this as? JsonArray
        private fun JsonElement.asStr(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()
        private fun JsonElement.asInt(): Int? = runCatching { jsonPrimitive.intOrNull }.getOrNull()
    }
}
