package com.xeno.mupa.playback

import androidx.annotation.DrawableRes
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class PlaybackServiceConfig(
    @DrawableRes val notificationSmallIconRes: Int,
    @DrawableRes val favoriteOffIconRes: Int = notificationSmallIconRes,
    @DrawableRes val favoriteOnIconRes: Int = notificationSmallIconRes,
    @DrawableRes val shuffleOffIconRes: Int = notificationSmallIconRes,
    @DrawableRes val shuffleOnIconRes: Int = notificationSmallIconRes,
    val audioCacheMaxBytes: Long = DEFAULT_AUDIO_CACHE_BYTES,
) {
    companion object {
        /** Default 500 MB (Default D3 di docs/QUESTIONS.md). */
        const val DEFAULT_AUDIO_CACHE_BYTES: Long = 500L * 1024L * 1024L
    }
}

/**
 * Jembatan sinkronisasi ringan antara MupaPlayerViewModel dan MupaPlaybackService
 * agar tombol Favorit & Acak di Dynamic Island / HyperIsland / Notifikasi Media selalu tersinkron live.
 */
object MupaMediaSessionBridge {
    @Volatile
    var isCurrentFavorite: Boolean = false
        private set

    @Volatile
    var isShuffleEnabled: Boolean = false
        private set

    @Volatile
    var onToggleFavoriteCommand: (() -> Unit)? = null

    @Volatile
    var onToggleShuffleCommand: (() -> Unit)? = null

    @Volatile
    private var onSessionLayoutRefresh: (() -> Unit)? = null

    fun attachSessionRefresher(refresher: (() -> Unit)?) {
        onSessionLayoutRefresh = refresher
    }

    fun updateState(isFavorite: Boolean, isShuffle: Boolean) {
        val changed = (isCurrentFavorite != isFavorite) || (isShuffleEnabled != isShuffle)
        isCurrentFavorite = isFavorite
        isShuffleEnabled = isShuffle
        if (changed) {
            onSessionLayoutRefresh?.invoke()
        }
    }

    fun dispatchToggleFavorite() {
        val handler = onToggleFavoriteCommand
        if (handler != null) {
            handler.invoke()
        } else {
            isCurrentFavorite = !isCurrentFavorite
            onSessionLayoutRefresh?.invoke()
        }
    }

    fun dispatchToggleShuffle() {
        val handler = onToggleShuffleCommand
        if (handler != null) {
            handler.invoke()
        } else {
            isShuffleEnabled = !isShuffleEnabled
            onSessionLayoutRefresh?.invoke()
        }
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
data class PersistedQueueSource(
    val kind: String = "LOCAL",
    val id: String = "",
    val title: String = "Pustaka Lokal",
    val subtitle: String = "",
    val isAlbum: Boolean = false,
) {
    fun toDomain(): QueueSource = when (kind) {
        "RADIO" -> QueueSource.Radio(seedTrackId = id, seedTitle = title, seedArtist = subtitle)
        "ARTIST" -> QueueSource.Artist(artistId = id, artistName = title)
        "PLAYLIST_OR_ALBUM" -> QueueSource.PlaylistOrAlbum(collectionId = id, title = title, isAlbum = isAlbum)
        "MIX_MUPA" -> QueueSource.MixMupa(label = title.ifBlank { "Mix Mupa" })
        else -> QueueSource.Local(label = title.ifBlank { "Pustaka Lokal" })
    }

    companion object {
        fun fromDomain(source: QueueSource): PersistedQueueSource = when (source) {
            is QueueSource.Radio -> PersistedQueueSource("RADIO", source.seedTrackId, source.seedTitle, source.seedArtist)
            is QueueSource.Artist -> PersistedQueueSource("ARTIST", source.artistId, source.artistName)
            is QueueSource.PlaylistOrAlbum -> PersistedQueueSource("PLAYLIST_OR_ALBUM", source.collectionId, source.title, isAlbum = source.isAlbum)
            is QueueSource.MixMupa -> PersistedQueueSource("MIX_MUPA", "", source.label)
            is QueueSource.Local -> PersistedQueueSource("LOCAL", "", source.label)
        }
    }
}

@Serializable
data class PersistedQueueSnapshot(
    val items: List<PersistedQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val savedAtEpochMs: Long = 0L,
    val source: PersistedQueueSource = PersistedQueueSource(),
    val continuationToken: String? = null,
    val radioPlaylistId: String? = null,
    val isInfinite: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val shuffleSeed: Long = 0L,
    val originalOrderIds: List<String> = emptyList(),
    val recentTrackIds: List<String> = emptyList(),
)

/**
 * Penyimpanan antrean putar dalam format JSON ringan (Fase 2T: menyertakan QueueSource,
 * continuation token, status shuffle/seed, dan 20 lagu terakhir agar pulih utuh setelah app ditutup).
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
        source: QueueSource = QueueSource.Local(),
        continuationToken: String? = null,
        radioPlaylistId: String? = null,
        isShuffleEnabled: Boolean = false,
        shuffleSeed: Long = 0L,
        originalOrderIds: List<String> = emptyList(),
        recentTrackIds: List<String> = emptyList(),
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
            source = PersistedQueueSource.fromDomain(source),
            continuationToken = continuationToken,
            radioPlaylistId = radioPlaylistId,
            isInfinite = source.isInfinite,
            isShuffleEnabled = isShuffleEnabled,
            shuffleSeed = shuffleSeed,
            originalOrderIds = originalOrderIds,
            recentTrackIds = recentTrackIds.takeLast(20),
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
