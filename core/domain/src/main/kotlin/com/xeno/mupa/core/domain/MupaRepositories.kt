package com.xeno.mupa.core.domain

import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Kontrak abstrak untuk sumber katalog musik (fake di Tahap 1, streaming di Tahap 2, lokal Rust di Tahap 3).
 * UI tidak boleh bergantung langsung pada penyedia tertentu.
 */
interface MusicSource {
    val sourceId: String
    suspend fun getHomeRecommendations(seedArtistIds: Set<String>): Result<List<Playlist>>
    suspend fun searchTracks(query: String): Result<List<Track>>
    suspend fun getLibraryTracks(): Result<List<Track>>
}

/**
 * Kontrak resolusi URL stream yang terisolasi per penyedia di Tahap 2.
 */
data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrateKbps: Int? = null,
    val headers: Map<String, String> = emptyMap(),
    val expiresAtEpochMs: Long? = null,
)

interface StreamProvider {
    val providerId: String
    val priority: Int
    suspend fun resolveStream(trackId: String): Result<ResolvedStream>
}

/**
 * Kontrak daftar artis untuk Onboarding dan personalisasi Beranda.
 */
interface ArtistRepository {
    suspend fun getSuggestedArtists(query: String = ""): Result<List<Artist>>
    suspend fun getArtistsByIds(ids: Set<String>): Result<List<Artist>>
}

/**
 * Kontrak penyimpanan preferensi lokal Mupa (DataStore).
 */
interface PreferencesRepository {
    val userPreferences: Flow<UserPreferences>
    suspend fun setOnboardingCompleted(artistIds: Set<String>)
    suspend fun updateFavoriteArtists(artistIds: Set<String>)
    suspend fun setGlassStyle(style: GlassStyle)
    suspend fun setOledMode(enabled: Boolean)
    suspend fun setAuroraEnabled(enabled: Boolean)
    suspend fun setPerformanceMode(enabled: Boolean)
    suspend fun setStripArtistName(enabled: Boolean)
}
