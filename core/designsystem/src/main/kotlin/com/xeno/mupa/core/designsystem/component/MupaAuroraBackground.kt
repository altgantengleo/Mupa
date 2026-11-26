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

/**
 * Latar belakang Aurora dua blob radial lembut di bagian atas layar.
 * Menggunakan warna dinamis cover art jika aktif, atau palet default Ember Editorial (#FF5A3C & #FFB067).
 * Otomatis nonaktif pada Mode OLED, Mode Performa, atau saat toggle Aurora dimatikan.
 */
@Composable
fun MupaAuroraBackground(
    modifier: Modifier = Modifier,
    primaryAlpha: Float = 0.28f,
    secondaryAlpha: Float = 0.18f,
) {
    val appearance = LocalMupaAppearance.current
    if (!appearance.shouldRenderAurora) return

    val targetAccent = appearance.effectiveCoverAccent
    val primaryColor by animateColorAsState(
        targetValue = targetAccent.primary,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationColorCrossfadeMs),
        label = "auroraPrimaryColor",
    )
    val secondaryColor by animateColorAsState(
        targetValue = targetAccent.secondary,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationColorCrossfadeMs),
        label = "auroraSecondaryColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val width = size.width
                val height = size.height
                val topBandHeight = height.coerceAtMost(width * 1.35f)

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = primaryAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(width * 0.14f, topBandHeight * 0.04f),
                        radius = width * 0.85f,
                    ),
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            secondaryColor.copy(alpha = secondaryAlpha),
                            Color.Transparent,
                        ),
                        center = Offset(width * 0.92f, topBandHeight * 0.12f),
                        radius = width * 0.78f,
                    ),
                )
            },
    )
}
