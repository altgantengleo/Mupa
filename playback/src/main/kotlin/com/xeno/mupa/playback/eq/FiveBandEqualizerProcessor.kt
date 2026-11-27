package com.xeno.mupa.playback.eq

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class EqPreset(
    val label: String,
    val gainsDb: List<Float>,
) {
    FLAT("Flat", listOf(0f, 0f, 0f, 0f, 0f)),
    VOCAL("Vocal", listOf(-2f, 1f, 4f, 3f, 0f)),
    BASS("Bass", listOf(6f, 3f, 0f, -1f, -1f)),
    TREBLE("Treble", listOf(-1f, -1f, 0f, 3f, 5f)),
    WARM("Warm", listOf(3f, 2f, 0f, -2f, -3f)),
    CUSTOM("Kustom", listOf(0f, 0f, 0f, 0f, 0f)),
}

/**
 * Equalizer 5-band RBJ biquad nyata untuk Media3 ExoPlayer (60, 250, 1000, 4000, 12000 Hz; -12..+12 dB).
 * Menghindari Android LoudnessEnhancer bawaan yang bermasalah (sesuai docs/AUDIT.md §3.1 & §7.8).
 * Otomatis bypass tanpa biaya CPU saat semua band 0 dB (Flat) atau dimatikan.
 */
@OptIn(UnstableApi::class)
class FiveBandEqualizerProcessor : BaseAudioProcessor() {

    private data class BiquadCoeffs(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    )

    @Volatile
    private var enabled: Boolean = true

    @Volatile
    private var gainsDb: FloatArray = floatArrayOf(0f, 0f, 0f, 0f, 0f)

    @Volatile
    private var coeffs: Array<BiquadCoeffs> = computeAllCoeffs(48_000, floatArrayOf(0f, 0f, 0f, 0f, 0f))

    private var sampleRateHz: Int = 48_000
    private var channelCount: Int = 2

    // Direct Form II Transposed state: [channel][band][z1, z2]
    private var z1: Array<DoubleArray> = Array(2) { DoubleArray(BAND_COUNT) }
    private var z2: Array<DoubleArray> = Array(2) { DoubleArray(BAND_COUNT) }

    // Klon tersinkron untuk dek sekunder crossfade agar tiap ExoPlayer punya ByteBuffer & state filter terpisah
    private val syncedClones = java.util.concurrent.CopyOnWriteArrayList<FiveBandEqualizerProcessor>()

    fun createSyncedClone(): FiveBandEqualizerProcessor {
        val clone = FiveBandEqualizerProcessor()
        clone.setEnabled(enabled)
        clone.setBandGains(gainsDb.toList())
        syncedClones.add(clone)
        return clone
    }

    fun removeSyncedClone(clone: FiveBandEqualizerProcessor) {
        syncedClones.remove(clone)
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        syncedClones.forEach { it.setEnabled(isEnabled) }
    }

    fun setBandGains(newGainsDb: List<Float>) {
        val clamped = FloatArray(BAND_COUNT) { idx ->
            newGainsDb.getOrElse(idx) { 0f }.coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        gainsDb = clamped
        coeffs = computeAllCoeffs(sampleRateHz, clamped)
        val snapshot = clamped.toList()
        syncedClones.forEach { it.setBandGains(snapshot) }
    }

    fun currentGains(): List<Float> = gainsDb.toList()

    fun isFlatOrDisabled(): Boolean = !enabled || gainsDb.all { kotlin.math.abs(it) < 0.05f }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8_000)
        channelCount = inputAudioFormat.channelCount.coerceIn(1, 8)
        coeffs = computeAllCoeffs(sampleRateHz, gainsDb)
        resetFilterState()
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)
        if (isFlatOrDisabled()) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val activeCoeffs = coeffs
        val channels = channelCount
        val totalSamples = remaining / 2

        for (i in 0 until totalSamples) {
            val ch = i % channels
            var x = inputBuffer.short.toDouble() / 32768.0

            val chZ1 = z1[ch]
            val chZ2 = z2[ch]
            for (b in 0 until BAND_COUNT) {
                val c = activeCoeffs[b]
                val y = c.b0 * x + chZ1[b]
                chZ1[b] = c.b1 * x - c.a1 * y + chZ2[b]
                chZ2[b] = c.b2 * x - c.a2 * y
                x = y
            }

            // Soft-limit untuk mencegah hard clipping saat boost tinggi
            val limited = x.coerceIn(-0.9999, 0.9999)
            val pcm = (limited * 32767.0).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            output.putShort(pcm.toShort())
        }

        output.flip()
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun onReset() {
        resetFilterState()
    }

    private fun resetFilterState() {
        z1 = Array(channelCount) { DoubleArray(BAND_COUNT) }
        z2 = Array(channelCount) { DoubleArray(BAND_COUNT) }
    }

    companion object {
        const val BAND_COUNT = 5
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
        val BAND_FREQUENCIES_HZ = listOf(60, 250, 1000, 4000, 12000)
        val BAND_LABELS = listOf("60Hz", "250Hz", "1kHz", "4kHz", "12kHz")

        private fun computeAllCoeffs(sampleRate: Int, gains: FloatArray): Array<BiquadCoeffs> {
            return Array(BAND_COUNT) { idx ->
                val freq = BAND_FREQUENCIES_HZ[idx].toDouble().coerceAtMost(sampleRate * 0.45)
                val gain = gains[idx].toDouble()
                when (idx) {
                    0 -> lowShelf(sampleRate.toDouble(), freq, gain)
                    BAND_COUNT - 1 -> highShelf(sampleRate.toDouble(), freq, gain)
                    else -> peakingEq(sampleRate.toDouble(), freq, gain, q = 1.0)
                }
            }
        }

        private fun peakingEq(fs: Double, f0: Double, gainDb: Double, q: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val cosW0 = cos(w0)
            val alpha = sin(w0) / (2.0 * q)

            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * cosW0
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * cosW0
            val a2 = 1.0 - alpha / a
            return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        private fun lowShelf(fs: Double, f0: Double, gainDb: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val cosW0 = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val b0 = a * ((a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cosW0)
            val b2 = a * ((a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val a0 = (a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha
            val a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cosW0)
            val a2 = (a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha
            return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }

        private fun highShelf(fs: Double, f0: Double, gainDb: Double): BiquadCoeffs {
            val a = 10.0.pow(gainDb / 40.0)
            val w0 = 2.0 * PI * f0 / fs
            val cosW0 = cos(w0)
            val alpha = sin(w0) / 2.0 * sqrt(2.0)
            val twoSqrtAAlpha = 2.0 * sqrt(a) * alpha

            val b0 = a * ((a + 1.0) + (a - 1.0) * cosW0 + twoSqrtAAlpha)
            val b1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cosW0)
            val b2 = a * ((a + 1.0) + (a - 1.0) * cosW0 - twoSqrtAAlpha)
            val a0 = (a + 1.0) - (a - 1.0) * cosW0 + twoSqrtAAlpha
            val a1 = 2.0 * ((a - 1.0) - (a + 1.0) * cosW0)
            val a2 = (a + 1.0) - (a - 1.0) * cosW0 - twoSqrtAAlpha
            return BiquadCoeffs(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
        }
    }
}
