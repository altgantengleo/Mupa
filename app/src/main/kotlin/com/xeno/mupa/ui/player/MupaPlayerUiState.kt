package com.xeno.mupa.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class PlayerOverlayPane {
    NONE,
    LYRICS,
    QUEUE,
}

@Immutable
data class MupaPlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: List<LyricsLine> = emptyList(),
    val lyricsOffsetMs: Long = 0L,
    val activeOverlay: PlayerOverlayPane = PlayerOverlayPane.NONE,
    val sleepTimerMinutes: Int? = null,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val coverAccent: DynamicCoverAccent
        get() {
            val argb = currentTrack?.accentColorArgb ?: return DynamicCoverAccent.Default
            val primary = Color(argb.toInt())
            val secondary = Color(
                red = (primary.red * 0.72f + 0.24f).coerceIn(0f, 1f),
                green = (primary.green * 0.80f + 0.14f).coerceIn(0f, 1f),
                blue = (primary.blue * 0.65f + 0.28f).coerceIn(0f, 1f),
                alpha = 1f,
            )
            return DynamicCoverAccent(primary = primary, secondary = secondary)
        }
}

/**
 * Menghitung indeks baris lirik aktif dan progres wipe 0f..1f untuk baris tersebut.
 * Dipisahkan agar mudah diuji unit test.
 */
object LyricsSyncCalculator {
    data class ActiveLineState(
        val index: Int,
        val lineProgress: Float,
    )

    fun compute(
        lines: List<LyricsLine>,
        positionMs: Long,
        offsetMs: Long = 0L,
    ): ActiveLineState {
        if (lines.isEmpty()) return ActiveLineState(index = -1, lineProgress = 0f)
        val effectivePos = (positionMs + offsetMs).coerceAtLeast(0L)

        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= effectivePos) {
                activeIndex = i
            } else {
                break
            }
        }
        if (activeIndex < 0) return ActiveLineState(index = -1, lineProgress = 0f)

        val current = lines[activeIndex]
        val nextStart = lines.getOrNull(activeIndex + 1)?.timestampMs
        val endMs = current.endTimestampMs ?: nextStart ?: (current.timestampMs + 3_500L)
        val spanMs = (endMs - current.timestampMs).coerceAtLeast(400L)
        val progress = ((effectivePos - current.timestampMs).toFloat() / spanMs.toFloat()).coerceIn(0f, 1f)

        return ActiveLineState(index = activeIndex, lineProgress = progress)
    }

    /**
     * Memastikan warna wipe lirik tetap terang dan terbaca (minimal luminansi relatif tinggi)
     * meskipun warna dominan cover art gelap (mengikuti prinsip OKLCH L>=0.87 di AUDIT.md).
     */
    fun ensureReadableWipeColor(baseColor: Color): Color {
        val luminance = 0.2126f * baseColor.red + 0.7152f * baseColor.green + 0.0722f * baseColor.blue
        return if (luminance >= 0.62f) {
            baseColor
        } else {
            val lift = ((0.72f - luminance) / (1f - luminance).coerceAtLeast(0.05f)).coerceIn(0.35f, 0.82f)
            Color(
                red = baseColor.red + (1f - baseColor.red) * lift,
                green = baseColor.green + (1f - baseColor.green) * lift,
                blue = baseColor.blue + (1f - baseColor.blue) * lift,
                alpha = 1f,
            )
        }
    }
}
