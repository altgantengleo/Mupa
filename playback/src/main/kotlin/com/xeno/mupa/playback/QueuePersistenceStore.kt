package com.xeno.mupa.playback

import androidx.annotation.DrawableRes
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class PlaybackServiceConfig(
    @DrawableRes val notificationSmallIconRes: Int,
    val audioCacheMaxBytes: Long = DEFAULT_AUDIO_CACHE_BYTES,
) {
    companion object {
        /** Default 500 MB (Default D3 di docs/QUESTIONS.md). */
        const val DEFAULT_AUDIO_CACHE_BYTES: Long = 500L * 1024L * 1024L
    }
}

@Serializable
data class PersistedQueueItem(
    val id: String,
    val title: String,
    val artistName: String,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val artworkUrl: String? = null,
    val sourceType: String = AudioSourceType.STREAMING.name,
    val mediaUri: String? = null,
)

@Serializable
data class PersistedQueueSnapshot(
    val items: List<PersistedQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val savedAtEpochMs: Long = 0L,
)

/**
 * Penyimpanan antrean putar dalam format JSON ringan (menggantikan ObjectOutputStream).
 * Aman terhadap file rusak atau perubahan skema.
 */
class QueuePersistenceStore(
    private val file: File,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun save(
        tracks: List<Track>,
        currentIndex: Int,
        positionMs: Long,
        mediaUrisByTrackId: Map<String, String> = emptyMap(),
    ) {
        val snapshot = PersistedQueueSnapshot(
            items = tracks.map { track ->
                PersistedQueueItem(
                    id = track.id,
                    title = track.title,
                    artistName = track.artistName,
                    albumTitle = track.albumTitle,
                    durationMs = track.durationMs,
                    artworkUrl = track.artworkUrl,
                    sourceType = track.sourceType.name,
                    mediaUri = mediaUrisByTrackId[track.id],
                )
            },
            currentIndex = currentIndex.coerceIn(0, (tracks.size - 1).coerceAtLeast(0)),
            positionMs = positionMs.coerceAtLeast(0L),
            savedAtEpochMs = System.currentTimeMillis(),
        )
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        }
    }

    fun load(): PersistedQueueSnapshot? {
        if (!file.exists()) return null
        return runCatching {
            json.decodeFromString<PersistedQueueSnapshot>(file.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }
}
