package com.xeno.mupa.core.designsystem.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.GlassStyle

/**
 * Permukaan kaca reusable Mupa yang mendukung gaya Crystal dan Liquid+.
 * - Android 12+ (API 31+): memakai RenderEffect hanya pada lapisan difusi latar belakang (teks/ikon tetap tajam).
 * - Di bawah API 31 atau Mode Performa: fallback solid/translusen bersih tanpa RenderEffect.
 * - Mode OLED: latar hitam pekat #000000 dengan garis tepi tipis tanpa blur maupun glow.
 */
@Composable
fun MupaGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MupaRadii.FloatingNavBar,
    styleOverride: GlassStyle? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val appearance = LocalMupaAppearance.current
    val style = styleOverride ?: appearance.glassStyle
    val isOled = appearance.isOledMode
    val useRenderEffect = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appearance.shouldUseBlurEffect
    val accent = appearance.effectiveCoverAccent

    val borderBrush = when {
        isOled -> Brush.linearGradient(
            colors = listOf(MupaPalette.OledBorder, MupaPalette.OledBorder),
        )
        style == GlassStyle.LIQUID_PLUS -> Brush.horizontalGradient(
            colors = listOf(
                accent.primary.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.36f),
                accent.secondary.copy(alpha = 0.36f),
            ),
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                MupaPalette.OutlineVariant.copy(alpha = 0.55f),
            ),
        )
    }

    val baseBackgroundColor = when {
        isOled -> MupaPalette.OledBackground
        !useRenderEffect -> when (style) {
            GlassStyle.CRYSTAL -> Color(0xEC1B1B22)
            GlassStyle.LIQUID_PLUS -> Color(0xEA17171D)
        }
        style == GlassStyle.CRYSTAL -> Color(0xCC1B1B23)
        else -> Color(0xBA14141A)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseBackgroundColor)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
    ) {
        if (!isOled) {
            val blurRadiusPx = when (style) {
                GlassStyle.CRYSTAL -> 22f
                GlassStyle.LIQUID_PLUS -> 14f
            }
            val sheenBrush = when (style) {
                GlassStyle.CRYSTAL -> Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f),
                        accent.primary.copy(alpha = 0.06f),
                        Color.Transparent,
                    ),
                )
                GlassStyle.LIQUID_PLUS -> Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        accent.primary.copy(alpha = 0.09f),
                        accent.secondary.copy(alpha = 0.07f),
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && appearance.shouldUseBlurEffect) {
                            renderEffect = RenderEffect
                                .createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    }
                    .background(sheenBrush),
            )
        }

        content()
    }
}
