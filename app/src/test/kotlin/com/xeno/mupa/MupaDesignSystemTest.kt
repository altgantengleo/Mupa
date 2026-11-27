package com.xeno.mupa

import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaEmberColorScheme
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaOledColorScheme
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MupaDesignSystemTest {

    @Test
    fun coralPrimaryUsesDarkCharcoalInkWithWcagAaContrast() {
        assertEquals(MupaPalette.LogoCoral, MupaEmberColorScheme.primary)
        assertEquals(MupaPalette.LogoCharcoal, MupaEmberColorScheme.onPrimary)

        val contrast = contrastRatio(MupaPalette.LogoCharcoal, MupaPalette.LogoCoral)
        assertTrue("Expected contrast >= 4.5, got $contrast", contrast >= 4.5)
    }

    @Test
    fun creamTextPassesWcagAaaAcrossAllDarkAndOledSurfaces() {
        val backgrounds = listOf(
            MupaPalette.Background,
            MupaPalette.Surface,
            MupaPalette.OledBackground,
        )
        backgrounds.forEach { bg ->
            val contrast = contrastRatio(MupaPalette.LogoCream, bg)
            assertTrue("Expected contrast >= 7.0, got $contrast", contrast >= 7.0)
        }
    }

    @Test
    fun oledAndPerformanceModesDisableAuroraBlurAndDynamicCoverTint() {
        val customCover = DynamicCoverAccent(
            primary = Color(0xFF3EC9B0),
            secondary = Color(0xFF639BFF),
        )

        val oledAppearance = MupaAppearance(
            isOledMode = true,
            isUltraDynamicEnabled = true,
            isPerformanceMode = false,
            dynamicCoverAccent = customCover,
        )
        assertFalse(oledAppearance.shouldRenderAurora)
        assertFalse(oledAppearance.shouldUseBlurEffect)
        assertEquals(DynamicCoverAccent.Default, oledAppearance.effectiveCoverAccent)
        assertEquals(Color.Black, MupaOledColorScheme.background)

        val perfAppearance = MupaAppearance(
            isOledMode = false,
            isUltraDynamicEnabled = true,
            isPerformanceMode = true,
            dynamicCoverAccent = customCover,
        )
        assertFalse(perfAppearance.shouldRenderAurora)
        assertFalse(perfAppearance.shouldUseBlurEffect)
        assertEquals(DynamicCoverAccent.Default, perfAppearance.effectiveCoverAccent)
    }

    @Test
    fun motionTokensStrictlyForbidOvershoot() {
        assertTrue(MupaMotion.NoBounceDampingRatio >= 1.0f)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(v: Float): Double {
            val d = v.toDouble()
            return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }
}
