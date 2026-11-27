package com.xeno.mupa.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Konfigurasi live durasi Crossfade (0 = Mati, 1..12 detik) yang dibaca langsung
 * oleh MupaCrossfadeEngine tanpa perlu restart service.
 */
object MupaCrossfadeConfig {
    const val MIN_SECONDS = 0
    const val MAX_SECONDS = 12
    const val DEFAULT_SECONDS = 5

    private val durationSecondsAtomic = AtomicInteger(DEFAULT_SECONDS)

    fun getDurationSeconds(): Int = durationSecondsAtomic.get()

    fun setDurationSeconds(seconds: Int) {
        durationSecondsAtomic.set(seconds.coerceIn(MIN_SECONDS, MAX_SECONDS))
    }

    /**
     * Kurva equal-power psikoakustik (smootherstep orde-5 + cos/sin^1.25) ala Spotify:
     * - Turunan pertama & kedua bernilai 0 di awal/akhir -> awal masuk & akhir pudar tak terasa.
     * - Eksponen 1.25 menyesuaikan skala amplitudo linear Android AudioTrack ke persepsi dB telinga.
     */
    fun computeEqualPowerGains(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        // Smootherstep orde-5: p^3 * (p * (6p - 15) + 10)
        val s = p * p * p * (p * (p * 6f - 15f) + 10f)
        val angle = s * (PI / 2.0)
        val outGain = cos(angle).coerceAtLeast(0.0).pow(1.25).toFloat().coerceIn(0f, 1f)
        val inGain = sin(angle).coerceAtLeast(0.0).pow(1.25).toFloat().coerceIn(0f, 1f)
        return outGain to inGain
    }
}

/**
 * Mesin Crossfade Dual-Deck Ping-Pong Sejati ala Spotify untuk Media3 ExoPlayer:
 * - Dua dek ExoPlayer identik bergantian menjadi activePlayer di MediaLibrarySession (tanpa seek ulang!).
 * - Lagu berikutnya dipra-muat 8 detik sebelum jendela crossfade di dek standby.
 * - Overlap nyata pada 100 Hz (10 ms) memakai kurva psikoakustik smootherstep + equal-power.
 * - Di akhir fade, antrean disisipkan di sekitar lagu aktif pada dek masuk tanpa memutus audio sedetik pun,
 *   lalu MediaLibrarySession.setPlayer(dekMasuk) dipanggil -> nol stutter, nol echo, nol lonjakan volume.
 */
@OptIn(UnstableApi::class)
class MupaCrossfadeEngine(
    private val context: Context,
    initialActivePlayer: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    private val audioProcessorsFactory: () -> List<AudioProcessor>,
    private val onReleaseProcessors: (List<AudioProcessor>) -> Unit = {},
    private val onActivePlayerSwapped: (ExoPlayer) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private val musicAudioAttributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private var activePlayer: ExoPlayer = initialActivePlayer
    private var standbyPlayer: ExoPlayer? = null
    private val createdExtraProcessors = mutableListOf<List<AudioProcessor>>()

    private var stagedMediaId: String? = null
    private var stagedTargetIndex: Int = C.INDEX_UNSET
    private var isStandbyReady: Boolean = false

    private var isCrossfading: Boolean = false
    private var crossfadeStartElapsedMs: Long = 0L
    private var activeCrossfadeDurationMs: Long = 0L
    private var handoffTargetIndex: Int = C.INDEX_UNSET
    private var isPerformingSwap: Boolean = false

    private val activeListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && isCrossfading && !isPerformingSwap) {
                cancelCrossfade(resetActiveVolume = true)
            }
            schedulePollIfNeeded()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isPerformingSwap) {
                cancelCrossfade(resetActiveVolume = true)
                clearStandbyDeck()
            }
            schedulePollIfNeeded()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!isPerformingSwap && reason == Player.DISCONTINUITY_REASON_SEEK) {
                cancelCrossfade(resetActiveVolume = true)
                clearStandbyDeck()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (!isPerformingSwap && (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE)) {
                cancelCrossfade(resetActiveVolume = true)
            }
            schedulePollIfNeeded()
        }
    }

    private val standbyListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isStandbyReady = playbackState == Player.STATE_READY
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            cancelCrossfade(resetActiveVolume = true)
            clearStandbyDeck()
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            tick()
            val delayMs = if (isCrossfading) FADE_STEP_MS else MONITOR_INTERVAL_MS
            if (activePlayer.isPlaying || isCrossfading) {
                handler.postDelayed(this, delayMs)
            }
        }
    }

    fun attach() {
        activePlayer.addListener(activeListener)
        schedulePollIfNeeded()
    }

    fun currentActivePlayer(): ExoPlayer = activePlayer

    fun release() {
        handler.removeCallbacksAndMessages(null)
        runCatching { activePlayer.removeListener(activeListener) }
        runCatching {
            standbyPlayer?.removeListener(standbyListener)
            standbyPlayer?.release()
        }
        standbyPlayer = null
        runCatching { activePlayer.release() }
        createdExtraProcessors.forEach { list ->
            runCatching { onReleaseProcessors(list) }
        }
        createdExtraProcessors.clear()
    }

    private fun schedulePollIfNeeded() {
        handler.removeCallbacks(pollRunnable)
        if (activePlayer.isPlaying || isCrossfading) {
            handler.postDelayed(pollRunnable, if (isCrossfading) FADE_STEP_MS else MONITOR_INTERVAL_MS)
        }
    }

    private fun ensureStandbyPlayer(): ExoPlayer {
        standbyPlayer?.let { return it }
        // Instance AudioProcessor terpisah per dek agar ByteBuffer EQ tidak pernah bentrok
        val freshProcessors = audioProcessorsFactory()
        createdExtraProcessors.add(freshProcessors)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 45_000, 650, 1_400)
            .setTargetBufferBytes(10 * 1024 * 1024)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                ctx: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(ctx)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(freshProcessors.toTypedArray())
                .build()
        }.setEnableDecoderFallback(true)

        return ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { created ->
                created.volume = 0f
                created.addListener(standbyListener)
                standbyPlayer = created
            }
    }

    private fun tick() {
        val configuredSec = MupaCrossfadeConfig.getDurationSeconds()
        if (configuredSec <= 0) {
            if (isCrossfading) cancelCrossfade(resetActiveVolume = true)
            return
        }

        if (isCrossfading) {
            stepActiveCrossfade()
            return
        }

        if (!activePlayer.isPlaying) return
        if (activePlayer.repeatMode == Player.REPEAT_MODE_ONE) return

        val nextIndex = activePlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == activePlayer.currentMediaItemIndex) return

        val durationMs = activePlayer.duration
        if (durationMs <= 0L || durationMs == C.TIME_UNSET) return

        val positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        val remainingMs = durationMs - positionMs
        if (remainingMs <= 0L) return

        val requestedFadeMs = configuredSec * 1_000L
        // Jangan pernah memakan lebih dari 30% durasi lagu pendek
        val effectiveFadeMs = requestedFadeMs.coerceAtMost((durationMs * 3L / 10L).coerceAtLeast(1_500L))
        val preStageWindowMs = effectiveFadeMs + PRE_STAGE_LEAD_MS

        val nextItem = activePlayer.getMediaItemAt(nextIndex)

        // 1. Pra-muat dek standby 8 detik sebelum jendela crossfade agar buffer 100% siap
        if (remainingMs <= preStageWindowMs && stagedMediaId != nextItem.mediaId) {
            stageNextOnStandby(nextItem, nextIndex)
        }

        // 2. Mulai overlap equal-power begitu masuk jendela crossfade dan dek standby siap
        if (remainingMs <= effectiveFadeMs && remainingMs >= MIN_TRIGGER_REMAINING_MS) {
            if (stagedMediaId == nextItem.mediaId && isStandbyReady) {
                startCrossfade(nextIndex, remainingMs.coerceAtMost(effectiveFadeMs))
            }
        }
    }

    private fun stageNextOnStandby(item: MediaItem, index: Int) {
        val standby = ensureStandbyPlayer()
        stagedMediaId = item.mediaId
        stagedTargetIndex = index
        isStandbyReady = false
        standby.stop()
        standby.clearMediaItems()
        standby.volume = 0f
        standby.setMediaItem(item)
        standby.playWhenReady = false
        standby.prepare()
    }

    private fun startCrossfade(targetIndex: Int, fadeDurationMs: Long) {
        val standby = standbyPlayer ?: return
        isCrossfading = true
        handoffTargetIndex = targetIndex
        activeCrossfadeDurationMs = fadeDurationMs.coerceAtLeast(800L)
        crossfadeStartElapsedMs = SystemClock.elapsedRealtime()
        standby.volume = 0f
        standby.seekTo(0L)
        standby.play()
        // Segera beralih ke tick 10ms (~100fps) agar kurva gain ultra-halus
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, FADE_STEP_MS)
    }

    private fun stepActiveCrossfade() {
        val standby = standbyPlayer
        if (standby == null || !activePlayer.isPlaying) {
            cancelCrossfade(resetActiveVolume = true)
            return
        }

        val elapsed = (SystemClock.elapsedRealtime() - crossfadeStartElapsedMs).coerceAtLeast(0L)
        val durationMs = activePlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val activeRemaining = (durationMs - activePlayer.currentPosition).coerceAtLeast(0L)

        val progress = (elapsed.toFloat() / activeCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
        val (outGain, inGain) = MupaCrossfadeConfig.computeEqualPowerGains(progress)
        activePlayer.volume = outGain
        standby.volume = inGain

        // Tukar dek secara mulus tanpa seek ulang saat fade tuntas atau 60ms sebelum lagu lama habis
        if (progress >= 1f || activeRemaining <= SWAP_LEAD_MS) {
            performZeroSeekDeckSwap(standby)
        }
    }

    private fun performZeroSeekDeckSwap(incomingDeck: ExoPlayer) {
        val outgoingDeck = activePlayer
        val targetIdx = handoffTargetIndex
        val totalItems = outgoingDeck.mediaItemCount
        if (targetIdx == C.INDEX_UNSET || targetIdx !in 0 until totalItems) {
            cancelCrossfade(resetActiveVolume = true)
            return
        }

        isPerformingSwap = true
        isCrossfading = false

        // Pastikan volume akhir bersih sebelum swap (outgoing senyap total, incoming penuh)
        outgoingDeck.volume = 0f
        incomingDeck.volume = 1f

        // Sisipkan item sebelum & sesudah targetIdx ke incomingDeck TANPA memutus lagu yang sedang diputar!
        val beforeItems = if (targetIdx > 0) {
            List(targetIdx) { idx -> outgoingDeck.getMediaItemAt(idx) }
        } else {
            emptyList()
        }
        val afterItems = if (targetIdx + 1 < totalItems) {
            List(totalItems - (targetIdx + 1)) { offset ->
                outgoingDeck.getMediaItemAt(targetIdx + 1 + offset)
            }
        } else {
            emptyList()
        }

        incomingDeck.repeatMode = outgoingDeck.repeatMode
        incomingDeck.shuffleModeEnabled = outgoingDeck.shuffleModeEnabled
        if (beforeItems.isNotEmpty()) {
            incomingDeck.addMediaItems(0, beforeItems)
        }
        if (afterItems.isNotEmpty()) {
            incomingDeck.addMediaItems(afterItems)
        }

        // Tukar listener & peran dek
        outgoingDeck.removeListener(activeListener)
        incomingDeck.removeListener(standbyListener)

        // Lepas audio focus dari dek lama (sudah 0f) lalu serahkan ke dek aktif baru
        outgoingDeck.setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ false)
        outgoingDeck.pause()
        outgoingDeck.stop()
        outgoingDeck.clearMediaItems()

        incomingDeck.setAudioAttributes(musicAudioAttributes, /* handleAudioFocus = */ true)

        activePlayer = incomingDeck
        standbyPlayer = outgoingDeck

        activePlayer.addListener(activeListener)
        standbyPlayer?.addListener(standbyListener)

        // Reset staging untuk lagu berikutnya
        stagedMediaId = null
        stagedTargetIndex = C.INDEX_UNSET
        isStandbyReady = false
        handoffTargetIndex = C.INDEX_UNSET

        // Pindahkan MediaLibrarySession ke dek aktif baru (MediaController tersinkron tanpa memutus audio!)
        onActivePlayerSwapped(activePlayer)

        isPerformingSwap = false
        schedulePollIfNeeded()
    }

    private fun cancelCrossfade(resetActiveVolume: Boolean) {
        isCrossfading = false
        handoffTargetIndex = C.INDEX_UNSET
        if (resetActiveVolume) {
            activePlayer.volume = 1f
        }
        standbyPlayer?.let { standby ->
            runCatching {
                standby.volume = 0f
                standby.pause()
            }
        }
    }

    private fun clearStandbyDeck() {
        stagedMediaId = null
        stagedTargetIndex = C.INDEX_UNSET
        isStandbyReady = false
        standbyPlayer?.let { standby ->
            runCatching {
                standby.stop()
                standby.clearMediaItems()
            }
        }
    }

    companion object {
        private const val MONITOR_INTERVAL_MS = 200L
        private const val FADE_STEP_MS = 10L
        private const val PRE_STAGE_LEAD_MS = 8_000L
        private const val MIN_TRIGGER_REMAINING_MS = 400L
        private const val SWAP_LEAD_MS = 60L
    }
}
