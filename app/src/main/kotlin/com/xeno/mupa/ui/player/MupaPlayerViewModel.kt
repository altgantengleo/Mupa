package com.xeno.mupa.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.util.concurrent.ListenableFuture
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPaletteCache
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.Track
import java.io.File
import com.xeno.mupa.data.local.LocalLibraryRepository
import com.xeno.mupa.data.lyrics.LyricsRepository
import com.xeno.mupa.data.lyrics.LyricsRequest
import com.xeno.mupa.playback.MupaPlaybackService
import com.xeno.mupa.playback.QueuePersistenceStore
import com.xeno.mupa.playback.eq.EqPreset
import com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor
import com.xeno.mupa.streaming.api.StreamingError
import com.xeno.mupa.streaming.api.StreamingRadioProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * ViewModel pemutar Mupa terhubung langsung ke MupaPlaybackService via Media3 MediaController.
 * - Menangani streaming (buffer segment ditampilkan) & lokal (URI SAF persisten, skip aman jika file hilang/dicabut).
 * - Menyediakan getCurrentPositionMs() ringan untuk ticker lirik 45ms tanpa rekomposisi seluruh layar.
 */
@HiltViewModel
class MupaPlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val queueStore: QueuePersistenceStore,
    private val equalizerProcessor: FiveBandEqualizerProcessor,
    private val lyricsRepository: LyricsRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val radioProvider: StreamingRadioProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MupaPlayerUiState())
    val uiState: StateFlow<MupaPlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingCommand: ((MediaController) -> Unit)? = null
    private var positionSyncJob: Job? = null
    private var lyricsFetchJob: Job? = null
    private var udExtractJob: Job? = null
    private var radioLoadJob: Job? = null

    @Volatile
    private var isCleared: Boolean = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) {
                startPositionSync()
            } else {
                stopPositionSync()
                persistCurrentQueue()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val ctrl = mediaController
            val buffering = playbackState == Player.STATE_BUFFERING
            val realDuration = ctrl?.duration?.takeIf { it > 0L }
            val buffered = ctrl?.bufferedPosition?.coerceAtLeast(0L) ?: 0L
            _uiState.update { state ->
                state.copy(
                    isBuffering = buffering,
                    bufferedPositionMs = buffered,
                    durationMs = realDuration ?: state.durationMs,
                    errorMessage = if (playbackState == Player.STATE_READY) null else state.errorMessage,
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = mediaController ?: return
            val idx = ctrl.currentMediaItemIndex
            val queue = _uiState.value.queue
            if (idx in queue.indices) {
                val track = queue[idx]
                val realDur = ctrl.duration.takeIf { it > 0L } ?: track.durationMs.coerceAtLeast(0L)
                _uiState.update {
                    val updatedRecent = (it.recentPlayedIds.filterNot { id -> id == track.id } + track.id)
                        .takeLast(SmartQueueFilter.RECENT_HISTORY_LIMIT)
                    it.copy(
                        currentIndex = idx,
                        currentTrack = track,
                        recentPlayedIds = updatedRecent,
                        positionMs = 0L,
                        bufferedPositionMs = 0L,
                        durationMs = realDur,
                        lyrics = emptyList(),
                        lyricsIsPlain = false,
                        lyricsError = null,
                        lyricsOffsetMs = 0L,
                        errorMessage = null,
                    )
                }
                if (track.sourceType == AudioSourceType.LOCAL) {
                    viewModelScope.launch { localLibraryRepository.recordPlay(track.id) }
                }
                persistCurrentQueue()
                extractUltraDynamicForTrack(track)
                fetchLyricsForCurrentTrack()
                maybeLoadMoreRadioQueue()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            stopPositionSync()
            val current = _uiState.value.currentTrack
            if (current != null && current.sourceType == AudioSourceType.LOCAL) {
                // File lokal terhapus atau izin URI dicabut -> beri tahu & skip aman bila ada lagu lain
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        errorMessage = "File lokal \"${current.title}\" tidak dapat diakses atau telah dihapus.",
                    )
                }
                if (_uiState.value.queue.size > 1) {
                    skipNext()
                }
                return
            }
            val detail = error.cause?.message?.takeIf { it.isNotBlank() }
                ?: error.message?.takeIf { it.isNotBlank() }
                ?: "Kode ${error.errorCodeName}"
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = "Gagal memutar audio: $detail",
                )
            }
        }
    }

    init {
        connectToPlaybackService()
        restorePersistedQueue()
    }

    /** Dibaca ringan oleh ticker lirik 45ms tanpa me-recompose layar utama. */
    fun currentPlaybackPositionMs(): Long {
        val ctrl = mediaController
        return if (ctrl != null && ctrl.isPlaying) {
            ctrl.currentPosition.coerceAtLeast(0L)
        } else {
            _uiState.value.positionMs
        }
    }

    private fun connectToPlaybackService() {
        val token = SessionToken(appContext, ComponentName(appContext, MupaPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                if (isCleared) {
                    MediaController.releaseFuture(future)
                    return@addListener
                }
                runCatching { future.get() }
                    .onSuccess { controller ->
                        if (isCleared) {
                            controller.release()
                            return@onSuccess
                        }
                        mediaController = controller
                        controller.addListener(playerListener)
                        syncFromController(controller)
                        pendingCommand?.let { cmd ->
                            pendingCommand = null
                            cmd(controller)
                        }
                    }
                    .onFailure { err ->
                        _uiState.update {
                            it.copy(
                                isBuffering = false,
                                errorMessage = "Gagal terhubung ke layanan pemutar: ${err.message}",
                            )
                        }
                    }
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    private fun syncFromController(controller: MediaController) {
        val playing = controller.isPlaying
        val buffering = controller.playbackState == Player.STATE_BUFFERING
        val pos = controller.currentPosition.coerceAtLeast(0L)
        val buf = controller.bufferedPosition.coerceAtLeast(0L)
        val dur = controller.duration.takeIf { it > 0L }
        _uiState.update { state ->
            state.copy(
                isPlaying = playing,
                isBuffering = buffering,
                positionMs = pos,
                bufferedPositionMs = buf,
                durationMs = dur ?: state.durationMs,
            )
        }
        if (playing) startPositionSync()
    }

    private fun restorePersistedQueue() {
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
                }.filter { track ->
                    // Pastikan URI lokal yang dipulihkan masih terbaca
                    track.sourceType != AudioSourceType.LOCAL ||
                        !track.id.startsWith("content://") ||
                        localLibraryRepository.isUriReadable(Uri.parse(track.id))
                }
                if (restoredTracks.isEmpty()) return@launch
                val safeIndex = restored.currentIndex.coerceIn(0, restoredTracks.lastIndex)
                val current = restoredTracks[safeIndex]
                val restoredSource = restored.source.toDomain()
                val restoredOrder = if (restored.originalOrderIds.isNotEmpty()) {
                    val byId = restoredTracks.associateBy { it.id }
                    restored.originalOrderIds.mapNotNull { byId[it] }.ifEmpty { restoredTracks }
                } else {
                    restoredTracks
                }
                _uiState.update { state ->
                    if (state.queue.isNotEmpty()) return@update state
                    state.copy(
                        queue = restoredTracks,
                        currentIndex = safeIndex,
                        currentTrack = current,
                        queueSource = restoredSource,
                        continuationToken = restored.continuationToken,
                        radioPlaylistId = restored.radioPlaylistId,
                        isShuffleEnabled = restored.isShuffleEnabled,
                        shuffleSeed = restored.shuffleSeed,
                        originalQueueOrder = restoredOrder,
                        recentPlayedIds = restored.recentTrackIds,
                        positionMs = restored.positionMs.coerceIn(0L, current.durationMs.coerceAtLeast(1L)),
                        durationMs = current.durationMs.coerceAtLeast(0L),
                    )
                }
            }
        }
    }

    private fun withController(action: (MediaController) -> Unit) {
        val ctrl = mediaController
        if (ctrl != null) {
            action(ctrl)
        } else {
            pendingCommand = action
        }
    }

    private fun toMediaItems(tracks: List<Track>): List<MediaItem> =
        tracks.map { track ->
            MupaPlaybackService.buildMediaItem(
                trackId = track.id,
                title = track.title,
                artist = track.artistName,
                albumTitle = track.albumTitle,
                artworkUrl = track.artworkUrl,
                localContentUri = if (track.sourceType == AudioSourceType.LOCAL && track.id.startsWith("content://")) {
                    track.id
                } else {
                    null
                },
            )
        }

    private fun persistCurrentQueue() {
        val snapshot = _uiState.value
        if (snapshot.queue.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            queueStore.save(
                tracks = snapshot.queue,
                currentIndex = snapshot.currentIndex,
                positionMs = snapshot.positionMs,
                source = snapshot.queueSource,
                continuationToken = snapshot.continuationToken,
                radioPlaylistId = snapshot.radioPlaylistId,
                isShuffleEnabled = snapshot.isShuffleEnabled,
                shuffleSeed = snapshot.shuffleSeed,
                originalOrderIds = snapshot.originalQueueOrder.map { it.id },
                recentTrackIds = snapshot.recentPlayedIds,
            )
        }
    }

    /**
     * Memutar hasil pencarian tunggal (perbaikan bug "blue" Fase 2T):
     * - Mode Streaming: antrean = [lagu itu] + RADIO dari lagu itu (BUKAN sisa hasil pencarian).
     * - Mode Lokal: memutar daftar lokal sesuai urutan.
     */
    fun playFromSearch(track: Track) {
        if (track.sourceType == AudioSourceType.LOCAL) {
            playQueue(listOf(track), 0, QueueSource.Local("Hasil Pencarian Lokal"))
            return
        }
        val radioSource = QueueSource.Radio(
            seedTrackId = track.id,
            seedTitle = track.title,
            seedArtist = track.artistName,
        )
        playQueue(listOf(track), 0, radioSource)
        loadRadioForSeed(track, radioSource, continuation = null, playlistId = null)
    }

    fun playArtistRadio(artistId: String, artistName: String, initialTracks: List<Track> = emptyList()) {
        val source = QueueSource.Artist(artistId = artistId, artistName = artistName)
        if (initialTracks.isNotEmpty()) {
            val cleaned = SmartQueueFilter.filterAndOrderRadioCandidates(
                seed = initialTracks.first(),
                candidates = initialTracks.drop(1),
                existingQueue = listOf(initialTracks.first()),
                recentTrackIds = _uiState.value.recentPlayedIds.toSet(),
            )
            playQueue(listOf(initialTracks.first()) + cleaned, 0, source)
            maybeLoadMoreRadioQueue()
        } else {
            radioLoadJob?.cancel()
            radioLoadJob = viewModelScope.launch {
                _uiState.update { it.copy(isLoadingMoreQueue = true) }
                radioProvider.getArtistRadio(artistId, artistName).onSuccess { page ->
                    val filtered = SmartQueueFilter.filterAndOrderRadioCandidates(
                        seed = page.tracks.firstOrNull(),
                        candidates = page.tracks.drop(1),
                        recentTrackIds = _uiState.value.recentPlayedIds.toSet(),
                    )
                    val full = listOfNotNull(page.tracks.firstOrNull()) + filtered
                    if (full.isNotEmpty()) {
                        playQueue(full, 0, source)
                        _uiState.update {
                            it.copy(
                                continuationToken = page.continuationToken,
                                radioPlaylistId = page.playlistId,
                                isLoadingMoreQueue = false,
                            )
                        }
                        persistCurrentQueue()
                    } else {
                        _uiState.update { it.copy(isLoadingMoreQueue = false) }
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoadingMoreQueue = false) }
                }
            }
        }
    }

    fun playQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        source: QueueSource? = null,
    ) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val track = tracks[safeIndex]
        val resolvedSource = source ?: if (track.sourceType == AudioSourceType.LOCAL) {
            QueueSource.Local()
        } else {
            QueueSource.MixMupa()
        }

        if (track.sourceType == AudioSourceType.LOCAL && track.id.startsWith("content://")) {
            if (!localLibraryRepository.isUriReadable(Uri.parse(track.id))) {
                _uiState.update {
                    it.copy(errorMessage = "File lokal \"${track.title}\" sudah dipindah atau izinnya dicabut.")
                }
                return
            }
            viewModelScope.launch { localLibraryRepository.recordPlay(track.id) }
        }

        radioLoadJob?.cancel()
        val updatedRecent = (_uiState.value.recentPlayedIds.filterNot { it == track.id } + track.id)
            .takeLast(SmartQueueFilter.RECENT_HISTORY_LIMIT)

        _uiState.update {
            it.copy(
                queue = tracks,
                currentIndex = safeIndex,
                currentTrack = track,
                queueSource = resolvedSource,
                continuationToken = null,
                radioPlaylistId = null,
                isLoadingMoreQueue = false,
                isShuffleEnabled = false,
                shuffleSeed = 0L,
                originalQueueOrder = tracks,
                recentPlayedIds = updatedRecent,
                positionMs = 0L,
                bufferedPositionMs = 0L,
                durationMs = track.durationMs.coerceAtLeast(0L),
                isPlaying = false,
                isBuffering = track.sourceType == AudioSourceType.STREAMING,
                lyrics = emptyList(),
                lyricsIsPlain = false,
                lyricsError = null,
                lyricsOffsetMs = 0L,
                errorMessage = null,
            )
        }
        persistCurrentQueue()

        val mediaItems = toMediaItems(tracks)
        withController { ctrl ->
            ctrl.shuffleModeEnabled = false
            ctrl.setMediaItems(mediaItems, safeIndex, 0L)
            ctrl.prepare()
            ctrl.play()
        }
        // Pre-fetch lirik & ekstraksi palet Ultra Dynamic di latar belakang
        extractUltraDynamicForTrack(track)
        fetchLyricsForCurrentTrack()
        if (resolvedSource.isInfinite && tracks.size - 1 - safeIndex <= 3 && resolvedSource !is QueueSource.Radio) {
            maybeLoadMoreRadioQueue()
        }
    }

    /** Muat lanjutan radio otomatis saat sisa antrean <= 3 (untuk sumber tak berujung). */
    fun maybeLoadMoreRadioQueue() {
        val state = _uiState.value
        if (!state.queueSource.isInfinite || state.isLoadingMoreQueue) return
        val remaining = (state.queue.size - 1) - state.currentIndex
        if (remaining > 3) return
        val seedTrack = state.queue.lastOrNull() ?: state.currentTrack ?: return
        if (seedTrack.sourceType != AudioSourceType.STREAMING) return

        loadRadioForSeed(
            seedTrack = seedTrack,
            expectedSource = state.queueSource,
            continuation = state.continuationToken,
            playlistId = state.radioPlaylistId,
        )
    }

    private fun loadRadioForSeed(
        seedTrack: Track,
        expectedSource: QueueSource,
        continuation: String?,
        playlistId: String?,
    ) {
        radioLoadJob?.cancel()
        radioLoadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMoreQueue = true) }
            val result = radioProvider.getRadioNext(
                seedTrack = seedTrack,
                continuationToken = continuation,
                playlistId = playlistId,
            )
            result.onSuccess { page ->
                val current = _uiState.value
                if (current.queueSource != expectedSource) return@onSuccess
                val filtered = SmartQueueFilter.filterAndOrderRadioCandidates(
                    seed = current.currentTrack ?: seedTrack,
                    candidates = page.tracks,
                    existingQueue = current.queue,
                    recentTrackIds = current.recentPlayedIds.toSet(),
                )
                if (filtered.isNotEmpty()) {
                    val newQueue = current.queue + filtered
                    val newOriginal = current.originalQueueOrder + filtered
                    _uiState.update {
                        it.copy(
                            queue = newQueue,
                            originalQueueOrder = newOriginal,
                            continuationToken = page.continuationToken,
                            radioPlaylistId = page.playlistId ?: it.radioPlaylistId,
                            isLoadingMoreQueue = false,
                        )
                    }
                    withController { ctrl ->
                        ctrl.addMediaItems(toMediaItems(filtered))
                    }
                    persistCurrentQueue()
                } else {
                    _uiState.update {
                        it.copy(
                            continuationToken = page.continuationToken,
                            radioPlaylistId = page.playlistId ?: it.radioPlaylistId,
                            isLoadingMoreQueue = false,
                        )
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isLoadingMoreQueue = false) }
            }
        }
    }

    fun togglePlayPause() {
        val state = _uiState.value
        val current = state.currentTrack ?: return
        withController { ctrl ->
            if (ctrl.mediaItemCount == 0 && state.queue.isNotEmpty()) {
                _uiState.update {
                    it.copy(isBuffering = current.sourceType == AudioSourceType.STREAMING, errorMessage = null)
                }
                ctrl.setMediaItems(toMediaItems(state.queue), state.currentIndex, state.positionMs)
                ctrl.prepare()
                ctrl.play()
            } else if (ctrl.isPlaying) {
                ctrl.pause()
            } else {
                if (ctrl.playbackState == Player.STATE_IDLE || ctrl.playbackState == Player.STATE_ENDED) {
                    _uiState.update { it.copy(isBuffering = current.sourceType == AudioSourceType.STREAMING, errorMessage = null) }
                    ctrl.prepare()
                }
                ctrl.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val dur = _uiState.value.durationMs.coerceAtLeast(1L)
        val clamped = positionMs.coerceIn(0L, dur)
        _uiState.update { it.copy(positionMs = clamped) }
        withController { it.seekTo(clamped) }
    }

    fun skipNext() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        withController { ctrl ->
            if (ctrl.hasNextMediaItem()) {
                ctrl.seekToNextMediaItem()
                ctrl.play()
            } else if (state.queue.isNotEmpty()) {
                selectQueueIndex(0, autoPlay = true)
            }
        }
    }

    fun skipPrevious() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        if (state.positionMs > 3_500L) {
            seekTo(0L)
            return
        }
        withController { ctrl ->
            if (ctrl.hasPreviousMediaItem()) {
                ctrl.seekToPreviousMediaItem()
                ctrl.play()
            } else {
                selectQueueIndex(state.queue.lastIndex, autoPlay = true)
            }
        }
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
                bufferedPositionMs = 0L,
                durationMs = track.durationMs.coerceAtLeast(0L),
                isBuffering = autoPlay && track.sourceType == AudioSourceType.STREAMING,
                lyrics = emptyList(),
                lyricsIsPlain = false,
                lyricsError = null,
                lyricsOffsetMs = 0L,
                errorMessage = null,
            )
        }
        withController { ctrl ->
            if (ctrl.mediaItemCount != state.queue.size) {
                ctrl.setMediaItems(toMediaItems(state.queue), index, 0L)
                ctrl.prepare()
            } else {
                ctrl.seekTo(index, 0L)
            }
            if (autoPlay) ctrl.play() else ctrl.pause()
        }
        persistCurrentQueue()
        extractUltraDynamicForTrack(track)
    }

    fun toggleShuffle() {
        val next = !_uiState.value.isShuffleEnabled
        _uiState.update { it.copy(isShuffleEnabled = next) }
        withController { it.shuffleModeEnabled = next }
    }

    fun cycleRepeatMode() {
        val next = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = next) }
        withController { ctrl ->
            ctrl.repeatMode = when (next) {
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            }
        }
    }

    fun toggleFavoriteCurrent() {
        val current = _uiState.value.currentTrack ?: return
        if (current.sourceType == AudioSourceType.LOCAL) {
            viewModelScope.launch { localLibraryRepository.toggleFavorite(current.id) }
        }
        _uiState.update { state ->
            val cur = state.currentTrack ?: return@update state
            val updated = cur.copy(isFavorite = !cur.isFavorite)
            val updatedQueue = state.queue.map { if (it.id == cur.id) updated else it }
            state.copy(currentTrack = updated, queue = updatedQueue)
        }
    }

    fun openOverlay(pane: PlayerOverlayPane) {
        val target = if (_uiState.value.activeOverlay == pane) PlayerOverlayPane.NONE else pane
        _uiState.update { it.copy(activeOverlay = target) }
        if (target == PlayerOverlayPane.LYRICS && _uiState.value.lyrics.isEmpty() && !_uiState.value.lyricsLoading) {
            fetchLyricsForCurrentTrack(forceRefresh = true)
        }
    }

    fun closeOverlay() {
        _uiState.update { it.copy(activeOverlay = PlayerOverlayPane.NONE) }
    }

    fun openCinemaMode() {
        _uiState.update { it.copy(isCinemaMode = true) }
        if (_uiState.value.lyrics.isEmpty() && !_uiState.value.lyricsLoading) {
            fetchLyricsForCurrentTrack()
        }
    }

    fun closeCinemaMode() {
        _uiState.update { it.copy(isCinemaMode = false) }
    }

    fun adjustLyricsOffset(deltaMs: Long) {
        _uiState.update {
            it.copy(lyricsOffsetMs = (it.lyricsOffsetMs + deltaMs).coerceIn(-15_000L, 15_000L))
        }
    }

    fun resetLyricsOffset() {
        _uiState.update { it.copy(lyricsOffsetMs = 0L) }
    }

    fun selectEqPreset(preset: EqPreset) {
        equalizerProcessor.setBandGains(preset.gainsDb)
        _uiState.update { it.copy(eqPreset = preset, eqGainsDb = preset.gainsDb) }
    }

    fun updateEqBand(bandIndex: Int, gainDb: Float) {
        val current = _uiState.value.eqGainsDb.toMutableList()
        if (bandIndex !in current.indices) return
        current[bandIndex] = gainDb.coerceIn(
            FiveBandEqualizerProcessor.MIN_GAIN_DB,
            FiveBandEqualizerProcessor.MAX_GAIN_DB,
        )
        equalizerProcessor.setBandGains(current)
        _uiState.update { it.copy(eqPreset = EqPreset.CUSTOM, eqGainsDb = current) }
    }

    fun resetEq() {
        selectEqPreset(EqPreset.FLAT)
    }

    fun fetchLyricsForCurrentTrack(forceRefresh: Boolean = false) {
        val track = _uiState.value.currentTrack ?: return
        lyricsFetchJob?.cancel()
        lyricsFetchJob = viewModelScope.launch {
            _uiState.update { it.copy(lyricsLoading = true, lyricsError = null) }
            val request = LyricsRequest(
                trackId = track.id,
                title = track.title,
                artistName = track.artistName,
                albumTitle = track.albumTitle,
                durationMs = _uiState.value.durationMs.takeIf { it > 0L } ?: track.durationMs,
                sourceType = track.sourceType,
            )
            lyricsRepository.getLyrics(request, forceRefresh = forceRefresh)
                .onSuccess { doc ->
                    if (_uiState.value.currentTrack?.id == track.id) {
                        _uiState.update {
                            it.copy(
                                lyrics = doc.lines,
                                lyricsIsPlain = !doc.isSynced,
                                lyricsProviderLabel = doc.providerLabel,
                                lyricsLoading = false,
                                lyricsError = null,
                            )
                        }
                    }
                }
                .onFailure { err ->
                    if (_uiState.value.currentTrack?.id == track.id) {
                        if (err is StreamingError.Unavailable) {
                            // Tidak ditemukan -> tampilkan status kosong rapi (bukan error jaringan)
                            _uiState.update {
                                it.copy(
                                    lyrics = emptyList(),
                                    lyricsIsPlain = false,
                                    lyricsProviderLabel = null,
                                    lyricsLoading = false,
                                    lyricsError = null,
                                )
                            }
                        } else {
                            _uiState.update {
                                it.copy(
                                    lyricsLoading = false,
                                    lyricsError = err.message ?: "Gagal memuat lirik.",
                                )
                            }
                        }
                    }
                }
        }
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
        withController { ctrl ->
            if (fromIndex in 0 until ctrl.mediaItemCount && toIndex in 0 until ctrl.mediaItemCount) {
                ctrl.moveMediaItem(fromIndex, toIndex)
            }
        }
        persistCurrentQueue()
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
                durationMs = nextTrack.durationMs.coerceAtLeast(0L),
            )
        }
        withController { ctrl ->
            if (index in 0 until ctrl.mediaItemCount && ctrl.mediaItemCount > 1) {
                ctrl.removeMediaItem(index)
            }
        }
        persistCurrentQueue()
    }

    fun clearUpcomingQueue() {
        val state = _uiState.value
        val current = state.currentTrack ?: return
        val curIdx = state.currentIndex
        _uiState.update {
            it.copy(queue = listOf(current), currentIndex = 0)
        }
        withController { ctrl ->
            if (ctrl.mediaItemCount > 1) {
                for (i in ctrl.mediaItemCount - 1 downTo 0) {
                    if (i != curIdx) ctrl.removeMediaItem(i)
                }
            }
        }
        persistCurrentQueue()
    }

    private fun startPositionSync() {
        if (positionSyncJob?.isActive == true) return
        positionSyncJob = viewModelScope.launch {
            while (true) {
                val ctrl = mediaController
                if (ctrl == null || !ctrl.isPlaying) break
                val pos = ctrl.currentPosition.coerceAtLeast(0L)
                val buf = ctrl.bufferedPosition.coerceAtLeast(0L)
                val dur = ctrl.duration.takeIf { it > 0L }
                _uiState.update { state ->
                    state.copy(
                        positionMs = pos,
                        bufferedPositionMs = buf,
                        durationMs = dur ?: state.durationMs,
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionSync() {
        positionSyncJob?.cancel()
        positionSyncJob = null
    }

    private fun extractUltraDynamicForTrack(track: Track) {
        val art = track.artworkUrl
        if (art.isNullOrBlank()) {
            _uiState.update { it.copy(ultraDynamicPalette = UltraDynamicPalette.BrandNoCover) }
            return
        }
        UltraDynamicPaletteCache.get(track.id)?.let { cached ->
            _uiState.update { it.copy(ultraDynamicPalette = cached) }
            return
        }
        udExtractJob?.cancel()
        udExtractJob = viewModelScope.launch(Dispatchers.IO) {
            val model: Any = if (art.startsWith("/")) File(art) else art
            val loader = SingletonImageLoader.get(appContext)
            // Prefetch resolusi penuh (768px) dengan memoryCacheKey bersama sebelum animasi cover (anti-kedip S2)
            runCatching {
                loader.enqueue(
                    ImageRequest.Builder(appContext)
                        .data(model)
                        .size(768)
                        .memoryCacheKey("mupa_cover_${track.id}")
                        .build(),
                )
            }
            val bmp = runCatching {
                val req = ImageRequest.Builder(appContext)
                    .data(model)
                    .size(128)
                    .allowHardware(false)
                    .build()
                loader.execute(req).image?.toBitmap()
            }.getOrNull()
            val palette = UltraDynamicPaletteCache.extractOrGet(track.id, bmp)
            if (_uiState.value.currentTrack?.id == track.id) {
                _uiState.update { it.copy(ultraDynamicPalette = palette) }
            }
        }
    }

    override fun onCleared() {
        isCleared = true
        pendingCommand = null
        stopPositionSync()
        lyricsFetchJob?.cancel()
        udExtractJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null
        super.onCleared()
    }
}
