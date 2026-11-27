package com.xeno.mupa.streaming.aggregator

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.streaming.api.MupaStreamingDiagnostics
import com.xeno.mupa.streaming.api.StreamLookupRequest
import com.xeno.mupa.streaming.api.StreamingAudioProvider
import com.xeno.mupa.streaming.api.StreamingError
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import java.util.concurrent.ConcurrentHashMap

/**
 * Circuit Breaker per penyedia streaming untuk mencegah kegagalan beruntun dan spam jaringan.
 */
class ProviderCircuitBreaker(
    private val failureThreshold: Int = 3,
    private val openDurationMs: Long = 60_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class BreakerState(
        val consecutiveFailures: Int = 0,
        val openedUntilMs: Long = 0L,
    )

    private val states = ConcurrentHashMap<String, BreakerState>()

    fun isOpen(providerId: String): Boolean {
        val state = states[providerId] ?: return false
        return state.openedUntilMs > clockMs()
    }

    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    fun recordFailure(providerId: String) {
        states.compute(providerId) { _, existing ->
            val current = existing ?: BreakerState()
            val nextFailures = current.consecutiveFailures + 1
            if (nextFailures >= failureThreshold) {
                BreakerState(
                    consecutiveFailures = nextFailures,
                    openedUntilMs = clockMs() + openDurationMs,
                )
            } else {
                current.copy(consecutiveFailures = nextFailures)
            }
        }
    }
}

/**
 * Cache URL stream thread-safe (ConcurrentHashMap — memperbaiki data race HashMap di AUDIT.md).
 */
class ThreadSafeStreamUrlCache(
    private val defaultTtlMs: Long = 20 * 60 * 1_000L,
    private val safetyMarginMs: Long = 60_000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private data class Entry(
        val stream: ResolvedStream,
        val validUntilMs: Long,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun get(trackId: String): ResolvedStream? {
        val entry = entries[trackId] ?: return null
        if (clockMs() >= entry.validUntilMs) {
            entries.remove(trackId)
            return null
        }
        return entry.stream
    }

    fun put(trackId: String, stream: ResolvedStream) {
        val expiry = stream.expiresAtEpochMs ?: (clockMs() + defaultTtlMs)
        entries[trackId] = Entry(
            stream = stream,
            validUntilMs = (expiry - safetyMarginMs).coerceAtLeast(clockMs() + 5_000L),
        )
    }

    fun invalidate(trackId: String) {
        entries.remove(trackId)
    }

    fun clear() {
        entries.clear()
    }
}

/**
 * Registry + Router multi-penyedia dengan prioritas, retry backoff, fallback, circuit breaker, dan URL cache.
 */
class ProviderRegistry(
    providers: List<StreamingAudioProvider>,
    private val circuitBreaker: ProviderCircuitBreaker = ProviderCircuitBreaker(),
    private val urlCache: ThreadSafeStreamUrlCache = ThreadSafeStreamUrlCache(),
    private val maxRetryPerProvider: Int = 1,
    private val baseRetryDelayMs: Long = 350L,
    private val hedgeDelayMs: Long = 800L,
) {
    private val orderedProviders: List<StreamingAudioProvider> = providers.sortedBy { it.priority }

    data class ResolutionOutcome(
        val providerId: String,
        val stream: ResolvedStream,
        val fromCache: Boolean,
    )

    fun registeredProviderIds(): List<String> = orderedProviders.map { it.providerId }

    fun invalidateCachedStream(trackId: String) {
        urlCache.invalidate(trackId)
    }

    suspend fun resolve(request: StreamLookupRequest, forceRefresh: Boolean = false): Result<ResolutionOutcome> {
        val startMs = System.currentTimeMillis()
        if (!forceRefresh) {
            urlCache.get(request.trackId)?.let { cached ->
                MupaStreamingDiagnostics.recordTimeToAudio(request.title, "cache", 0L, fromCache = true)
                return Result.success(
                    ResolutionOutcome(
                        providerId = "cache",
                        stream = cached,
                        fromCache = true,
                    ),
                )
            }
        } else {
            urlCache.invalidate(request.trackId)
        }

        val activeProviders = orderedProviders.filterNot { circuitBreaker.isOpen(it.providerId) }
        if (activeProviders.isEmpty()) {
            val firstId = orderedProviders.firstOrNull()?.providerId ?: "none"
            return Result.failure(
                if (orderedProviders.isNotEmpty()) StreamingError.CircuitOpen(firstId)
                else StreamingError.Unavailable("Tidak ada penyedia streaming aktif"),
            )
        }

        // Hedged resolution (~800ms): jalankan provider primer lebih dulu; bila belum selesai dalam hedgeDelayMs
        // dan ada provider kedua, picu provider kedua secara paralel lalu ambil respons sukses pertama.
        if (activeProviders.size >= 2 && hedgeDelayMs > 0L) {
            val primary = activeProviders[0]
            val secondary = activeProviders[1]
            val hedged = resolveHedgedPair(primary, secondary, request, startMs)
            if (hedged != null) return Result.success(hedged)
        }

        var lastError: Throwable = StreamingError.Unavailable("Tidak ada penyedia streaming aktif")

        for (provider in activeProviders) {
            if (circuitBreaker.isOpen(provider.providerId)) {
                lastError = StreamingError.CircuitOpen(provider.providerId)
                continue
            }

            val result = resolveWithRetry(provider, request)
            val stream = result.getOrNull()
            if (stream != null) {
                circuitBreaker.recordSuccess(provider.providerId)
                urlCache.put(request.trackId, stream)
                MupaStreamingDiagnostics.recordTimeToAudio(
                    label = request.title,
                    providerId = provider.providerId,
                    durationMs = System.currentTimeMillis() - startMs,
                )
                return Result.success(
                    ResolutionOutcome(
                        providerId = provider.providerId,
                        stream = stream,
                        fromCache = false,
                    ),
                )
            }

            val err = result.exceptionOrNull()
            if (err != null) {
                lastError = err
                if (err !is StreamingError || err.tripsCircuitBreaker) {
                    circuitBreaker.recordFailure(provider.providerId)
                }
            }
        }

        return Result.failure(lastError)
    }

    private suspend fun resolveHedgedPair(
        primary: StreamingAudioProvider,
        secondary: StreamingAudioProvider,
        request: StreamLookupRequest,
        startMs: Long,
    ): ResolutionOutcome? = coroutineScope {
        val primaryDeferred = async { primary.providerId to resolveWithRetry(primary, request) }
        val earlyPrimary = select<Pair<String, Result<ResolvedStream>>?> {
            primaryDeferred.onAwait { it }
            async { delay(hedgeDelayMs) }.onAwait { null }
        }
        if (earlyPrimary != null) {
            val stream = earlyPrimary.second.getOrNull()
            if (stream != null) {
                coroutineContext.cancelChildren()
                return@coroutineScope finalizeSuccess(earlyPrimary.first, stream, request, startMs)
            }
            recordError(earlyPrimary.first, earlyPrimary.second.exceptionOrNull())
            val secResult = resolveWithRetry(secondary, request)
            val secStream = secResult.getOrNull()
            coroutineContext.cancelChildren()
            return@coroutineScope if (secStream != null) {
                finalizeSuccess(secondary.providerId, secStream, request, startMs)
            } else {
                recordError(secondary.providerId, secResult.exceptionOrNull())
                null
            }
        }

        // Primary melewati 800ms -> picu secondary secara hedged paralel
        val secondaryDeferred = async { secondary.providerId to resolveWithRetry(secondary, request) }
        val firstWinner = select<Pair<String, Result<ResolvedStream>>> {
            primaryDeferred.onAwait { it }
            secondaryDeferred.onAwait { it }
        }
        val firstStream = firstWinner.second.getOrNull()
        if (firstStream != null) {
            coroutineContext.cancelChildren()
            return@coroutineScope finalizeSuccess(firstWinner.first, firstStream, request, startMs)
        }
        recordError(firstWinner.first, firstWinner.second.exceptionOrNull())

        val other = if (firstWinner.first == primary.providerId) secondaryDeferred.await() else primaryDeferred.await()
        val otherStream = other.second.getOrNull()
        coroutineContext.cancelChildren()
        if (otherStream != null) {
            finalizeSuccess(other.first, otherStream, request, startMs)
        } else {
            recordError(other.first, other.second.exceptionOrNull())
            null
        }
    }

    private fun finalizeSuccess(
        providerId: String,
        stream: ResolvedStream,
        request: StreamLookupRequest,
        startMs: Long,
    ): ResolutionOutcome {
        circuitBreaker.recordSuccess(providerId)
        urlCache.put(request.trackId, stream)
        MupaStreamingDiagnostics.recordTimeToAudio(
            label = request.title,
            providerId = providerId,
            durationMs = System.currentTimeMillis() - startMs,
        )
        return ResolutionOutcome(providerId = providerId, stream = stream, fromCache = false)
    }

    private fun recordError(providerId: String, err: Throwable?) {
        if (err != null && (err !is StreamingError || err.tripsCircuitBreaker)) {
            circuitBreaker.recordFailure(providerId)
        }
    }

    private suspend fun resolveWithRetry(
        provider: StreamingAudioProvider,
        request: StreamLookupRequest,
    ): Result<ResolvedStream> {
        var attempt = 0
        var lastResult: Result<ResolvedStream> = Result.failure(StreamingError.Unavailable())
        while (attempt <= maxRetryPerProvider) {
            lastResult = provider.resolveAudio(request)
            if (lastResult.isSuccess) return lastResult
            val err = lastResult.exceptionOrNull()
            val retryable = err is StreamingError && err.isRetryable
            if (!retryable || attempt == maxRetryPerProvider) {
                return lastResult
            }
            val backoff = baseRetryDelayMs * (1L shl attempt)
            delay(backoff)
            attempt++
        }
        return lastResult
    }
}
