package com.xeno.mupa.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.playback.eq.EqPreset
import kotlin.math.cbrt
import kotlin.math.pow

enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

enum class PlayerOverlayPane {
    NONE,
    LYRICS,
    QUEUE,
    EQUALIZER,
}

@Immutable
data class MupaPlayerUiState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val lyrics: List<LyricsLine> = emptyList(),
    val lyricsIsPlain: Boolean = false,
    val lyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val lyricsProviderLabel: String? = null,
    val lyricsOffsetMs: Long = 0L,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqGainsDb: List<Float> = EqPreset.FLAT.gainsDb,
    val activeOverlay: PlayerOverlayPane = PlayerOverlayPane.NONE,
    val isCinemaMode: Boolean = false,
    val sleepTimerMinutes: Int? = null,
    val isBuffering: Boolean = false,
    val errorMessage: String? = null,
    val ultraDynamicPalette: UltraDynamicPalette = UltraDynamicPalette.BrandNoCover,
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val bufferedFraction: Float
        get() = if (durationMs > 0L) {
            (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
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
 * Menghitung baris lirik aktif, progres wipe, serta pengangkatan warna aktif di ruang OKLCH (L >= 0.87).
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

        // Binary search O(log n) agar perpindahan baris cepat tanpa scan linear tiap tick
        var low = 0
        var high = lines.lastIndex
        var activeIndex = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= effectivePos) {
                activeIndex = mid
                low = mid + 1
            } else {
                high = mid - 1
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
     * Memastikan warna wipe lirik aktif memenuhi target OKLCH L >= 0.87 (fallback krem #F6F4EF
     * jika cover terlalu gelap/netral) sesuai docs/AUDIT.md §7.7.
     */
    fun ensureReadableWipeColor(baseColor: Color): Color {
        val (l, a, b) = srgbToOklab(baseColor)
        if (l >= 0.87f) return baseColor
        val targetL = l.coerceAtLeast(0.89f).coerceAtMost(0.96f)
        val chroma = kotlin.math.sqrt(a * a + b * b)
        // Jaga kroma aman dalam gamut sRGB saat L tinggi
        val scale = if (chroma > 0.10f) 0.10f / chroma else 1f
        val lifted = oklabToSrgb(targetL, a * scale, b * scale)
        return lifted
    }

    internal fun srgbToOklab(c: Color): Triple<Float, Float, Float> {
        fun lin(v: Float): Double = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        val r = lin(c.red)
        val g = lin(c.green)
        val b = lin(c.blue)

        val l = cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

        val okL = (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s).toFloat()
        val okA = (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s).toFloat()
        val okB = (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s).toFloat()
        return Triple(okL, okA, okB)
    }

    private fun oklabToSrgb(okL: Float, okA: Float, okB: Float): Color {
        val l = (okL + 0.3963377774 * okA + 0.2158037573 * okB).pow(3.0)
        val m = (okL - 0.1055613458 * okA - 0.0638541728 * okB).pow(3.0)
        val s = (okL - 0.0894841775 * okA - 1.2914855480 * okB).pow(3.0)

        fun gamma(v: Double): Float {
            val clamped = v.coerceIn(0.0, 1.0)
            return (if (clamped <= 0.0031308) 12.92 * clamped else 1.055 * clamped.pow(1.0 / 2.4) - 0.055)
                .toFloat().coerceIn(0f, 1f)
        }

        val r = +4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
        val g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
        val b = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
        return Color(gamma(r), gamma(g), gamma(b), 1f)
    }
}
