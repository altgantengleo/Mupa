package com.xeno.mupa.streaming.aggregator

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.streaming.aggregator.providers.DeezerCatalogProvider
import com.xeno.mupa.streaming.aggregator.providers.ItunesCatalogProvider
import com.xeno.mupa.streaming.api.CatalogProvider
import com.xeno.mupa.streaming.api.MupaStreamingDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Model rilisan (Album / EP / Single) gabungan lintas provider untuk tampilan segmen pencarian & profil artis.
 */
data class CatalogRelease(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val releaseType: String = "Album",
    val releaseYear: String? = null,
    val trackCount: Int = 0,
    val cachedTracks: List<Track> = emptyList(),
)

/**
 * Hasil pencarian kaya lintas provider: Artis teratas + daftar Artis + Rilisan/Album + hingga 60 Lagu.
 */
data class RichSearchBundle(
    val query: String,
    val tracks: List<Track>,
    val artists: List<Artist>,
    val topArtist: Artist?,
    val releases: List<CatalogRelease>,
)

/**
 * Profil detail artis: Lagu Populer + Rilisan (Album/EP/Single).
 */
data class ArtistProfileBundle(
    val artist: Artist,
    val topTracks: List<Track>,
    val releases: List<CatalogRelease>,
)

/**
 * Cache Stale-While-Revalidate (SWR) thread-safe di memori lokal dengan TTL per jenis data.
 * Data lama langsung dikembalikan seketika, sementara pembaruan berjalan di latar belakang.
 */
class SwrCache<K : Any, V : Any>(
    private val ttlMs: Long,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    data class Entry<V>(
        val value: V,
        val storedAtMs: Long,
        val isStale: Boolean,
    )

    private data class Stored<V>(val value: V, val storedAtMs: Long)

    private val map = ConcurrentHashMap<K, Stored<V>>()

    fun get(key: K): Entry<V>? {
        val stored = map[key] ?: return null
        val stale = (clockMs() - stored.storedAtMs) > ttlMs
        return Entry(value = stored.value, storedAtMs = stored.storedAtMs, isStale = stale)
    }

    fun put(key: K, value: V) {
        map[key] = Stored(value, clockMs())
    }

    fun clear() {
        map.clear()
    }
}

/**
 * Pra-panas koneksi DNS + TLS + HTTP/2 connection pool saat app dibuka (ringan, tidak memblokir UI).
 */
object ProviderPrewarmer {
    private val warmUrls = listOf(
        "https://music.youtube.com/generate_204",
        "https://api.deezer.com/infos",
        "https://itunes.apple.com/search?term=a&limit=1&country=ID",
        "https://discoveryprovider.audius.co/health_check",
    )

    suspend fun prewarm(client: OkHttpClient) = withContext(Dispatchers.IO) {
        coroutineScope {
            warmUrls.map { url ->
                launch {
                    withTimeoutOrNull(3_000L) {
                        runCatching {
                            val req = Request.Builder().url(url).head().build()
                            client.newCall(req).execute().close()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Agregator katalog paralel/progresif lintas provider (Deezer + iTunes + InnerTube):
 * - Memanggil 2-3 provider secara paralel dengan batas konkurensi + circuit breaker + timeout 6 dtk.
 * - Hasil pertama langsung dipancarkan (Flow progresif), hasil berikutnya digabung + dedupe,
 *   memprioritaskan kelengkapan gambar/foto HD dan relevansi.
 * - SWR cache membuat hasil ulang & daftar artis tampil seketika.
 */
class MultiProviderCatalogAggregator(
    providers: List<CatalogProvider>,
    private val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
    private val perProviderTimeoutMs: Long = 6_000L,
    private val maxConcurrentRequests: Int = 4,
) {
    private val orderedProviders = providers.sortedBy { it.catalogPriority }
    private val concurrencyGate = Semaphore(maxConcurrentRequests)
    private val trackSearchCache = SwrCache<String, List<Track>>(ttlMs = 5 * 60_000L)
    private val artistSearchCache = SwrCache<String, List<Artist>>(ttlMs = 10 * 60_000L)

    fun searchTracksProgressive(query: String, limit: Int = 20): Flow<List<Track>> = channelFlow {
        val normQuery = query.trim().lowercase()
        val startMs = System.currentTimeMillis()
        var recordedFirst = false

        val cached = trackSearchCache.get(normQuery)
        if (cached != null && cached.value.isNotEmpty()) {
            MupaStreamingDiagnostics.recordFirstResult("search:$normQuery", "swr_cache", 0L, fromCache = true)
            recordedFirst = true
            send(cached.value)
            if (!cached.isStale) return@channelFlow
        }

        val merged = LinkedHashMap<String, Track>()
        val lock = Any()

        coroutineScope {
            orderedProviders.forEach { provider ->
                if (circuitBreaker.isOpen(provider.providerId)) return@forEach
                launch(Dispatchers.IO) {
                    val result = concurrencyGate.withPermit {
                        withTimeoutOrNull(perProviderTimeoutMs) {
                            provider.searchTracks(query, limit)
                        }
                    }
                    val list = result?.getOrNull()
                    if (list != null) {
                        circuitBreaker.recordSuccess(provider.providerId)
                        if (list.isNotEmpty()) {
                            val snapshot = synchronized(lock) {
                                mergeTracks(merged, list, normQuery, limit)
                            }
                            if (!recordedFirst) {
                                recordedFirst = true
                                MupaStreamingDiagnostics.recordFirstResult(
                                    label = "search:$normQuery",
                                    providerId = provider.providerId,
                                    durationMs = System.currentTimeMillis() - startMs,
                                )
                            }
                            send(snapshot)
                        }
                    } else {
                        val err = result?.exceptionOrNull()
                        if (err != null) circuitBreaker.recordFailure(provider.providerId)
                    }
                }
            }
        }

        val finalSnapshot = synchronized(lock) { merged.values.take(limit) }
        if (finalSnapshot.isNotEmpty()) {
            trackSearchCache.put(normQuery, finalSnapshot)
        }
    }

    suspend fun searchTracksMerged(query: String, limit: Int = 20): Result<List<Track>> {
        var latest: List<Track> = emptyList()
        searchTracksProgressive(query, limit).collect { latest = it }
        return if (latest.isNotEmpty()) {
            Result.success(latest)
        } else {
            Result.failure(com.xeno.mupa.streaming.api.StreamingError.Unavailable("Hasil tidak ditemukan untuk \"$query\""))
        }
    }

    fun observeArtistsProgressive(query: String, limit: Int = 24): Flow<List<Artist>> = channelFlow {
        val normKey = query.trim().lowercase().ifEmpty { "__trending__" }
        val startMs = System.currentTimeMillis()
        var recordedFirst = false

        val cached = artistSearchCache.get(normKey)
        if (cached != null && cached.value.isNotEmpty()) {
            MupaStreamingDiagnostics.recordFirstResult("artists:$normKey", "swr_cache", 0L, fromCache = true)
            recordedFirst = true
            send(cached.value)
            if (!cached.isStale) return@channelFlow
        }

        val merged = LinkedHashMap<String, Artist>()
        val lock = Any()

        coroutineScope {
            orderedProviders.forEach { provider ->
                if (circuitBreaker.isOpen(provider.providerId)) return@forEach
                launch(Dispatchers.IO) {
                    val result = concurrencyGate.withPermit {
                        withTimeoutOrNull(perProviderTimeoutMs) {
                            if (query.trim().length < 2) {
                                provider.getTrendingArtists(limit)
                            } else {
                                provider.searchArtists(query.trim(), limit)
                            }
                        }
                    }
                    val list = result?.getOrNull()
                    if (list != null) {
                        circuitBreaker.recordSuccess(provider.providerId)
                        if (list.isNotEmpty()) {
                            val snapshot = synchronized(lock) {
                                mergeArtists(merged, list, limit)
                            }
                            if (!recordedFirst) {
                                recordedFirst = true
                                MupaStreamingDiagnostics.recordFirstResult(
                                    label = "artists:$normKey",
                                    providerId = provider.providerId,
                                    durationMs = System.currentTimeMillis() - startMs,
                                )
                            }
                            send(snapshot)
                        }
                    } else if (result?.exceptionOrNull() != null) {
                        circuitBreaker.recordFailure(provider.providerId)
                    }
                }
            }
        }

        val finalSnapshot = synchronized(lock) { rankArtists(merged.values, limit) }
        if (finalSnapshot.isNotEmpty()) {
            artistSearchCache.put(normKey, finalSnapshot)
        }
    }

    suspend fun getArtistsMerged(query: String, limit: Int = 24): List<Artist> {
        var latest: List<Artist> = emptyList()
        observeArtistsProgressive(query, limit).collect { latest = it }
        return latest
    }

    suspend fun searchTracksParallelBatch(queries: List<String>, perQueryLimit: Int = 10): List<Track> =
        coroutineScope {
            queries.map { q ->
                async(Dispatchers.IO) {
                    searchTracksMerged(q, perQueryLimit).getOrDefault(emptyList())
                }
            }.awaitAll().flatten()
        }

    fun clearCaches() {
        trackSearchCache.clear()
        artistSearchCache.clear()
    }

    /**
     * Pencarian kaya paralel lintas 4 provider (Deezer + iTunes + YouTube Music + Audius):
     * mengembalikan hingga [trackLimit] lagu (default 60), daftar artis berfoto HD,
     * sorotan Artis Teratas (bila query cocok kuat dengan artis, mis. "jvke"), dan daftar Rilisan/Album.
     */
    suspend fun searchRich(query: String, trackLimit: Int = 60): Result<RichSearchBundle> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@coroutineScope Result.success(
                RichSearchBundle(
                    query = "",
                    tracks = emptyList(),
                    artists = emptyList(),
                    topArtist = null,
                    releases = emptyList(),
                ),
            )
        }

        val deezer = orderedProviders.filterIsInstance<DeezerCatalogProvider>().firstOrNull()
        val itunes = orderedProviders.filterIsInstance<ItunesCatalogProvider>().firstOrNull()

        val tracksJob = async(Dispatchers.IO) {
            searchTracksMerged(trimmed, limit = trackLimit).getOrDefault(emptyList())
        }
        val artistsJob = async(Dispatchers.IO) {
            getArtistsMerged(trimmed, limit = 16)
        }
        val dzAlbumsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(perProviderTimeoutMs) {
                deezer?.searchAlbums(trimmed, limit = 14)?.getOrDefault(emptyList())
            }.orEmpty()
        }
        val itAlbumsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(perProviderTimeoutMs) {
                itunes?.searchAlbums(trimmed, limit = 14)?.getOrDefault(emptyList())
            }.orEmpty()
        }

        val tracks = tracksJob.await()
        val rawArtists = artistsJob.await()
        val normQ = SmartQueueFilter.normalizeArtist(trimmed)

        val rankedArtists = rawArtists.sortedWith(
            compareByDescending<Artist> { artistMatchScore(it.name, normQ) }
                .thenByDescending { !it.artworkUrl.isNullOrBlank() },
        )
        val topArtist = rankedArtists.firstOrNull()?.takeIf { artistMatchScore(it.name, normQ) >= 80 }

        // Bila ada Artis Teratas (mis. "jvke"), ambil juga rilisan resminya agar album/EP/single-nya langsung tampil!
        val topArtistAlbums = if (topArtist != null && deezer != null) {
            withTimeoutOrNull(3_500L) {
                deezer.getArtistAlbums(topArtist.id, topArtist.name, limit = 14).getOrDefault(emptyList())
            }.orEmpty()
        } else {
            emptyList()
        }

        // Gabungkan rilisan dari album artis teratas + Deezer album search + iTunes album search + grup album dari tracks
        val derivedFromTracks = deriveReleasesFromTracks(tracks)
        val mergedReleases = mergeReleases(
            topArtistAlbums + dzAlbumsJob.await() + itAlbumsJob.await() + derivedFromTracks,
            normQ,
            topArtist?.name,
            limit = 24,
        )

        if (tracks.isEmpty() && rankedArtists.isEmpty() && mergedReleases.isEmpty()) {
            Result.failure(com.xeno.mupa.streaming.api.StreamingError.Unavailable("Hasil tidak ditemukan untuk \"$trimmed\""))
        } else {
            Result.success(
                RichSearchBundle(
                    query = trimmed,
                    tracks = tracks,
                    artists = rankedArtists,
                    topArtist = topArtist,
                    releases = mergedReleases,
                ),
            )
        }
    }

    /**
     * Memuat profil lengkap seorang artis (Lagu Populer hingga 40 lagu + daftar Rilisan/Album/EP/Single).
     */
    suspend fun getArtistProfile(artist: Artist): ArtistProfileBundle = coroutineScope {
        val deezer = orderedProviders.filterIsInstance<DeezerCatalogProvider>().firstOrNull()
        val itunes = orderedProviders.filterIsInstance<ItunesCatalogProvider>().firstOrNull()

        val dzTopJob = async(Dispatchers.IO) {
            withTimeoutOrNull(perProviderTimeoutMs) {
                deezer?.getArtistTopTracks(artist.id, artist.name)?.getOrDefault(emptyList())
            }.orEmpty()
        }
        val searchJob = async(Dispatchers.IO) {
            searchTracksMerged(artist.name, limit = 40).getOrDefault(emptyList())
        }
        val dzAlbumsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(perProviderTimeoutMs) {
                deezer?.getArtistAlbums(artist.id, artist.name, limit = 20)?.getOrDefault(emptyList())
            }.orEmpty()
        }
        val itAlbumsJob = async(Dispatchers.IO) {
            withTimeoutOrNull(perProviderTimeoutMs) {
                itunes?.searchAlbums(artist.name, limit = 16)?.getOrDefault(emptyList())
            }.orEmpty()
        }

        val normArtist = SmartQueueFilter.normalizeArtist(artist.name)
        val mergedMap = LinkedHashMap<String, Track>()
        val combined = dzTopJob.await() + searchJob.await().filter { t ->
            val a = SmartQueueFilter.normalizeArtist(t.artistName)
            a.contains(normArtist) || normArtist.contains(a) || t.title.lowercase().contains(normArtist)
        }
        val topTracks = mergeTracks(mergedMap, combined, normArtist, limit = 40)
        val derived = deriveReleasesFromTracks(topTracks)
        val releases = mergeReleases(
            dzAlbumsJob.await() + itAlbumsJob.await() + derived,
            normQuery = normArtist,
            focusArtistName = artist.name,
            limit = 24,
        )

        ArtistProfileBundle(
            artist = artist,
            topTracks = topTracks,
            releases = releases,
        )
    }

    /**
     * Memuat daftar lagu lengkap untuk sebuah Rilisan/Album/EP/Single.
     */
    suspend fun getReleaseTracks(release: CatalogRelease): List<Track> = coroutineScope {
        val deezer = orderedProviders.filterIsInstance<DeezerCatalogProvider>().firstOrNull()
        val itunes = orderedProviders.filterIsInstance<ItunesCatalogProvider>().firstOrNull()

        val direct = when {
            release.id.startsWith("dz_album_") && deezer != null -> {
                withTimeoutOrNull(perProviderTimeoutMs) {
                    deezer.getAlbumTracks(release.id, release.title, release.artworkUrl).getOrNull()
                }.orEmpty()
            }
            release.id.startsWith("it_album_") && itunes != null -> {
                withTimeoutOrNull(perProviderTimeoutMs) {
                    itunes.getAlbumTracks(release.id).getOrNull()
                }.orEmpty()
            }
            else -> emptyList()
        }
        if (direct.isNotEmpty()) return@coroutineScope direct
        if (release.cachedTracks.isNotEmpty()) return@coroutineScope release.cachedTracks

        // Fallback: cari berdasarkan judul album + artis
        searchTracksMerged("${release.artistName} ${release.title}", limit = 25).getOrDefault(emptyList())
    }

    private fun artistMatchScore(artistName: String, normQuery: String): Int {
        if (normQuery.isBlank()) return 0
        val normArtist = SmartQueueFilter.normalizeArtist(artistName)
        return when {
            normArtist == normQuery -> 120
            normArtist.startsWith(normQuery) && normQuery.length >= 2 -> 95
            normQuery.startsWith(normArtist) && normArtist.length >= 3 -> 85
            normArtist.contains(normQuery) && normQuery.length >= 3 -> 70
            else -> 0
        }
    }

    private fun deriveReleasesFromTracks(tracks: List<Track>): List<CatalogRelease> =
        tracks
            .filter { !it.albumTitle.isNullOrBlank() }
            .groupBy { "${it.albumTitle!!.trim().lowercase()}|${SmartQueueFilter.normalizeArtist(it.artistName)}" }
            .values
            .mapNotNull { group ->
                val first = group.firstOrNull() ?: return@mapNotNull null
                val title = first.albumTitle?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                CatalogRelease(
                    id = "trk_album_${title.lowercase().hashCode()}_${first.artistName.lowercase().hashCode()}",
                    title = title,
                    artistName = first.artistName,
                    artworkUrl = group.firstNotNullOfOrNull { it.artworkUrl },
                    releaseType = if (group.size <= 1) "Single" else "Album",
                    trackCount = group.size,
                    cachedTracks = group,
                )
            }

    private fun mergeReleases(
        incoming: List<CatalogRelease>,
        normQuery: String,
        focusArtistName: String?,
        limit: Int,
    ): List<CatalogRelease> {
        val map = LinkedHashMap<String, CatalogRelease>()
        for (rel in incoming) {
            val key = "${rel.title.trim().lowercase()}|${SmartQueueFilter.normalizeArtist(rel.artistName)}"
            val existing = map[key]
            if (existing == null) {
                map[key] = rel
            } else {
                // Utamakan rilisan ber-ID resmi Deezer/iTunes yang punya tracklist lengkap & tahun rilis
                val existingOfficial = existing.id.startsWith("dz_album_") || existing.id.startsWith("it_album_")
                val incomingOfficial = rel.id.startsWith("dz_album_") || rel.id.startsWith("it_album_")
                val base = if (incomingOfficial && !existingOfficial) rel else existing
                val other = if (base === existing) rel else existing
                map[key] = base.copy(
                    artworkUrl = base.artworkUrl?.takeIf { it.isNotBlank() } ?: other.artworkUrl,
                    releaseYear = base.releaseYear ?: other.releaseYear,
                    trackCount = maxOf(base.trackCount, other.trackCount),
                    cachedTracks = if (base.cachedTracks.isNotEmpty()) base.cachedTracks else other.cachedTracks,
                )
            }
        }
        val focusNorm = focusArtistName?.let { SmartQueueFilter.normalizeArtist(it) }.orEmpty()
        return map.values
            .sortedWith(
                compareByDescending<CatalogRelease> { rel ->
                    var score = 0
                    val relArtist = SmartQueueFilter.normalizeArtist(rel.artistName)
                    val relTitle = rel.title.lowercase()
                    if (focusNorm.isNotEmpty() && (relArtist == focusNorm || relArtist.contains(focusNorm))) score += 100
                    if (relTitle == normQuery) score += 90
                    else if (relTitle.contains(normQuery)) score += 50
                    if (relArtist.contains(normQuery)) score += 45
                    if (rel.releaseType == "Album") score += 15
                    else if (rel.releaseType == "EP") score += 10
                    score
                }
                    .thenByDescending { it.releaseYear ?: "0000" }
                    .thenByDescending { !it.artworkUrl.isNullOrBlank() },
            )
            .take(limit)
    }

    private fun mergeTracks(
        target: LinkedHashMap<String, Track>,
        incoming: List<Track>,
        normQuery: String,
        limit: Int,
    ): List<Track> {
        for (track in incoming) {
            val key = SmartQueueFilter.normalizedTrackKey(track)
            val existing = target[key]
            if (existing == null) {
                target[key] = track
            } else {
                // Utamakan entri yang punya artwork HD, durasi valid, dan ID stream langsung (YouTube 11-char)
                target[key] = pickRicherTrack(existing, track)
            }
        }
        return target.values
            .sortedWith(
                compareByDescending<Track> { relevanceScore(it, normQuery) }
                    .thenByDescending { !it.artworkUrl.isNullOrBlank() }
                    .thenByDescending { it.durationMs > 0L },
            )
            .take(limit)
    }

    private fun pickRicherTrack(a: Track, b: Track): Track {
        val aIsDirect = !a.id.startsWith("dz_") && !a.id.startsWith("it_") && !a.id.startsWith("au_")
        val bIsDirect = !b.id.startsWith("dz_") && !b.id.startsWith("it_") && !b.id.startsWith("au_")
        val base = if (bIsDirect && !aIsDirect) b else a
        val other = if (base === a) b else a
        return base.copy(
            artworkUrl = base.artworkUrl?.takeIf { it.isNotBlank() } ?: other.artworkUrl,
            albumTitle = base.albumTitle?.takeIf { it.isNotBlank() } ?: other.albumTitle,
            durationMs = if (base.durationMs > 0L) base.durationMs else other.durationMs,
        )
    }

    private fun relevanceScore(track: Track, normQuery: String): Int {
        if (normQuery.isBlank()) return 0
        val title = track.title.lowercase()
        val artist = track.artistName.lowercase()
        val combined = "$title $artist"
        var score = 0
        if (title == normQuery) score += 110
        else if (title.startsWith(normQuery)) score += 70
        else if (title.contains(normQuery)) score += 45
        if (artist == normQuery) score += 55
        else if (artist.contains(normQuery)) score += 30

        // Pencocokan berbasis kata kunci (mis. "phonk tiki tiki" tetap mengangkat "TIKI TIKI - QMIIR" ke puncak)
        val queryTokens = normQuery
            .split(Regex("""[^a-z0-9]+"""))
            .filter { it.length >= 2 }
            .distinct()
        if (queryTokens.isNotEmpty()) {
            val titleHits = queryTokens.count { title.contains(it) }
            val combinedHits = queryTokens.count { combined.contains(it) }
            score += (titleHits * 45) / queryTokens.size
            score += (combinedHits * 25) / queryTokens.size
            if (titleHits == queryTokens.size) score += 25
        }

        // Jangan penalti versi slowed/remix/phonk/live bila pengguna memang mengetik kata tersebut
        val queryWantsAlt = ALT_QUERY_HINTS.any { normQuery.contains(it) }
        if (!queryWantsAlt && SmartQueueFilter.isAlternateVersionTitle(track.title)) {
            score -= 25
        }
        return score
    }

    companion object {
        private val ALT_QUERY_HINTS = listOf(
            "phonk", "funk", "slowed", "sped", "speed", "remix", "dj",
            "reverb", "nightcore", "bass", "live", "cover", "acoustic",
            "karaoke", "instrumental", "edit", "mashup", "koplo",
        )
    }

    private fun mergeArtists(
        target: LinkedHashMap<String, Artist>,
        incoming: List<Artist>,
        limit: Int,
    ): List<Artist> {
        for (artist in incoming) {
            val key = SmartQueueFilter.normalizeArtist(artist.name)
            if (key.isEmpty()) continue
            val existing = target[key]
            if (existing == null || (existing.artworkUrl.isNullOrBlank() && !artist.artworkUrl.isNullOrBlank())) {
                target[key] = artist
            }
        }
        return rankArtists(target.values, limit)
    }

    private fun rankArtists(values: Collection<Artist>, limit: Int): List<Artist> =
        values
            .distinctBy { it.id }
            .sortedByDescending { !it.artworkUrl.isNullOrBlank() }
            .take(limit)
}
