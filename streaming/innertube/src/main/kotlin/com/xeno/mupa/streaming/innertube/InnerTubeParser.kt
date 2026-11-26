package com.xeno.mupa.streaming.innertube

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.streaming.api.AudioQualityPreference
import com.xeno.mupa.streaming.api.StreamingError
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Parser JSON InnerTube yang toleran dan defensif (nol !! assertion).
 * Memetakan respons /search dan /player menjadi model domain Mupa murni.
 */
object InnerTubeParser {

    val tolerantJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    data class ParsedPlayerStream(
        val stream: ResolvedStream?,
        val playabilityStatus: String,
        val reason: String?,
        val hasCipheredAudioOnly: Boolean,
    )

    fun parsePlayerResponse(
        rawJson: String,
        quality: AudioQualityPreference = AudioQualityPreference.HIGH,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Result<ParsedPlayerStream> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON player tidak valid", it)) }

        val playability = root["playabilityStatus"]?.asObjectOrNull()
        val status = playability?.get("status")?.asStringOrNull().orEmpty().ifEmpty { "UNKNOWN" }
        val reason = playability?.get("reason")?.asStringOrNull()

        if (status != "OK") {
            val lowerReason = reason.orEmpty().lowercase()
            val error = when {
                status == "LOGIN_REQUIRED" && (lowerReason.contains("bot") || lowerReason.contains("confirm")) ->
                    StreamingError.BotCheck(reason ?: "Verifikasi bot YouTube")
                lowerReason.contains("country") || lowerReason.contains("region") || lowerReason.contains("geo") ->
                    StreamingError.GeoBlocked(reason ?: "Konten dibatasi wilayah")
                status == "UNPLAYABLE" || status == "ERROR" ->
                    StreamingError.Unavailable(reason ?: "Stream tidak dapat diputar ($status)")
                else ->
                    StreamingError.Unavailable(reason ?: "Status playability: $status")
            }
            return Result.failure(error)
        }

        val streamingData = root["streamingData"]?.asObjectOrNull()
            ?: return Result.failure(StreamingError.Unavailable("streamingData tidak ditemukan"))
        val expiresSec = streamingData["expiresInSeconds"]?.asLongOrNull() ?: 18_000L
        val adaptiveFormats = streamingData["adaptiveFormats"]?.asArrayOrNull().orEmpty()

        val audioObjects = adaptiveFormats.mapNotNull { it.asObjectOrNull() }
            .filter { fmt -> fmt["mimeType"]?.asStringOrNull()?.startsWith("audio/") == true }

        val directCandidates = audioObjects.mapNotNull { fmt ->
            val url = fmt["url"]?.asStringOrNull()?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val mime = fmt["mimeType"]?.asStringOrNull()?.substringBefore(';').orEmpty().ifEmpty { "audio/mp4" }
            val bitrate = fmt["bitrate"]?.asIntOrNull() ?: 0
            Triple(url, mime, bitrate)
        }

        if (directCandidates.isEmpty()) {
            val hasCipher = audioObjects.any { it["signatureCipher"] != null || it["cipher"] != null }
            return Result.success(
                ParsedPlayerStream(
                    stream = null,
                    playabilityStatus = status,
                    reason = reason,
                    hasCipheredAudioOnly = hasCipher,
                ),
            )
        }

        val targetMaxBps = when (quality) {
            AudioQualityPreference.LOW -> 80_000
            AudioQualityPreference.MEDIUM -> 140_000
            AudioQualityPreference.HIGH -> Int.MAX_VALUE
        }

        val picked = directCandidates
            .filter { it.third <= targetMaxBps }
            .maxByOrNull { scoreFormat(it.second, it.third) }
            ?: directCandidates.minByOrNull { it.third }
            ?: return Result.failure(StreamingError.Unavailable("Tidak ada format audio"))

        return Result.success(
            ParsedPlayerStream(
                stream = ResolvedStream(
                    url = picked.first,
                    mimeType = picked.second,
                    bitrateKbps = picked.third / 1000,
                    expiresAtEpochMs = nowEpochMs + (expiresSec * 1_000L),
                ),
                playabilityStatus = status,
                reason = reason,
                hasCipheredAudioOnly = false,
            ),
        )
    }

    fun parseSearchTracks(rawJson: String, limit: Int = 20): Result<List<Track>> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON pencarian tidak valid", it)) }

        val collected = mutableListOf<Track>()
        collectResponsiveItems(root, collected, limit)
        return Result.success(collected.distinctBy { it.id }.take(limit))
    }

    fun parseSearchArtists(rawJson: String, limit: Int = 16): Result<List<Artist>> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON artis tidak valid", it)) }

        val artists = mutableListOf<Artist>()
        collectArtistItems(root, artists, limit)
        return Result.success(artists.distinctBy { it.id }.take(limit))
    }

    private fun scoreFormat(mime: String, bitrate: Int): Long {
        val codecBonus = when {
            mime.contains("webm", ignoreCase = true) || mime.contains("opus", ignoreCase = true) -> 20_000L
            mime.contains("mp4", ignoreCase = true) -> 10_000L
            else -> 0L
        }
        return bitrate.toLong() + codecBonus
    }

    private fun collectResponsiveItems(node: JsonElement, out: MutableList<Track>, limit: Int) {
        if (out.size >= limit) return
        when (node) {
            is JsonObject -> {
                val renderer = node["musicResponsiveListItemRenderer"]?.asObjectOrNull()
                if (renderer != null) {
                    parseTrackRenderer(renderer)?.let { out.add(it) }
                }
                for (value in node.values) {
                    if (out.size >= limit) break
                    collectResponsiveItems(value, out, limit)
                }
            }
            is JsonArray -> {
                for (item in node) {
                    if (out.size >= limit) break
                    collectResponsiveItems(item, out, limit)
                }
            }
            else -> Unit
        }
    }

    private fun parseTrackRenderer(renderer: JsonObject): Track? {
        val videoId = renderer["playlistItemData"]?.asObjectOrNull()?.get("videoId")?.asStringOrNull()
            ?: findNestedString(renderer, "videoId")
            ?: return null

        val flexColumns = renderer["flexColumns"]?.asArrayOrNull().orEmpty()
        val title = flexColumns.getOrNull(0)?.let { extractRunsText(it) }?.takeIf { it.isNotBlank() }
            ?: return null
        val subtitleRuns = flexColumns.getOrNull(1)?.let { extractRunsList(it) }.orEmpty()
            .filter { it != " • " && it != "•" && it.isNotBlank() }

        val artist = subtitleRuns.firstOrNull { !isDurationToken(it) && !it.equals("Song", true) && !it.equals("Lagu", true) }
            ?: "Artis Tidak Diketahui"
        val durationToken = subtitleRuns.lastOrNull { isDurationToken(it) }
        val durationMs = durationToken?.let { parseDurationTokenMs(it) } ?: 0L
        val thumbnailUrl = extractBestThumbnail(renderer)

        return Track(
            id = videoId,
            title = title,
            artistName = artist,
            durationMs = durationMs,
            artworkUrl = thumbnailUrl,
            sourceType = AudioSourceType.STREAMING,
        )
    }

    private fun collectArtistItems(node: JsonElement, out: MutableList<Artist>, limit: Int) {
        if (out.size >= limit) return
        when (node) {
            is JsonObject -> {
                val renderer = node["musicResponsiveListItemRenderer"]?.asObjectOrNull()
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.asObjectOrNull()
                        ?.get("browseEndpoint")?.asObjectOrNull()
                        ?.get("browseId")?.asStringOrNull()
                    if (browseId != null && browseId.startsWith("UC")) {
                        val name = renderer["flexColumns"]?.asArrayOrNull()
                            ?.getOrNull(0)?.let { extractRunsText(it) }
                            ?.takeIf { it.isNotBlank() }
                        if (name != null) {
                            out.add(
                                Artist(
                                    id = browseId,
                                    name = name,
                                    artworkUrl = extractBestThumbnail(renderer),
                                    sourceType = AudioSourceType.STREAMING,
                                ),
                            )
                        }
                    }
                }
                for (value in node.values) {
                    if (out.size >= limit) break
                    collectArtistItems(value, out, limit)
                }
            }
            is JsonArray -> {
                for (item in node) {
                    if (out.size >= limit) break
                    collectArtistItems(item, out, limit)
                }
            }
            else -> Unit
        }
    }

    private fun extractRunsText(element: JsonElement): String =
        extractRunsList(element).joinToString("").trim()

    private fun extractRunsList(element: JsonElement): List<String> {
        val runs = mutableListOf<String>()
        fun walk(e: JsonElement) {
            when (e) {
                is JsonObject -> {
                    val text = e["text"]?.asStringOrNull()
                    if (text != null) runs.add(text)
                    e.values.forEach(::walk)
                }
                is JsonArray -> e.forEach(::walk)
                else -> Unit
            }
        }
        walk(element)
        return runs
    }

    private fun extractBestThumbnail(renderer: JsonObject): String? {
        val thumbs = renderer["thumbnail"]?.asObjectOrNull()
            ?.get("musicThumbnailRenderer")?.asObjectOrNull()
            ?.get("thumbnail")?.asObjectOrNull()
            ?.get("thumbnails")?.asArrayOrNull()
            ?: return null
        return thumbs.lastOrNull()?.asObjectOrNull()?.get("url")?.asStringOrNull()
    }

    private fun findNestedString(node: JsonElement, key: String): String? = when (node) {
        is JsonObject -> node[key]?.asStringOrNull()
            ?: node.values.firstNotNullOfOrNull { findNestedString(it, key) }
        is JsonArray -> node.firstNotNullOfOrNull { findNestedString(it, key) }
        else -> null
    }

    private fun isDurationToken(token: String): Boolean =
        Regex("""^\d{1,2}:\d{2}(:\d{2})?$""").matches(token.trim())

    internal fun parseDurationTokenMs(token: String): Long {
        val parts = token.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1_000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1_000L
            else -> 0L
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.asArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.asStringOrNull(): String? = runCatching { this.jsonPrimitive.contentOrNull }.getOrNull()
    private fun JsonElement.asIntOrNull(): Int? = runCatching { this.jsonPrimitive.intOrNull }.getOrNull()
    private fun JsonElement.asLongOrNull(): Long? = runCatching { this.jsonPrimitive.longOrNull }.getOrNull()
}
