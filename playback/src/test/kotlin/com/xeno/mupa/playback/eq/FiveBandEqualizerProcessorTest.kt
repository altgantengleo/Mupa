package com.xeno.mupa.playback.eq

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@OptIn(UnstableApi::class)
class FiveBandEqualizerProcessorTest {

    @Test
    fun flatPresetBypassesSamplesBitExact() {
        val eq = FiveBandEqualizerProcessor()
        eq.configure(AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
        eq.flush()
        assertTrue(eq.isFlatOrDisabled())

        val input = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder())
        val expected = ShortArray(32) { idx -> (idx * 500 - 8000).toShort() }
        expected.forEach { input.putShort(it) }
        input.flip()

        eq.queueInput(input)
        val out = eq.output
        assertEquals(64, out.remaining())
        for (i in expected.indices) {
            assertEquals(expected[i], out.short)
        }
    }

    @Test
    fun bassBoostAmplifies60HzSineWithoutOverflow() {
        val eq = FiveBandEqualizerProcessor()
        eq.configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        eq.setBandGains(EqPreset.BASS.gainsDb)
        eq.flush()
        assertFalse(eq.isFlatOrDisabled())

        val sampleCount = 1024
        val input = ByteBuffer.allocateDirect(sampleCount * 2).order(ByteOrder.nativeOrder())
        var inEnergy = 0.0
        for (n in 0 until sampleCount) {
            val s = (sin(2.0 * PI * 60.0 * n / 48_000.0) * 6000.0).toInt().toShort()
            inEnergy += abs(s.toInt())
            input.putShort(s)
        }
        input.flip()

        eq.queueInput(input)
        val out = eq.output
        var outEnergy = 0.0
        while (out.remaining() >= 2) {
            outEnergy += abs(out.short.toInt())
        }
        assertTrue("Expected boosted 60Hz output energy ($outEnergy) > input ($inEnergy)", outEnergy > inEnergy * 1.2)
    }

    @Test
    fun presetsMatchAuditExactCurves() {
        assertEquals(listOf(6f, 3f, 0f, -1f, -1f), EqPreset.BASS.gainsDb)
        assertEquals(listOf(-2f, 1f, 4f, 3f, 0f), EqPreset.VOCAL.gainsDb)
        assertEquals(listOf(-1f, -1f, 0f, 3f, 5f), EqPreset.TREBLE.gainsDb)
        assertEquals(listOf(3f, 2f, 0f, -2f, -3f), EqPreset.WARM.gainsDb)
    }
}
