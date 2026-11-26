package com.xeno.mupa.core.designsystem.component

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
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.LocalMupaHazeState
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.GlassStyle
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * Permukaan lapisan melayang Mupa (Mini Player, Bottom Nav, Sheet).
 * - Default = SOLID (alpha 1.0, tanpa tembus teks — perbaikan B1 & B6).
 * - Crystal / Liquid+ hanya aktif bila dipilih di Pengaturan, API 31+, Mode OLED & Performa OFF,
 *   dan menggunakan real backdrop-blur Haze (Apache-2.0) pada konten latar belakang yang terdaftar
 *   di LocalMupaHazeState (fallback ke solid bila HazeState tidak tersedia).
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
    val hazeState = LocalMupaHazeState.current
    val canUseBackdropBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        style != GlassStyle.SOLID &&
        appearance.shouldUseBlurEffect &&
        hazeState != null

    val borderBrush = when {
        isOled -> Brush.linearGradient(listOf(MupaPalette.OledBorder, MupaPalette.OledBorder))
        style == GlassStyle.SOLID || !canUseBackdropBlur ->
            Brush.linearGradient(listOf(MupaPalette.Hairline, MupaPalette.Hairline))
        style == GlassStyle.LIQUID_PLUS -> Brush.horizontalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.24f),
                Color.White.copy(alpha = 0.14f),
                MupaPalette.WarmAmber.copy(alpha = 0.22f),
            ),
        )
        else -> Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.22f),
                MupaPalette.Hairline,
            ),
        )
    }

    val solidFill = if (isOled) MupaPalette.OledCard else MupaPalette.Surface1

    val blurModifier = if (canUseBackdropBlur && hazeState != null) {
        val blurRadius = if (style == GlassStyle.CRYSTAL) 20.dp else 14.dp
        val tintAlpha = if (style == GlassStyle.CRYSTAL) 0.78f else 0.72f
        Modifier.hazeEffect(
            state = hazeState,
            style = HazeStyle(
                backgroundColor = MupaPalette.Surface1,
                tint = HazeTint(MupaPalette.Surface1.copy(alpha = tintAlpha)),
                blurRadius = blurRadius,
                noiseFactor = 0f,
            ),
        )
    } else {
        Modifier.background(solidFill)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(blurModifier)
            .border(width = 1.dp, brush = borderBrush, shape = shape),
        content = content,
    )
}
