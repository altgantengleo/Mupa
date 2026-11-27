package com.xeno.mupa.core.designsystem.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.model.GlassStyle
import dev.chrisbanes.haze.HazeState

@Immutable
data class MupaAppearance(
    val glassStyle: GlassStyle = GlassStyle.SOLID,
    val isOledMode: Boolean = false,
    val isUltraDynamicEnabled: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val dynamicCoverAccent: DynamicCoverAccent = DynamicCoverAccent.Default,
    val ultraDynamicPalette: UltraDynamicPalette = UltraDynamicPalette.BrandNoCover,
) {
    /** Alias kompatibilitas untuk pengujian lama; dipetakan ke isUltraDynamicEnabled. */
    val isAuroraEnabled: Boolean get() = isUltraDynamicEnabled

    val shouldRenderUltraDynamic: Boolean
        get() = isUltraDynamicEnabled && !isOledMode

    val shouldRenderAurora: Boolean
        get() = isUltraDynamicEnabled && !isOledMode && !isPerformanceMode

    val shouldUseBlurEffect: Boolean
        get() = glassStyle != GlassStyle.SOLID && !isOledMode && !isPerformanceMode

    val effectiveUltraDynamicPalette: UltraDynamicPalette
        get() = when {
            isOledMode -> UltraDynamicPalette.OledBlack
            !isUltraDynamicEnabled -> UltraDynamicPalette.BrandNoCover
            else -> ultraDynamicPalette
        }

    val effectiveCoverAccent: DynamicCoverAccent
        get() = if (isOledMode || isPerformanceMode || !isUltraDynamicEnabled) {
            DynamicCoverAccent.Default
        } else {
            ultraDynamicPalette.toDynamicCoverAccent()
        }
}

/**
 * Satu sumber kebenaran insets untuk seluruh layar scroll agar tidak pernah tertimpa
 * Bottom Nav maupun Mini Player (perbaikan B1).
 */
@Immutable
data class PlayerChromeInsets(
    val topInset: Dp = 0.dp,
    val bottomChromeHeight: Dp = 0.dp,
    val bottomContentPadding: Dp = 20.dp,
    val hasMiniPlayer: Boolean = false,
) {
    fun screenPaddingValues(
        horizontal: Dp = MupaSpacing.ScreenHorizontal,
        extraTop: Dp = 12.dp,
    ): PaddingValues = PaddingValues(
        start = horizontal,
        end = horizontal,
        top = topInset + extraTop,
        bottom = bottomContentPadding,
    )
}

val LocalMupaAppearance = staticCompositionLocalOf { MupaAppearance() }
val LocalPlayerChromeInsets = compositionLocalOf { PlayerChromeInsets() }
val LocalMupaHazeState = compositionLocalOf<HazeState?> { null }

@Composable
fun MupaTheme(
    appearance: MupaAppearance = MupaAppearance(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (appearance.isOledMode) {
        MupaOledColorScheme
    } else {
        MupaEmberColorScheme
    }

    CompositionLocalProvider(LocalMupaAppearance provides appearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MupaTypography,
            shapes = MupaShapes,
            content = content,
        )
    }
}
