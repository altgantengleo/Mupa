package com.xeno.mupa.core.model

enum class AudioSourceType {
    LOCAL,
    STREAMING,
}

enum class GlassStyle {
    CRYSTAL,
    LIQUID_PLUS,
}

data class Artist(
    val id: String,
    val name: String,
    val artworkUrl: String? = null,
    val genre: String? = null,
    val sourceType: AudioSourceType = AudioSourceType.STREAMING,
)

data class Album(
    val id: String,
    val title: String,
    val artistName: String,
    val artworkUrl: String? = null,
    val year: Int? = null,
)

data class Track(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val accentColorArgb: Long? = null,
    val sourceType: AudioSourceType = AudioSourceType.STREAMING,
    val originalFileName: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
)

data class Playlist(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val tracks: List<Track> = emptyList(),
    val isSystemGenerated: Boolean = false,
)

data class LyricsLine(
    val timestampMs: Long,
    val endTimestampMs: Long? = null,
    val text: String,
)

data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val favoriteArtistIds: Set<String> = emptySet(),
    val glassStyle: GlassStyle = GlassStyle.LIQUID_PLUS,
    val isOledMode: Boolean = false,
    val isAuroraEnabled: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val stripArtistName: Boolean = true,
)
