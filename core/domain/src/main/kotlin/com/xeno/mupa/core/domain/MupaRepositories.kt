package com.xeno.mupa.core.domain

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface MusicSource {
    val sourceId: String
    suspend fun getHomeRecommendations(
        seedArtistIds: Set<String>,
        source: AudioSourceType = AudioSourceType.STREAMING,
    ): Result<List<Playlist>>

    suspend fun searchTracks(
        query: String,
        source: AudioSourceType = AudioSourceType.STREAMING,
    ): Result<List<Track>>

    suspend fun getLibraryTracks(
        source: AudioSourceType = AudioSourceType.STREAMING,
    ): Result<List<Track>>
}

interface StreamProvider {
    val providerId: String
    suspend fun resolveStreamUrl(track: Track): Result<ResolvedStream>
}

data class ResolvedStream(
    val url: String,
    val mimeType: String? = null,
    val bitrateKbps: Int? = null,
    val expiresAtEpochMs: Long? = null,
    val headers: Map<String, String> = emptyMap(),
)

interface ArtistRepository {
    suspend fun getSuggestedArtists(query: String = ""): Result<List<Artist>>
    suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>>
}

interface PreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(artistIds: Set<String>)
    suspend fun updateFavoriteArtists(artistIds: Set<String>)
    suspend fun setGlassStyle(style: GlassStyle)
    suspend fun setOledMode(enabled: Boolean)
    suspend fun setAuroraEnabled(enabled: Boolean)
    suspend fun setPerformanceMode(enabled: Boolean)
    suspend fun setStripArtistName(enabled: Boolean)
    suspend fun setActiveSource(source: AudioSourceType)
    suspend fun setCrossfadeSeconds(seconds: Int) = Unit
}
