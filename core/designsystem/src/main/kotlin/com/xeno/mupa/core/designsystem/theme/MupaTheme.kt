package com.xeno.mupa.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.xeno.mupa.core.model.GlassStyle

@Immutable
data class MupaAppearance(
    val glassStyle: GlassStyle = GlassStyle.LIQUID_PLUS,
    val isOledMode: Boolean = false,
    val isAuroraEnabled: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val dynamicCoverAccent: DynamicCoverAccent = DynamicCoverAccent.Default,
) {
    /** Aurora & blur otomatis mati pada Mode OLED atau Mode Performa. */
    val shouldRenderAurora: Boolean
        get() = isAuroraEnabled && !isOledMode && !isPerformanceMode

    val shouldUseBlurEffect: Boolean
        get() = !isOledMode && !isPerformanceMode

    /** Warna dinamis cover art untuk Aurora & Now Playing ikut nonaktif di OLED/Performa. */
    val effectiveCoverAccent: DynamicCoverAccent
        get() = if (isOledMode || isPerformanceMode) {
            DynamicCoverAccent.Default
        } else {
            dynamicCoverAccent
        }
}

val LocalMupaAppearance = staticCompositionLocalOf { MupaAppearance() }

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
