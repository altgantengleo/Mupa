package com.xeno.mupa.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.playback.QueuePersistenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel player terhubung dengan QueuePersistenceStore (Tahap 2).
 */
@HiltViewModel
class MupaPlayerViewModel @Inject constructor(
    private val queueStore: QueuePersistenceStore,
) : ViewModel() {

    private val sampleTracks: List<Track> = listOf(
        Track(
            id = "demo-1",
            title = "Senja di Kota Tua",
            artistName = "Hindia",
            albumTitle = "Lagipula Hidup Akan Berakhir",
            durationMs = 214_000L,
            accentColorArgb = 0xFFFF5A3C,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 14,
        ),
        Track(
            id = "demo-2",
            title = "Lampu Lalu Lintas",
            artistName = "Nadin Amizah",
            albumTitle = "Untuk Dunia, Cinta, dan Kotornya",
            durationMs = 238_000L,
            accentColorArgb = 0xFF4ECDC4,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Nadin Amizah - Lampu Lalu Lintas (Official Audio).flac",
            isFavorite = false,
            playCount = 7,
        ),
        Track(
            id = "demo-3",
            title = "Malam Tanpa Bintang",
            artistName = "Tulus",
            albumTitle = "Manusia",
            durationMs = 196_000L,
            accentColorArgb = 0xFFE07A5F,
            sourceType = AudioSourceType.STREAMING,
            isFavorite = true,
            playCount = 21,
        ),
        Track(
            id = "demo-4",
            title = "Ruang Hening",
            artistName = "Reality Club",
            albumTitle = "Reality Club Presents...",
            durationMs = 225_000L,
            accentColorArgb = 0xFF7AA2F7,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "Reality Club - Ruang Hening.mp3",
            isFavorite = false,
            playCount = 0,
        ),
    )

    private val sampleLyricsByTrack: Map<String, List<LyricsLine>> = mapOf(
        "demo-1" to listOf(
            LyricsLine(0L, 4_200L, "Langit jingga mulai turun di ujung jalan"),
            LyricsLine(4_200L, 8_600L, "Langkah pelan menyusuri sisa kenangan"),
            LyricsLine(8_600L, 13_400L, "Lampu kota menyala satu per satu"),
            LyricsLine(13_400L, 18_200L, "Menemani nada yang berputar di kepala"),
            LyricsLine(18_200L, 23_000L, "Tak perlu tergesa mengejar esok hari"),
            LyricsLine(23_000L, 28_400L, "Biarkan irama tenang menuntun malam ini"),
            LyricsLine(28_400L, 34_000L, "Di antara riuh yang perlahan mereda"),
            LyricsLine(34_000L, 40_000L, "Kita temukan ruang untuk bernapas lagi"),
        ),
        "demo-2" to listOf(
            LyricsLine(0L, 4_800L, "Merah menyala menahan laju waktu"),
            LyricsLine(4_800L, 9_500L, "Kuning berkedip di sudut pandangmu"),
            LyricsLine(9_500L, 14_800L, "Hijau terbuka membawa langkah baru"),
            LyricsLine(14_800L, 20_400L, "Semua cerita tersimpan di saku rindu"),
        ),
    )

    private val _uiState = MutableStateFlow(
        MupaPlayerUiState(
            currentTrack = sampleTracks.first(),
            queue = sampleTracks,
            currentIndex = 0,
            isPlaying = false,
            positionMs = 6_400L,
            durationMs = sampleTracks.first().durationMs,
            lyrics = sampleLyricsByTrack["demo-1"].orEmpty(),
        ),
    )
    val uiState: StateFlow<MupaPlayerUiState> = _uiState.asStateFlow()

    private var tickerJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val restored = queueStore.load() ?: return@launch
            if (restored.items.isNotEmpty()) {
                val restoredTracks = restored.items.map { item ->
                    Track(
                        id = item.id,
                        title = item.title,
                        artistName = item.artistName,
                        albumTitle = item.albumTitle,
                        durationMs = item.durationMs,
                        artworkUrl = item.artworkUrl,
                        sourceType = runCatching { AudioSourceType.valueOf(item.sourceType) }
                            .getOrDefault(AudioSourceType.STREAMING),
                    )
                }
                val safeIndex = restored.currentIndex.coerceIn(0, restoredTracks.lastIndex)
                val current = restoredTracks[safeIndex]
                _uiState.update {
                    it.copy(
                        queue = restoredTracks,
                        currentIndex = safeIndex,
                        currentTrack = current,
                        positionMs = restored.positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(1L)),
                        durationMs = current.durationMs.coerceAtLeast(1_000L),
                        lyrics = sampleLyricsByTrack[current.id] ?: defaultLyricsFor(current),
                    )
                }
            }
        }
    }

    private fun persistCurrentQueue() {
        val snapshot = _uiState.value
        if (snapshot.queue.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            queueStore.save(
                tracks = snapshot.queue,
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
            )
        }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[safeIndex]
        _uiState.update {
            it.copy(
                queue = tracks,
                currentIndex = safeIndex,
                currentTrack = track,
                positionMs = 0L,
                durationMs = track.durationMs.coerceAtLeast(1_000L),
                isPlaying = true,
                lyrics = sampleLyricsByTrack[track.id] ?: defaultLyricsFor(track),
                lyricsOffsetMs = 0L,
                errorMessage = null,
            )
        }
        persistCurrentQueue()
        startTicker()
    }

    fun togglePlayPause() {
        val nextPlaying = !_uiState.value.isPlaying
        _uiState.update { it.copy(isPlaying = nextPlaying) }
        if (nextPlaying) startTicker() else stopTicker()
    }

    fun seekTo(positionMs: Long) {
        val dur = _uiState.value.durationMs
        _uiState.update { it.copy(positionMs = positionMs.coerceIn(0L, dur)) }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIndex = when {
            state.repeatMode == RepeatMode.ONE -> state.currentIndex
            state.isShuffleEnabled && state.queue.size > 1 -> {
                (state.queue.indices - state.currentIndex).random()
            }
            state.currentIndex < state.queue.lastIndex -> state.currentIndex + 1
            state.repeatMode == RepeatMode.ALL -> 0
            else -> 0
        }
        selectQueueIndex(nextIndex, autoPlay = state.isPlaying)
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        if (state.positionMs > 3_500L) {
            seekTo(0L)
            return
        }
        val prevIndex = if (state.currentIndex > 0) state.currentIndex - 1 else state.queue.lastIndex
        selectQueueIndex(prevIndex, autoPlay = state.isPlaying)
    }

    fun selectQueueIndex(index: Int, autoPlay: Boolean = true) {
        val state = _uiState.value
        if (index !in state.queue.indices) return
        val track = state.queue[index]
        _uiState.update {
            it.copy(
                currentIndex = index,
                currentTrack = track,
                positionMs = 0L,
                durationMs = track.durationMs.coerceAtLeast(1_000L),
                isPlaying = autoPlay,
                lyrics = sampleLyricsByTrack[track.id] ?: defaultLyricsFor(track),
                lyricsOffsetMs = 0L,
            )
        }
        if (autoPlay) startTicker() else stopTicker()
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }
    }

    fun cycleRepeatMode() {
        _uiState.update { state ->
            val next = when (state.repeatMode) {
                RepeatMode.OFF -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.OFF
            }
            state.copy(repeatMode = next)
        }
    }

    fun toggleFavoriteCurrent() {
        _uiState.update { state ->
            val current = state.currentTrack ?: return@update state
            val updated = current.copy(isFavorite = !current.isFavorite)
            val updatedQueue = state.queue.map { if (it.id == current.id) updated else it }
            state.copy(currentTrack = updated, queue = updatedQueue)
        }
    }

    fun openOverlay(pane: PlayerOverlayPane) {
        _uiState.update {
            val target = if (it.activeOverlay == pane) PlayerOverlayPane.NONE else pane
            it.copy(activeOverlay = target)
        }
    }

    fun closeOverlay() {
        _uiState.update { it.copy(activeOverlay = PlayerOverlayPane.NONE) }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _uiState.update {
            it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L))
        }
    }

    fun resetLyricsOffset() {
        _uiState.update { it.copy(lyricsOffsetMs = 0L) }
    }

    fun cycleSleepTimer() {
        _uiState.update { state ->
            val next = when (state.sleepTimerMinutes) {
                null -> 15
                15 -> 30
                30 -> 60
                else -> null
            }
            state.copy(sleepTimerMinutes = next)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.queue.indices || toIndex !in state.queue.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.queue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            val currentId = state.currentTrack?.id
            val newCurrentIndex = mutable.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
            state.copy(queue = mutable, currentIndex = newCurrentIndex)
        }
    }

    fun removeQueueItem(index: Int) {
        _uiState.update { state ->
            if (index !in state.queue.indices || state.queue.size <= 1) return@update state
            val mutable = state.queue.toMutableList()
            mutable.removeAt(index)
            val newIndex = when {
                index < state.currentIndex -> state.currentIndex - 1
                index == state.currentIndex -> index.coerceAtMost(mutable.lastIndex)
                else -> state.currentIndex
            }
            val nextTrack = mutable[newIndex]
            state.copy(
                queue = mutable,
                currentIndex = newIndex,
                currentTrack = nextTrack,
                durationMs = nextTrack.durationMs.coerceAtLeast(1_000L),
            )
        }
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(100L)
                val state = _uiState.value
                if (!state.isPlaying) break
                val nextPos = state.positionMs + 100L
                if (nextPos >= state.durationMs && state.durationMs > 0L) {
                    skipNext()
                } else {
                    _uiState.update { it.copy(positionMs = nextPos) }
                }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun defaultLyricsFor(track: Track): List<LyricsLine> = listOf(
        LyricsLine(0L, 4_500L, track.title),
        LyricsLine(4_500L, 9_500L, "Dibawakan oleh ${track.artistName}"),
        LyricsLine(9_500L, 15_000L, "Pratinjau lirik tersinkron dengan wipe warna adaptif"),
        LyricsLine(15_000L, 21_000L, "Ketuk baris mana saja untuk melompat ke waktu tersebut"),
    )

    override fun onCleared() {
        stopTicker()
        super.onCleared()
    }
}
