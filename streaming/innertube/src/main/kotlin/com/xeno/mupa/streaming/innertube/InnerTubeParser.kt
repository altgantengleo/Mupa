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
 * Memetakan respons /search, /browse, dan /player menjadi model domain Mupa murni
 * dengan upscaling thumbnail HD untuk cover art & potret artis.
 */
object InnerTubeParser {

    val tolerantJson: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val googleSizeRegex = Regex("""=w\d+-h\d+[^&"]*""")
    private val googleSquareRegex = Regex("""=s\d+[^&"]*""")

    data class ParsedPlayerStream(
        val stream: ResolvedStream?,
        val playabilityStatus: String,
        val reason: String?,
        val hasCipheredAudioOnly: Boolean,
    )

    data class ParsedNextPage(
        val tracks: List<Track>,
        val continuationToken: String?,
        val automixPlaylistId: String?,
        val relatedBrowseId: String?,
    )

    fun extractVisitorData(rawJson: String): String? = runCatching {
        val root = tolerantJson.parseToJsonElement(rawJson).jsonObject
        root["responseContext"]?.asObjectOrNull()?.get("visitorData")?.asStringOrNull()
    }.getOrNull()

    fun parsePlayerResponse(
        rawJson: String,
        quality: AudioQualityPreference = AudioQualityPreference.HIGH,
        nowEpochMs: Long = System.currentTimeMillis(),
        requestHeaders: Map<String, String> = emptyMap(),
    ): Result<ParsedPlayerStream> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON player tidak valid", it)) }

        val playability = root["playabilityStatus"]?.asObjectOrNull()
        val status = playability?.get("status")?.asStringOrNull().orEmpty().ifEmpty { "UNKNOWN" }
        val reason = playability?.get("reason")?.asStringOrNull()

        if (status != "OK") {
            val lowerReason = reason.orEmpty().lowercase()
            val error = when {
                status == "LOGIN_REQUIRED" && (
                    lowerReason.contains("bot") ||
                        lowerReason.contains("confirm") ||
                        lowerReason.contains("konfirmasi")
                    ) ->
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
                    headers = requestHeaders,
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
        collectTrackItems(root, collected, limit)
        return Result.success(collected.distinctBy { it.id }.take(limit))
    }

    fun parseSearchArtists(rawJson: String, limit: Int = 24): Result<List<Artist>> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON artis tidak valid", it)) }

        val artists = mutableListOf<Artist>()
        collectArtistItems(root, artists, limit)
        return Result.success(artists.distinctBy { it.id }.take(limit))
    }

    /**
     * Parser respons endpoint InnerTube `POST /youtubei/v1/next` (Radio / Up Next / Automix).
     * Clean-room dan toleran (nol !!): mendukung halaman awal `playlistPanelRenderer`
     * maupun lanjutan `continuationContents.playlistPanelContinuation`.
     */
    fun parseNextResponse(rawJson: String): Result<ParsedNextPage> {
        val root = runCatching { tolerantJson.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON next/radio tidak valid", it)) }

        val tabs = root["contents"]?.asObjectOrNull()
            ?.get("singleColumnMusicWatchNextResultsRenderer")?.asObjectOrNull()
            ?.get("tabbedRenderer")?.asObjectOrNull()
            ?.get("watchNextTabbedResultsRenderer")?.asObjectOrNull()
            ?.get("tabs")?.asArrayOrNull()

        val panel = root["continuationContents"]?.asObjectOrNull()
            ?.get("playlistPanelContinuation")?.asObjectOrNull()
            ?: tabs?.getOrNull(0)?.asObjectOrNull()
                ?.get("tabRenderer")?.asObjectOrNull()
                ?.get("content")?.asObjectOrNull()
                ?.get("musicQueueRenderer")?.asObjectOrNull()
                ?.get("content")?.asObjectOrNull()
                ?.get("playlistPanelRenderer")?.asObjectOrNull()

        val relatedBrowseId = tabs?.getOrNull(2)?.asObjectOrNull()
            ?.get("tabRenderer")?.asObjectOrNull()
            ?.get("endpoint")?.asObjectOrNull()
            ?.get("browseEndpoint")?.asObjectOrNull()
            ?.get("browseId")?.asStringOrNull()

        val contents = panel?.get("contents")?.asArrayOrNull().orEmpty()
        val tracks = mutableListOf<Track>()
        var automixPlaylistId: String? = null

        for (entry in contents) {
            val obj = entry.asObjectOrNull() ?: continue
            obj["playlistPanelVideoRenderer"]?.asObjectOrNull()?.let { videoRenderer ->
                parsePlaylistPanelVideoRenderer(videoRenderer)?.let { tracks.add(it) }
            }
            if (automixPlaylistId == null) {
                automixPlaylistId = obj["automixPreviewVideoRenderer"]?.asObjectOrNull()
                    ?.get("content")?.asObjectOrNull()
                    ?.get("automixPlaylistVideoRenderer")?.asObjectOrNull()
                    ?.get("navigationEndpoint")?.asObjectOrNull()
                    ?.get("watchPlaylistEndpoint")?.asObjectOrNull()
                    ?.get("playlistId")?.asStringOrNull()
            }
        }

        val continuations = panel?.get("continuations")?.asArrayOrNull().orEmpty()
        val nextToken = continuations.firstNotNullOfOrNull { item ->
            val obj = item.asObjectOrNull() ?: return@firstNotNullOfOrNull null
            obj["nextRadioContinuationData"]?.asObjectOrNull()?.get("continuation")?.asStringOrNull()
                ?: obj["nextContinuationData"]?.asObjectOrNull()?.get("continuation")?.asStringOrNull()
                ?: obj["automixContinuationData"]?.asObjectOrNull()?.get("continuation")?.asStringOrNull()
        }

        return Result.success(
            ParsedNextPage(
                tracks = tracks.distinctBy { it.id },
                continuationToken = nextToken,
                automixPlaylistId = automixPlaylistId,
                relatedBrowseId = relatedBrowseId,
            ),
        )
    }

    private fun parsePlaylistPanelVideoRenderer(renderer: JsonObject): Track? {
        val videoId = renderer["videoId"]?.asStringOrNull()
            ?: renderer["navigationEndpoint"]?.asObjectOrNull()
                ?.get("watchEndpoint")?.asObjectOrNull()
                ?.get("videoId")?.asStringOrNull()
            ?: return null
        if (!InnerTubeProvider.isLikelyYouTubeVideoId(videoId)) return null

        val title = renderer["title"]?.let { extractRunsText(it) }?.takeIf { it.isNotBlank() }
            ?: return null

        val bylineRuns = (renderer["longBylineText"] ?: renderer["shortBylineText"])
            ?.let { extractRunsList(it) }.orEmpty()
            .filter { it != " • " && it != "•" && it.isNotBlank() }

        val artist = bylineRuns.firstOrNull {
            !isDurationToken(it) &&
                !it.equals("Song", true) &&
                !it.equals("Lagu", true) &&
                !it.equals("Video", true) &&
                !it.contains("views", true) &&
                !it.contains("ditonton", true)
        } ?: "Artis Tidak Diketahui"

        val album = bylineRuns.getOrNull(1)?.takeIf {
            !isDurationToken(it) &&
                !it.matches(Regex("""^\d{4}$""")) &&
                !it.contains("views", true) &&
                !it.contains("ditonton", true)
        }

        val durationText = renderer["lengthText"]?.let { extractRunsText(it) }
        val durationMs = durationText?.takeIf { isDurationToken(it) }?.let { parseDurationTokenMs(it) } ?: 0L

        val thumbs = renderer["thumbnail"]?.asObjectOrNull()?.get("thumbnails")?.asArrayOrNull()
        val thumbUrl = upscaleArtworkUrl(thumbs?.lastOrNull()?.asObjectOrNull()?.get("url")?.asStringOrNull())
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Track(
            id = videoId,
            title = title,
            artistName = artist,
            albumTitle = album,
            durationMs = durationMs,
            artworkUrl = thumbUrl,
            sourceType = AudioSourceType.STREAMING,
        )
    }

    internal fun upscaleArtworkUrl(rawUrl: String?): String? {
        val url = rawUrl?.trim()?.takeIf { it.startsWith("http") } ?: return null
        if (!url.contains("googleusercontent.com") && !url.contains("ggpht.com")) {
            return url
        }
        return when {
            googleSizeRegex.containsMatchIn(url) -> googleSizeRegex.replace(url, "=w544-h544-p-l90-rj")
            googleSquareRegex.containsMatchIn(url) -> googleSquareRegex.replace(url, "=s544-c-k-c0x00ffffff-no-rj")
            else -> url
        }
    }

    private fun scoreFormat(mime: String, bitrate: Int): Long {
        val codecBonus = when {
            mime.contains("mp4", ignoreCase = true) -> 15_000L
            mime.contains("webm", ignoreCase = true) || mime.contains("opus", ignoreCase = true) -> 20_000L
            else -> 0L
        }
        return bitrate.toLong() + codecBonus
    }

    private fun collectTrackItems(node: JsonElement, out: MutableList<Track>, limit: Int) {
        if (out.size >= limit) return
        when (node) {
            is JsonObject -> {
                node["musicResponsiveListItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    parseTrackRenderer(renderer)?.let { out.add(it) }
                }
                node["musicTwoRowItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    parseTwoRowTrackRenderer(renderer)?.let { out.add(it) }
                }
                for (value in node.values) {
                    if (out.size >= limit) break
                    collectTrackItems(value, out, limit)
                }
            }
            is JsonArray -> {
                for (item in node) {
                    if (out.size >= limit) break
                    collectTrackItems(item, out, limit)
                }
            }
            else -> Unit
        }
    }

    private fun parseTrackRenderer(renderer: JsonObject): Track? {
        val videoId = renderer["playlistItemData"]?.asObjectOrNull()?.get("videoId")?.asStringOrNull()
            ?: findNestedString(renderer, "videoId")
            ?: return null
        if (!InnerTubeProvider.isLikelyYouTubeVideoId(videoId)) return null

        val flexColumns = renderer["flexColumns"]?.asArrayOrNull().orEmpty()
        val title = flexColumns.getOrNull(0)?.let { extractRunsText(it) }?.takeIf { it.isNotBlank() }
            ?: return null
        val subtitleRuns = flexColumns.getOrNull(1)?.let { extractRunsList(it) }.orEmpty()
            .filter { it != " • " && it != "•" && it.isNotBlank() }

        val artist = subtitleRuns.firstOrNull {
            !isDurationToken(it) &&
                !it.equals("Song", true) &&
                !it.equals("Lagu", true) &&
                !it.equals("Video", true) &&
                !it.contains("views", true) &&
                !it.contains("ditonton", true) &&
                !it.contains("plays", true) &&
                !it.contains("kali diputar", true)
        } ?: "Artis Tidak Diketahui"
        val durationToken = subtitleRuns.lastOrNull { isDurationToken(it) }
        val durationMs = durationToken?.let { parseDurationTokenMs(it) } ?: 0L
        val thumbnailUrl = upscaleArtworkUrl(extractBestThumbnail(renderer))
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Track(
            id = videoId,
            title = title,
            artistName = artist,
            durationMs = durationMs,
            artworkUrl = thumbnailUrl,
            sourceType = AudioSourceType.STREAMING,
        )
    }

    private fun parseTwoRowTrackRenderer(renderer: JsonObject): Track? {
        val videoId = renderer["navigationEndpoint"]?.asObjectOrNull()
            ?.get("watchEndpoint")?.asObjectOrNull()
            ?.get("videoId")?.asStringOrNull()
            ?: return null
        if (!InnerTubeProvider.isLikelyYouTubeVideoId(videoId)) return null

        val title = renderer["title"]?.let { extractRunsText(it) }?.takeIf { it.isNotBlank() }
            ?: return null
        val subtitleRuns = renderer["subtitle"]?.let { extractRunsList(it) }.orEmpty()
            .filter { it != " • " && it != "•" && it.isNotBlank() }
        val artist = subtitleRuns.firstOrNull {
            !it.equals("Song", true) && !it.equals("Lagu", true) && !it.equals("Video", true)
        } ?: "Artis Tidak Diketahui"
        val thumbnailUrl = upscaleArtworkUrl(extractBestThumbnail(renderer))
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return Track(
            id = videoId,
            title = title,
            artistName = artist,
            artworkUrl = thumbnailUrl,
            sourceType = AudioSourceType.STREAMING,
        )
    }

    private fun collectArtistItems(node: JsonElement, out: MutableList<Artist>, limit: Int) {
        if (out.size >= limit) return
        when (node) {
            is JsonObject -> {
                node["musicResponsiveListItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    val browseId = renderer["navigationEndpoint"]?.asObjectOrNull()
                        ?.get("browseEndpoint")?.asObjectOrNull()
                        ?.get("browseId")?.asStringOrNull()
                    if (browseId != null && browseId.startsWith("UC")) {
                        val name = renderer["flexColumns"]?.asArrayOrNull()
                            ?.getOrNull(0)?.let { extractRunsText(it) }
                            ?.takeIf { it.isNotBlank() }
                        val thumb = upscaleArtworkUrl(extractBestThumbnail(renderer))
                        if (name != null) {
                            out.add(
                                Artist(
                                    id = browseId,
                                    name = name,
                                    artworkUrl = thumb,
                                    sourceType = AudioSourceType.STREAMING,
                                ),
                            )
                        }
                    }
                }
                node["musicTwoRowItemRenderer"]?.asObjectOrNull()?.let { renderer ->
                    val browseId = renderer["navigationEndpoint"]?.asObjectOrNull()
                        ?.get("browseEndpoint")?.asObjectOrNull()
                        ?.get("browseId")?.asStringOrNull()
                    if (browseId != null && browseId.startsWith("UC")) {
                        val name = renderer["title"]?.let { extractRunsText(it) }?.takeIf { it.isNotBlank() }
                        val thumb = upscaleArtworkUrl(extractBestThumbnail(renderer))
                        if (name != null) {
                            out.add(
                                Artist(
                                    id = browseId,
                                    name = name,
                                    artworkUrl = thumb,
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
        val thumbHolder = renderer["thumbnail"]?.asObjectOrNull()
            ?: renderer["thumbnailRenderer"]?.asObjectOrNull()
            ?: return null
        val thumbs = thumbHolder["musicThumbnailRenderer"]?.asObjectOrNull()
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
