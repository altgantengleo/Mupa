package com.xeno.mupa.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import kotlin.math.max
import kotlin.math.min

/**
 * Aurora halus Mupa Calm (perbaikan B10):
 * - Kroma & luminansi warna cover art dibatasi agar tidak pernah menghasilkan tint coklat kusam.
 * - Dibatasi ketat pada < 38% bagian atas layar dan memudar habis sebelum area konten/teks kritis.
 * - Otomatis nonaktif pada Mode OLED, Mode Performa, atau saat toggle Aurora dimatikan.
 */
@Composable
fun MupaAuroraBackground(
    modifier: Modifier = Modifier,
    primaryAlpha: Float = 0.13f,
    secondaryAlpha: Float = 0.08f,
) {
    val appearance = LocalMupaAppearance.current
    if (!appearance.shouldRenderAurora) return

    val targetAccent = appearance.effectiveCoverAccent
    val calmPrimary = clampAuroraColor(targetAccent.primary)
    val calmSecondary = clampAuroraColor(targetAccent.secondary)

    val primaryColor by animateColorAsState(
        targetValue = calmPrimary,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationColorCrossfadeMs),
        label = "auroraPrimaryColor",
    )
    val secondaryColor by animateColorAsState(
        targetValue = calmSecondary,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationColorCrossfadeMs),
        label = "auroraSecondaryColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val width = size.width
                val maxBandHeight = size.height * 0.38f

                // Blob primer kecil di kanan atas (jauh dari judul layar di kiri atas)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = primaryAlpha),
                            primaryColor.copy(alpha = primaryAlpha * 0.35f),
                            Color.Transparent,
                        ),
                        center = Offset(width * 0.82f, maxBandHeight * 0.12f),
                        radius = min(width * 0.68f, maxBandHeight * 0.95f),
                    ),
                )

                // Blob sekunder sangat lembut di kiri atas
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = secondaryAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(width * 0.18f, maxBandHeight * 0.05f),
                        radius = min(width * 0.54f, maxBandHeight * 0.80f),
                    ),
                )
            },
    )
}

/**
 * Membatasi saturasi & kecerahan warna cover art agar saat di-blend ke arang #16161A
 * tetap bersih (tidak berubah menjadi coklat kotor).
 */
private fun clampAuroraColor(color: Color): Color {
    val maxC = max(color.red, max(color.green, color.blue))
    val minC = min(color.red, min(color.green, color.blue))
    val luma = 0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue
    val chroma = maxC - minC
    // Desaturasi ringan dan jaga agar warna tetap bersih tanpa menabrak kontras teks
    val desatFactor = if (chroma > 0.55f) 0.75f else 0.90f
    val r = (luma + (color.red - luma) * desatFactor).coerceIn(0.18f, 0.88f)
    val g = (luma + (color.green - luma) * desatFactor).coerceIn(0.16f, 0.82f)
    val b = (luma + (color.blue - luma) * desatFactor).coerceIn(0.18f, 0.88f)
    return Color(r, g, b, 1f)
}
