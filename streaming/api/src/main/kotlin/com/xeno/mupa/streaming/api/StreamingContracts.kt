package com.xeno.mupa.streaming.api

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track

/**
 * Error bertipe untuk seluruh lapisan streaming Mupa agar fallback, retry,
 * dan circuit breaker dapat mengambil keputusan deterministik.
 */
sealed class StreamingError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {
    class Network(message: String, cause: Throwable? = null) : StreamingError(message, cause)
    class RateLimited(val retryAfterSeconds: Long? = null, message: String = "Terkena batas permintaan (HTTP 429)") :
        StreamingError(message)
    class GeoBlocked(message: String = "Konten dibatasi di wilayah ini") : StreamingError(message)
    class BotCheck(message: String = "Penyedia meminta verifikasi anti-bot") : StreamingError(message)
    class Unavailable(message: String = "Lagu tidak tersedia di penyedia ini") : StreamingError(message)
    class ParseError(message: String, cause: Throwable? = null) : StreamingError(message, cause)
    class CircuitOpen(val providerId: String) : StreamingError("Circuit breaker terbuka untuk $providerId")
    class Unknown(message: String, cause: Throwable? = null) : StreamingError(message, cause)

    val isRetryable: Boolean
        get() = this is Network || this is RateLimited

    val tripsCircuitBreaker: Boolean
        get() = this is Network || this is RateLimited || this is BotCheck || this is ParseError
}

enum class AudioQualityPreference {
    LOW,
    MEDIUM,
    HIGH,
}

data class StreamLookupRequest(
    val trackId: String,
    val title: String,
    val artistName: String,
    val durationMs: Long = 0L,
    val quality: AudioQualityPreference = AudioQualityPreference.HIGH,
)

interface StreamingAudioProvider {
    val providerId: String
    val priority: Int
    val supportsDirectTrackId: Boolean

    suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream>
}

interface StreamingCatalogProvider {
    val providerId: String
    suspend fun searchTracks(query: String, limit: Int = 20): Result<List<Track>>
    suspend fun searchArtists(query: String, limit: Int = 16): Result<List<Artist>>
    suspend fun getArtistTopTracks(artistId: String, artistName: String): Result<List<Track>>
}

interface StreamingLyricsProvider {
    val providerId: String
    suspend fun fetchLyrics(
        title: String,
        artistName: String,
        durationMs: Long,
    ): Result<List<LyricsLine>>
}
