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
 * CatalogProvider metadata-only berbasis Deezer Public API (`api.deezer.com`).
 * - Tanpa login, tanpa API key, tanpa DRM; lisensi implementasi clean-room Apache-2.0.
 * - Sangat cepat dan unggul untuk foto artis HD (`picture_big`/`picture_xl`) pada Onboarding & Pencarian.
 * - TIDAK melakukan streaming audio penuh (hanya katalog & metadata).
 */
class DeezerCatalogProvider(
    private val httpClient: OkHttpClient,
    private val baseUrl: String = "https://api.deezer.com",
) : CatalogProvider {

    override val providerId: String = PROVIDER_ID
    override val catalogPriority: Int = 10

    override suspend fun searchTracks(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext Result.success(emptyList())
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            val url = "$baseUrl/search?q=$encoded&limit=${limit.coerceIn(1, 30)}"
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
            val url = "$baseUrl/search/artist?q=$encoded&limit=${limit.coerceIn(1, 25)}"
            fetchJson(url).fold(
                onSuccess = { parseArtistsResponse(it, limit) },
                onFailure = { Result.failure(it) },
            )
        }

    override suspend fun getArtistTopTracks(artistId: String, artistName: String): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            val numericId = artistId.removePrefix("dz_artist_").takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
            if (numericId != null) {
                val url = "$baseUrl/artist/$numericId/top?limit=12"
                val byId = fetchJson(url).fold(
                    onSuccess = { parseTracksResponse(it, 12) },
                    onFailure = { Result.failure(it) },
                )
                if (byId.getOrNull()?.isNotEmpty() == true) return@withContext byId
            }
            searchTracks(artistName, limit = 12)
        }

    override suspend fun getTrendingArtists(limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/chart/0/artists?limit=${limit.coerceIn(1, 30)}"
            fetchJson(url).fold(
                onSuccess = { parseArtistsResponse(it, limit) },
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
                    else -> Result.failure(StreamingError.Network("Deezer HTTP ${resp.code}"))
                }
            }
        } catch (io: IOException) {
            Result.failure(StreamingError.Network("Koneksi Deezer gagal: ${io.message}", io))
        }
    }

    companion object {
        const val PROVIDER_ID = "deezer"

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

        fun parseTracksResponse(rawJson: String, limit: Int = 20): Result<List<Track>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON Deezer tidak valid", it)) }
            if (root["error"] != null) {
                val msg = root["error"]?.asObj()?.get("message")?.asStr() ?: "Deezer API error"
                return Result.failure(StreamingError.Unavailable(msg))
            }
            val data = root["data"]?.asArr().orEmpty()
            val tracks = data.mapNotNull { item ->
                val obj = item.asObj() ?: return@mapNotNull null
                val id = obj["id"]?.asStr()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = obj["title"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artistObj = obj["artist"]?.asObj()
                val artistName = artistObj?.get("name")?.asStr()?.trim()?.takeIf { it.isNotBlank() }
                    ?: "Artis Tidak Diketahui"
                val albumObj = obj["album"]?.asObj()
                val albumTitle = albumObj?.get("title")?.asStr()?.trim()?.takeIf { it.isNotBlank() }
                val durationSec = obj["duration"]?.asInt() ?: 0
                val artwork = albumObj?.get("cover_xl")?.asStr()
                    ?: albumObj?.get("cover_big")?.asStr()
                    ?: albumObj?.get("cover_medium")?.asStr()
                Track(
                    id = "dz_$id",
                    title = title,
                    artistName = artistName,
                    albumTitle = albumTitle,
                    durationMs = durationSec * 1_000L,
                    artworkUrl = artwork?.takeIf { it.startsWith("http") },
                    sourceType = AudioSourceType.STREAMING,
                )
            }.distinctBy { it.id }.take(limit)
            return Result.success(tracks)
        }

        fun parseArtistsResponse(rawJson: String, limit: Int = 24): Result<List<Artist>> {
            val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
                .getOrElse { return Result.failure(StreamingError.ParseError("JSON artis Deezer tidak valid", it)) }
            val data = root["data"]?.asArr().orEmpty()
            val artists = data.mapNotNull { item ->
                val obj = item.asObj() ?: return@mapNotNull null
                val id = obj["id"]?.asStr()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val name = obj["name"]?.asStr()?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val pic = obj["picture_xl"]?.asStr()
                    ?: obj["picture_big"]?.asStr()
                    ?: obj["picture_medium"]?.asStr()
                Artist(
                    id = "dz_artist_$id",
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
