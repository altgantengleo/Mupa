package com.xeno.mupa.streaming.innertube

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.streaming.api.AudioQualityPreference
import com.xeno.mupa.streaming.api.RadioPage
import com.xeno.mupa.streaming.api.StreamLookupRequest
import com.xeno.mupa.streaming.api.StreamingAudioProvider
import com.xeno.mupa.streaming.api.StreamingCatalogProvider
import com.xeno.mupa.streaming.api.StreamingError
import com.xeno.mupa.streaming.api.StreamingRadioProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Klien InnerTube Mupa (guest mode, tanpa login/cookie).
 * - Mendukung visitorData otomatis untuk VISIONOS / ANDROID_VR / IOS.
 * - Mengisolasi seluruh detail YouTube + fallback NewPipeExtractor di dalam modul :streaming:innertube.
 */
class InnerTubeProvider(
    private val httpClient: OkHttpClient = defaultHttpClient(),
) : StreamingAudioProvider, StreamingCatalogProvider, StreamingRadioProvider {

    override val providerId: String = "innertube"
    override val priority: Int = 10
    override val supportsDirectTrackId: Boolean = true

    private data class CachedVisitor(val visitorData: String, val expiresAtMs: Long)

    private val visitorCache = ConcurrentHashMap<String, CachedVisitor>()

    suspend fun getTrendingArtists(limit: Int = 24): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val bodyJson = buildJsonObject {
                put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
                put("browseId", "FEmusic_charts")
            }
            val fromCharts = postInnerTube(InnerTubeProfiles.WEB_REMIX, "browse", bodyJson.toString())
                .fold(
                    onSuccess = { InnerTubeParser.parseSearchArtists(it, limit).getOrDefault(emptyList()) },
                    onFailure = { emptyList() },
                )
                .filter { !it.artworkUrl.isNullOrBlank() }

            if (fromCharts.size >= 8) {
                return@withContext Result.success(fromCharts.distinctBy { it.id }.take(limit))
            }

            // Tambah dari pencarian umum agar daftar awal selalu kaya & berfoto HD
            val seedQueries = listOf("Indonesia Pop", "Indie Indonesia", "Global Top Artists")
            val combined = fromCharts.toMutableList()
            for (q in seedQueries) {
                if (combined.size >= limit) break
                val extra = searchArtists(q, limit = 10).getOrDefault(emptyList())
                combined.addAll(extra)
            }
            val unique = combined.distinctBy { it.name.lowercase() }.take(limit)
            if (unique.isNotEmpty()) {
                Result.success(unique)
            } else {
                Result.failure(StreamingError.Network("Gagal memuat artis populer dari YouTube Music"))
            }
        }

    override suspend fun searchTracks(query: String, limit: Int): Result<List<Track>> =
        withContext(Dispatchers.IO) {
            val withFilter = buildJsonObject {
                put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
                put("query", query)
                put("params", SONGS_FILTER_PARAMS)
            }
            val primary = postInnerTube(InnerTubeProfiles.WEB_REMIX, "search", withFilter.toString())
                .fold(
                    onSuccess = { InnerTubeParser.parseSearchTracks(it, limit) },
                    onFailure = { Result.failure(it) },
                )
            val tracks = primary.getOrDefault(emptyList())
            if (tracks.isNotEmpty()) {
                return@withContext Result.success(tracks)
            }

            // Fallback tanpa filter params bila YouTube mengubah token filter
            val plainBody = buildJsonObject {
                put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
                put("query", query)
            }
            postInnerTube(InnerTubeProfiles.WEB_REMIX, "search", plainBody.toString())
                .fold(
                    onSuccess = { InnerTubeParser.parseSearchTracks(it, limit) },
                    onFailure = { Result.failure(it) },
                )
        }

    override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> =
        withContext(Dispatchers.IO) {
            val withFilter = buildJsonObject {
                put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
                put("query", query)
                put("params", ARTISTS_FILTER_PARAMS)
            }
            val primary = postInnerTube(InnerTubeProfiles.WEB_REMIX, "search", withFilter.toString())
                .fold(
                    onSuccess = { InnerTubeParser.parseSearchArtists(it, limit) },
                    onFailure = { Result.failure(it) },
                )
            val artists = primary.getOrDefault(emptyList())
            if (artists.isNotEmpty()) {
                return@withContext Result.success(artists)
            }

            val plainBody = buildJsonObject {
                put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
                put("query", query)
            }
            postInnerTube(InnerTubeProfiles.WEB_REMIX, "search", plainBody.toString())
                .fold(
                    onSuccess = { InnerTubeParser.parseSearchArtists(it, limit) },
                    onFailure = { Result.failure(it) },
                )
        }

    override suspend fun getArtistTopTracks(artistId: String, artistName: String): Result<List<Track>> =
        searchTracks(artistName, limit = 10)

    /**
     * Endpoint `POST /youtubei/v1/next` (Radio / Up Next / Automix) clean-room.
     * - Untuk seed baru: memanggil `playlistId = "RDAMVM$videoId"`.
     * - Jika hasil <= 1 lagu, coba bare `videoId` (automix) dan ikuti `automixPlaylistId` bila ada.
     * - Jika masih kosong/sedikit (mis. session tamu terbatas), fallback ke pencarian lagu artis terkait.
     */
    override suspend fun getRadioNext(
        seedTrack: Track,
        continuationToken: String?,
        playlistId: String?,
    ): Result<RadioPage> = withContext(Dispatchers.IO) {
        val resolvedVideoId = if (isLikelyYouTubeVideoId(seedTrack.id)) {
            seedTrack.id
        } else {
            searchTracks("${seedTrack.artistName} ${seedTrack.title}", limit = 1)
                .getOrNull()?.firstOrNull()?.id
                ?: return@withContext fallbackArtistRadioSearch(seedTrack)
        }

        val effectivePlaylistId = playlistId ?: "RDAMVM$resolvedVideoId"
        val primaryPage = callNextEndpoint(
            videoId = if (continuationToken == null) resolvedVideoId else null,
            playlistId = if (continuationToken == null) effectivePlaylistId else null,
            continuation = continuationToken,
        ).getOrNull()

        var bestPage = primaryPage

        // Jika RDAMVM mengembalikan <= 1 lagu pada halaman awal, coba bare videoId untuk automix
        if (continuationToken == null && (bestPage == null || bestPage.tracks.size <= 1)) {
            val barePage = callNextEndpoint(
                videoId = resolvedVideoId,
                playlistId = null,
                continuation = null,
            ).getOrNull()
            if (barePage != null) {
                val automixId = barePage.automixPlaylistId
                if (!automixId.isNullOrBlank()) {
                    val expanded = callNextEndpoint(
                        videoId = null,
                        playlistId = automixId,
                        continuation = null,
                    ).getOrNull()
                    if (expanded != null && expanded.tracks.size > (bestPage?.tracks?.size ?: 0)) {
                        bestPage = expanded
                    }
                }
                if (barePage.tracks.size > (bestPage?.tracks?.size ?: 0)) {
                    bestPage = barePage
                }
            }
        }

        if (bestPage != null && bestPage.tracks.size > 1) {
            return@withContext Result.success(
                RadioPage(
                    tracks = bestPage.tracks,
                    continuationToken = bestPage.continuationToken,
                    playlistId = bestPage.automixPlaylistId ?: effectivePlaylistId,
                ),
            )
        }

        // Fallback aman: bila halaman awal /next tamu hanya berisi <= 1 item, ambil lagu serupa berbasis artis
        if (continuationToken == null) {
            return@withContext fallbackArtistRadioSearch(seedTrack, effectivePlaylistId)
        }

        Result.success(
            RadioPage(
                tracks = bestPage?.tracks.orEmpty(),
                continuationToken = bestPage?.continuationToken,
                playlistId = effectivePlaylistId,
            ),
        )
    }

    override suspend fun getArtistRadio(
        artistId: String,
        artistName: String,
        continuationToken: String?,
    ): Result<RadioPage> = withContext(Dispatchers.IO) {
        val topTracks = getArtistTopTracks(artistId, artistName).getOrDefault(emptyList())
        val first = topTracks.firstOrNull()
            ?: return@withContext Result.failure(StreamingError.Unavailable("Lagu artis $artistName tidak ditemukan"))
        val radio = getRadioNext(first, continuationToken = continuationToken).getOrNull()
        val combined = (topTracks + radio?.tracks.orEmpty()).distinctBy { it.id }
        Result.success(
            RadioPage(
                tracks = combined,
                continuationToken = radio?.continuationToken,
                playlistId = radio?.playlistId,
            ),
        )
    }

    private fun callNextEndpoint(
        videoId: String?,
        playlistId: String?,
        continuation: String?,
    ): Result<InnerTubeParser.ParsedNextPage> {
        val body = buildJsonObject {
            put("context", InnerTubeProfiles.WEB_REMIX.buildContextJson())
            if (!videoId.isNullOrBlank()) put("videoId", videoId)
            if (!playlistId.isNullOrBlank()) put("playlistId", playlistId)
            if (!continuation.isNullOrBlank()) put("continuation", continuation)
        }
        return postInnerTube(InnerTubeProfiles.WEB_REMIX, "next", body.toString()).fold(
            onSuccess = { InnerTubeParser.parseNextResponse(it) },
            onFailure = { Result.failure(it) },
        )
    }

    private suspend fun fallbackArtistRadioSearch(
        seedTrack: Track,
        playlistId: String? = null,
    ): Result<RadioPage> {
        val artistQuery = seedTrack.artistName.takeIf { it.isNotBlank() && !it.equals("Artis Tidak Diketahui", true) }
            ?: seedTrack.title
        val tracks = searchTracks("$artistQuery official audio", limit = 16).getOrDefault(emptyList())
        return if (tracks.isNotEmpty()) {
            Result.success(RadioPage(tracks = tracks, continuationToken = null, playlistId = playlistId))
        } else {
            Result.failure(StreamingError.Unavailable("Gagal memuat radio untuk ${seedTrack.title}"))
        }
    }

    override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> =
        withContext(Dispatchers.IO) {
            val videoId = if (isLikelyYouTubeVideoId(request.trackId)) {
                request.trackId
            } else {
                searchTracks("${request.artistName} ${request.title}", limit = 1)
                    .getOrNull()?.firstOrNull()?.id
                    ?: return@withContext Result.failure(
                        StreamingError.Unavailable("VideoId InnerTube tidak ditemukan untuk ${request.title}"),
                    )
            }

            var lastError: Throwable = StreamingError.Unavailable("Semua klien InnerTube gagal")
            for (profile in InnerTubeProfiles.STREAM_WALK_ORDER) {
                val visitorData = if (profile.ownVisitorId) getOrFetchVisitorData(profile) else null
                val payload = buildJsonObject {
                    put("context", profile.buildContextJson(visitorData = visitorData))
                    put("videoId", videoId)
                    put("contentCheckOk", true)
                    put("racyCheckOk", true)
                }
                val streamHeaders = mapOf("User-Agent" to profile.userAgent)
                val raw = postInnerTube(profile, "player", payload.toString(), visitorData = visitorData)
                val parsed = raw.fold(
                    onSuccess = {
                        InnerTubeParser.parsePlayerResponse(
                            rawJson = it,
                            quality = request.quality,
                            requestHeaders = streamHeaders,
                        )
                    },
                    onFailure = { Result.failure(it) },
                )
                val resolved = parsed.getOrNull()?.stream
                if (resolved != null) {
                    return@withContext Result.success(resolved)
                }
                val err = parsed.exceptionOrNull()
                if (err is StreamingError.BotCheck && profile.ownVisitorId) {
                    visitorCache.remove(profile.id)
                }
                if (err != null) lastError = err
            }

            // Fallback terisolasi ke NewPipeExtractor (untuk stream bersignature/n-param)
            resolveWithNewPipe(videoId, request.quality).fold(
                onSuccess = { Result.success(it) },
                onFailure = { npErr -> Result.failure(if (lastError is StreamingError) lastError else npErr) },
            )
        }

    private fun getOrFetchVisitorData(profile: InnerTubeClientProfile): String? {
        val now = System.currentTimeMillis()
        val cached = visitorCache[profile.id]
        if (cached != null && now < cached.expiresAtMs) {
            return cached.visitorData
        }
        val body = buildJsonObject {
            put("context", profile.buildContextJson())
        }
        val raw = postInnerTube(profile, "visitor_id", body.toString()).getOrNull() ?: return null
        val vd = InnerTubeParser.extractVisitorData(raw)?.takeIf { it.isNotBlank() } ?: return null
        visitorCache[profile.id] = CachedVisitor(vd, now + TimeUnit.MINUTES.toMillis(30))
        return vd
    }

    private fun resolveWithNewPipe(
        videoId: String,
        quality: AudioQualityPreference,
    ): Result<ResolvedStream> = runCatching {
        ensureNewPipeInitialized(httpClient)
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        val audioStreams = info.audioStreams.orEmpty().filter { !it.content.isNullOrBlank() }
        val maxKbps = when (quality) {
            AudioQualityPreference.LOW -> 80
            AudioQualityPreference.MEDIUM -> 140
            AudioQualityPreference.HIGH -> Int.MAX_VALUE
        }
        val selected = audioStreams
            .filter { it.averageBitrate <= maxKbps }
            .maxByOrNull { it.averageBitrate }
            ?: audioStreams.maxByOrNull { it.averageBitrate }
            ?: throw StreamingError.Unavailable("NewPipe tidak menemukan stream audio")

        ResolvedStream(
            url = selected.content,
            mimeType = selected.format?.mimeType ?: "audio/mp4",
            bitrateKbps = selected.averageBitrate.takeIf { it > 0 },
            expiresAtEpochMs = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(4),
        )
    }.recoverCatching { err ->
        if (err is StreamingError) throw err
        throw StreamingError.Network("Fallback NewPipe gagal: ${err.message}", err)
    }

    private fun postInnerTube(
        profile: InnerTubeClientProfile,
        endpoint: String,
        jsonBody: String,
        visitorData: String? = null,
    ): Result<String> {
        val url = "${profile.apiOrigin}/youtubei/v1/$endpoint?prettyPrint=false"
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", profile.userAgent)
            .header("X-YouTube-Client-Name", profile.clientNameHeaderId)
            .header("X-YouTube-Client-Version", profile.clientVersion)
            .header("Origin", profile.apiOrigin)
            .header("Referer", "${profile.apiOrigin}/")
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))

        profile.apiFormatVersion?.let { reqBuilder.header("X-YouTube-Api-Format-Version", it) }
        if (!visitorData.isNullOrBlank()) {
            reqBuilder.header("X-Goog-Visitor-Id", visitorData)
        }

        return try {
            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    in 200..299 -> Result.success(text)
                    429 -> Result.failure(
                        StreamingError.RateLimited(
                            retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
                        ),
                    )
                    403 -> Result.failure(StreamingError.BotCheck("HTTP 403 dari InnerTube (${profile.id})"))
                    else -> Result.failure(StreamingError.Network("HTTP ${response.code} dari InnerTube (${profile.id})"))
                }
            }
        } catch (io: IOException) {
            Result.failure(StreamingError.Network("Koneksi InnerTube gagal: ${io.message}", io))
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val SONGS_FILTER_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val ARTISTS_FILTER_PARAMS = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
        private val newPipeReady = AtomicBoolean(false)

        fun isLikelyYouTubeVideoId(id: String): Boolean =
            Regex("""^[A-Za-z0-9_-]{11}$""").matches(id)

        private fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        private fun ensureNewPipeInitialized(client: OkHttpClient) {
            if (newPipeReady.compareAndSet(false, true)) {
                NewPipe.init(OkHttpNewPipeDownloader(client))
            }
        }
    }
}

private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient,
) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder().url(request.url())
        request.headers().forEach { (key, values) ->
            values.forEach { value -> builder.addHeader(key, value) }
        }
        val bodyBytes = request.dataToSend()
        when (request.httpMethod()) {
            "POST" -> builder.post((bodyBytes ?: ByteArray(0)).toRequestBody(null))
            "HEAD" -> builder.head()
            else -> builder.get()
        }
        client.newCall(builder.build()).execute().use { resp ->
            return NpResponse(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string(),
                resp.request.url.toString(),
            )
        }
    }
}
