package com.xeno.mupa.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
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
     * Kurva equal-power (cosine/sine) dengan S-curve smoothstep ala Spotify:
     * menjaga daya persepsi konstan (out^2 + in^2 = 1) tanpa lembah volume atau puncak keras.
     */
    fun computeEqualPowerGains(progress: Float): Pair<Float, Float> {
        val p = progress.coerceIn(0f, 1f)
        // Smoothstep S-curve agar awal dan akhir transisi terasa sangat organik
        val s = p * p * (3f - 2f * p)
        val angle = s * (PI / 2.0)
        val outGain = cos(angle).toFloat().coerceIn(0f, 1f)
        val inGain = sin(angle).toFloat().coerceIn(0f, 1f)
        return outGain to inGain
    }
}

/**
 * Mesin Crossfade Dual-Deck Equal-Power ala Spotify untuk Media3 ExoPlayer:
 * - Memakai dek sekunder (tanpa merebut audio focus) yang berbagi cache & ResolvingDataSource yang sama.
 * - Mempra-muat lagu berikutnya ~5 detik sebelum jendela crossfade sehingga masuk tanpa jeda buffering.
 * - Mencampur (overlap nyata) lagu keluar & lagu masuk pada ~60 fps (16 ms) memakai kurva equal-power cos/sin.
 * - Menyerahkan kendali mulus kembali ke primaryPlayer (MediaSession) tepat di akhir fade dengan micro-ramp 40 ms.
 */
@OptIn(UnstableApi::class)
class MupaCrossfadeEngine(
    private val context: Context,
    private val primaryPlayer: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    private val audioProcessorsFactory: () -> List<AudioProcessor>,
    private val onReleaseProcessors: (List<AudioProcessor>) -> Unit = {},
) {
    private val handler = Handler(Looper.getMainLooper())
    private var deckPlayer: ExoPlayer? = null
    private var deckProcessors: List<AudioProcessor> = emptyList()

    private var stagedMediaId: String? = null
    private var stagedTargetIndex: Int = C.INDEX_UNSET
    private var isDeckReady: Boolean = false

    private var isCrossfading: Boolean = false
    private var crossfadeStartWallMs: Long = 0L
    private var activeCrossfadeDurationMs: Long = 0L
    private var handoffTargetIndex: Int = C.INDEX_UNSET
    private var isPerformingHandoff: Boolean = false

    private val primaryListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying && isCrossfading && !isPerformingHandoff) {
                cancelCrossfade(resetPrimaryVolume = true)
            }
            schedulePollIfNeeded()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (!isPerformingHandoff) {
                cancelCrossfade(resetPrimaryVolume = true)
                clearStagedDeck()
            }
            schedulePollIfNeeded()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (!isPerformingHandoff && reason == Player.DISCONTINUITY_REASON_SEEK) {
                cancelCrossfade(resetPrimaryVolume = true)
                clearStagedDeck()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                cancelCrossfade(resetPrimaryVolume = true)
            }
            schedulePollIfNeeded()
        }
    }

    private val deckListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            isDeckReady = playbackState == Player.STATE_READY
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            cancelCrossfade(resetPrimaryVolume = true)
            clearStagedDeck()
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            tick()
            val delayMs = if (isCrossfading) FADE_STEP_MS else MONITOR_INTERVAL_MS
            if (primaryPlayer.isPlaying || isCrossfading) {
                handler.postDelayed(this, delayMs)
            }
        }
    }

    fun attach() {
        primaryPlayer.addListener(primaryListener)
        schedulePollIfNeeded()
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        primaryPlayer.removeListener(primaryListener)
        runCatching {
            deckPlayer?.removeListener(deckListener)
            deckPlayer?.release()
        }
        deckPlayer = null
        if (deckProcessors.isNotEmpty()) {
            runCatching { onReleaseProcessors(deckProcessors) }
            deckProcessors = emptyList()
        }
    }

    private fun schedulePollIfNeeded() {
        handler.removeCallbacks(pollRunnable)
        if (primaryPlayer.isPlaying || isCrossfading) {
            handler.postDelayed(pollRunnable, if (isCrossfading) FADE_STEP_MS else MONITOR_INTERVAL_MS)
        }
    }

    private fun ensureDeckPlayer(): ExoPlayer {
        deckPlayer?.let { return it }
        // Wajib membuat instance AudioProcessor terpisah agar tidak bentrok ByteBuffer dengan primaryPlayer
        val freshProcessors = audioProcessorsFactory()
        deckProcessors = freshProcessors
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8_000, 16_000, 1_000, 2_000)
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
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ false, // Tidak merebut audio focus dari primaryPlayer
            )
            .build()
            .also { created ->
                created.volume = 0f
                created.addListener(deckListener)
                deckPlayer = created
            }
    }

    private fun tick() {
        val configuredSec = MupaCrossfadeConfig.getDurationSeconds()
        if (configuredSec <= 0) {
            if (isCrossfading) cancelCrossfade(resetPrimaryVolume = true)
            return
        }

        if (isCrossfading) {
            stepActiveCrossfade()
            return
        }

        if (!primaryPlayer.isPlaying) return
        if (primaryPlayer.repeatMode == Player.REPEAT_MODE_ONE) return

        val nextIndex = primaryPlayer.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == primaryPlayer.currentMediaItemIndex) return

        val durationMs = primaryPlayer.duration
        if (durationMs <= 0L || durationMs == C.TIME_UNSET) return

        val positionMs = primaryPlayer.currentPosition.coerceAtLeast(0L)
        val remainingMs = durationMs - positionMs
        if (remainingMs <= 0L) return

        val requestedFadeMs = configuredSec * 1_000L
        // Jangan pernah memakan lebih dari 25% durasi lagu pendek
        val effectiveFadeMs = requestedFadeMs.coerceAtMost((durationMs / 4L).coerceAtLeast(1_500L))
        val preStageWindowMs = effectiveFadeMs + PRE_STAGE_LEAD_MS

        val nextItem = primaryPlayer.getMediaItemAt(nextIndex)

        // 1. Pra-muat dek sekunder ~5 detik sebelum jendela crossfade
        if (remainingMs <= preStageWindowMs && stagedMediaId != nextItem.mediaId) {
            stageNextOnDeck(nextItem, nextIndex)
        }

        // 2. Mulai overlap equal-power begitu masuk jendela crossfade dan dek siap
        if (remainingMs <= effectiveFadeMs && remainingMs >= MIN_TRIGGER_REMAINING_MS) {
            if (stagedMediaId == nextItem.mediaId && isDeckReady) {
                startCrossfade(nextIndex, remainingMs.coerceAtMost(effectiveFadeMs))
            }
        }
    }

    private fun stageNextOnDeck(item: MediaItem, index: Int) {
        val deck = ensureDeckPlayer()
        stagedMediaId = item.mediaId
        stagedTargetIndex = index
        isDeckReady = false
        deck.stop()
        deck.clearMediaItems()
        deck.volume = 0f
        deck.setMediaItem(item)
        deck.playWhenReady = false
        deck.prepare()
    }

    private fun startCrossfade(targetIndex: Int, fadeDurationMs: Long) {
        val deck = deckPlayer ?: return
        isCrossfading = true
        handoffTargetIndex = targetIndex
        activeCrossfadeDurationMs = fadeDurationMs.coerceAtLeast(800L)
        crossfadeStartWallMs = System.currentTimeMillis()
        deck.volume = 0f
        deck.seekTo(0L)
        deck.play()
        // Segera beralih ke tick 16ms (~60fps)
        handler.removeCallbacks(pollRunnable)
        handler.postDelayed(pollRunnable, FADE_STEP_MS)
    }

    private fun stepActiveCrossfade() {
        val deck = deckPlayer
        if (deck == null || !primaryPlayer.isPlaying) {
            cancelCrossfade(resetPrimaryVolume = true)
            return
        }

        val elapsed = (System.currentTimeMillis() - crossfadeStartWallMs).coerceAtLeast(0L)
        val durationMs = primaryPlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET } ?: Long.MAX_VALUE
        val primaryRemaining = (durationMs - primaryPlayer.currentPosition).coerceAtLeast(0L)

        val progress = (elapsed.toFloat() / activeCrossfadeDurationMs.toFloat()).coerceIn(0f, 1f)
        val (outGain, inGain) = MupaCrossfadeConfig.computeEqualPowerGains(progress)
        primaryPlayer.volume = outGain
        deck.volume = inGain

        // Handoff mulus ke primaryPlayer saat fade tuntas atau 90ms sebelum lagu lama berakhir
        if (progress >= 1f || primaryRemaining <= HANDOFF_LEAD_MS) {
            performSeamlessHandoff(deck)
        }
    }

    private fun performSeamlessHandoff(deck: ExoPlayer) {
        val targetIdx = handoffTargetIndex
        if (targetIdx == C.INDEX_UNSET || targetIdx >= primaryPlayer.mediaItemCount) {
            cancelCrossfade(resetPrimaryVolume = true)
            return
        }

        isPerformingHandoff = true
        isCrossfading = false
        val currentDeckPos = deck.currentPosition.coerceAtLeast(0L)

        // Pindahkan primaryPlayer ke posisi persis dek (sudah ter-cache lokal sehingga instan!)
        primaryPlayer.volume = 1f
        primaryPlayer.seekTo(targetIdx, currentDeckPos)
        if (!primaryPlayer.playWhenReady) {
            primaryPlayer.play()
        }

        // Tunggu primaryPlayer STATE_READY (maks 240ms) sebelum micro-ramp 40ms dek sekunder
        val handoffDeadlineMs = System.currentTimeMillis() + HANDOFF_WAIT_MAX_MS
        val finishHandoffRunnable = object : Runnable {
            override fun run() {
                val ready = primaryPlayer.playbackState == Player.STATE_READY
                if (!ready && System.currentTimeMillis() < handoffDeadlineMs) {
                    handler.postDelayed(this, HANDOFF_WAIT_POLL_MS)
                    return
                }
                handler.postDelayed(
                    {
                        runCatching {
                            deck.volume = 0f
                            deck.pause()
                            deck.stop()
                            deck.clearMediaItems()
                        }
                        stagedMediaId = null
                        stagedTargetIndex = C.INDEX_UNSET
                        isDeckReady = false
                        handoffTargetIndex = C.INDEX_UNSET
                        isPerformingHandoff = false
                        schedulePollIfNeeded()
                    },
                    MICRO_RAMP_MS,
                )
            }
        }
        handler.post(finishHandoffRunnable)
    }

    private fun cancelCrossfade(resetPrimaryVolume: Boolean) {
        isCrossfading = false
        handoffTargetIndex = C.INDEX_UNSET
        if (resetPrimaryVolume) {
            primaryPlayer.volume = 1f
        }
        deckPlayer?.let { deck ->
            runCatching {
                deck.volume = 0f
                deck.pause()
            }
        }
    }

    private fun clearStagedDeck() {
        stagedMediaId = null
        stagedTargetIndex = C.INDEX_UNSET
        isDeckReady = false
        deckPlayer?.let { deck ->
            runCatching {
                deck.stop()
                deck.clearMediaItems()
            }
        }
    }

    companion object {
        private const val MONITOR_INTERVAL_MS = 250L
        private const val FADE_STEP_MS = 16L
        private const val PRE_STAGE_LEAD_MS = 5_000L
        private const val MIN_TRIGGER_REMAINING_MS = 500L
        private const val HANDOFF_LEAD_MS = 90L
        private const val HANDOFF_WAIT_POLL_MS = 20L
        private const val HANDOFF_WAIT_MAX_MS = 240L
        private const val MICRO_RAMP_MS = 40L
    }
}
