package com.xeno.mupa.streaming.aggregator

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.streaming.api.StreamLookupRequest
import com.xeno.mupa.streaming.api.StreamingAudioProvider
import com.xeno.mupa.streaming.api.StreamingError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AggregatorFallbackTest {

    @Test
    fun fallsBackToSecondaryProviderWhenPrimaryHitsBotCheckAndCachesWinner() = runTest {
        var primaryCalls = 0
        var secondaryCalls = 0

        val primary = object : StreamingAudioProvider {
            override val providerId = "innertube"
            override val priority = 10
            override val supportsDirectTrackId = true
            override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> {
                primaryCalls++
                return Result.failure(StreamingError.BotCheck())
            }
        }

        val secondary = object : StreamingAudioProvider {
            override val providerId = "jiosaavn"
            override val priority = 20
            override val supportsDirectTrackId = false
            override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> {
                secondaryCalls++
                return Result.success(
                    ResolvedStream(
                        url = "https://fallback.example/song.mp4",
                        mimeType = "audio/mp4",
                        bitrateKbps = 320,
                    ),
                )
            }
        }

        val registry = ProviderRegistry(
            providers = listOf(secondary, primary), // sengaja dibalik untuk uji sorting prioritas
            baseRetryDelayMs = 1L,
        )
        val req = StreamLookupRequest("trk-1", "Evaluasi", "Hindia")

        val first = registry.resolve(req).getOrThrow()
        assertEquals("jiosaavn", first.providerId)
        assertFalse(first.fromCache)
        assertEquals(1, primaryCalls)
        assertEquals(1, secondaryCalls)

        // Panggilan kedua harus dilayani dari URL cache thread-safe
        val second = registry.resolve(req).getOrThrow()
        assertTrue(second.fromCache)
        assertEquals(1, secondaryCalls)
    }

    @Test
    fun circuitBreakerOpensAfterThresholdAndRecoversAfterCooldown() = runTest {
        var now = 1_000L
        val breaker = ProviderCircuitBreaker(
            failureThreshold = 2,
            openDurationMs = 30_000L,
            clockMs = { now },
        )

        breaker.recordFailure("innertube")
        assertFalse(breaker.isOpen("innertube"))

        breaker.recordFailure("innertube")
        assertTrue(breaker.isOpen("innertube"))

        now += 31_000L
        assertFalse(breaker.isOpen("innertube"))
    }
}
