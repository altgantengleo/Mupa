package com.xeno.mupa.streaming.aggregator

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.Track
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
        val aIsDirect = !a.id.startsWith("dz_") && !a.id.startsWith("it_")
        val bIsDirect = !b.id.startsWith("dz_") && !b.id.startsWith("it_")
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
        var score = 0
        if (title == normQuery) score += 100
        else if (title.startsWith(normQuery)) score += 60
        else if (title.contains(normQuery)) score += 35
        if (artist == normQuery) score += 50
        else if (artist.contains(normQuery)) score += 25
        if (SmartQueueFilter.isAlternateVersionTitle(track.title)) score -= 30
        return score
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
        values.sortedByDescending { !it.artworkUrl.isNullOrBlank() }.take(limit)
}
