package com.xeno.mupa

import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.ui.player.LyricsSyncCalculator
import com.xeno.mupa.ui.player.formatDurationMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MupaPlayerLogicTest {

    @Test
    fun lyricsSyncTracksActiveLineProgressAndOffset() {
        val lines = listOf(
            LyricsLine(0L, 4_000L, "Baris pertama"),
            LyricsLine(4_000L, 8_000L, "Baris kedua"),
            LyricsLine(8_000L, 12_000L, "Baris ketiga"),
        )

        val mid = LyricsSyncCalculator.compute(lines, positionMs = 6_000L)
        assertEquals(1, mid.index)
        assertEquals(0.5f, mid.lineProgress, 0.01f)

        val shifted = LyricsSyncCalculator.compute(lines, positionMs = 6_000L, offsetMs = 2_500L)
        assertEquals(2, shifted.index)
        assertTrue(shifted.lineProgress in 0.1f..0.2f)
    }

    @Test
    fun darkCoverAccentIsLiftedForReadableLyricsWipe() {
        val veryDarkBlue = Color(0xFF101830)
        val lifted = LyricsSyncCalculator.ensureReadableWipeColor(veryDarkBlue)
        val luminance = 0.2126f * lifted.red + 0.7152f * lifted.green + 0.0722f * lifted.blue
        assertTrue("Expected lifted luminance >= 0.60, got $luminance", luminance >= 0.60f)
    }

    @Test
    fun durationFormattingProducesExpectedMmSs() {
        assertEquals("0:00", formatDurationMs(0L))
        assertEquals("3:34", formatDurationMs(214_000L))
        assertEquals("0:09", formatDurationMs(9_200L))
    }
}
