package com.xeno.mupa.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Palet Mupa — Opsi A: Ember Editorial (berbasis warna logo terverifikasi).
 * Warna inti logo:
 * - Coral: #FF5A3C
 * - Charcoal: #16161A
 * - Cream: #F6F4EF
 *
 * Aturan kontras wajib: teks/ikon di atas permukaan koral selalu menggunakan #16161A (WCAG AA 5.83:1+).
 */
object MupaPalette {
    val LogoCoral = Color(0xFFFF5A3C)
    val LogoCharcoal = Color(0xFF16161A)
    val LogoCream = Color(0xFFF6F4EF)

    // Ember Editorial Dark Surfaces
    val Background = Color(0xFF0E0E11)
    val Surface = LogoCharcoal
    val SurfaceContainerLowest = Color(0xFF0A0A0D)
    val SurfaceContainerLow = Color(0xFF141418)
    val SurfaceContainer = Color(0xFF1E1E24)
    val SurfaceContainerHigh = Color(0xFF26262E)
    val SurfaceContainerHighest = Color(0xFF30303A)

    // Accents
    val CoralPrimary = LogoCoral
    val CoralBright = Color(0xFFFF7357)
    val CoralSoft = Color(0xFFFF9A84)
    val CoralContainer = Color(0xFF3D1F1B)
    val OnCoralContainer = Color(0xFFFFDAD2)

    val WarmAmber = Color(0xFFFFB067)
    val WarmAmberContainer = Color(0xFF3A2817)
    val OnWarmAmberContainer = Color(0xFFFFE1C4)

    // Text & Borders
    val OnSurface = LogoCream
    val OnSurfaceVariant = Color(0xFFB4B0A8)
    val OnSurfaceMuted = Color(0xFF85828A)
    val Outline = Color(0xFF47454F)
    val OutlineVariant = Color(0xFF2E2E36)

    // Semantic Status
    val Error = Color(0xFFFF6B6B)
    val OnError = LogoCharcoal
    val ErrorContainer = Color(0xFF45191C)
    val OnErrorContainer = Color(0xFFFFD6D6)

    // OLED Pure Black Surfaces
    val OledBackground = Color(0xFF000000)
    val OledSurface = Color(0xFF000000)
    val OledCard = Color(0xFF0A0A0E)
    val OledCardHigh = Color(0xFF121218)
    val OledBorder = Color(0xFF22222B)
    val OledOnSurface = Color(0xFFEAE7E1)
}

@Immutable
data class DynamicCoverAccent(
    val primary: Color = MupaPalette.LogoCoral,
    val secondary: Color = MupaPalette.WarmAmber,
) {
    companion object {
        val Default = DynamicCoverAccent(
            primary = MupaPalette.LogoCoral,
            secondary = MupaPalette.WarmAmber,
        )
    }
}

val MupaEmberColorScheme = darkColorScheme(
    primary = MupaPalette.CoralPrimary,
    onPrimary = MupaPalette.LogoCharcoal,
    primaryContainer = MupaPalette.CoralContainer,
    onPrimaryContainer = MupaPalette.OnCoralContainer,
    secondary = MupaPalette.WarmAmber,
    onSecondary = MupaPalette.LogoCharcoal,
    secondaryContainer = MupaPalette.WarmAmberContainer,
    onSecondaryContainer = MupaPalette.OnWarmAmberContainer,
    tertiary = MupaPalette.CoralSoft,
    onTertiary = MupaPalette.LogoCharcoal,
    background = MupaPalette.Background,
    onBackground = MupaPalette.OnSurface,
    surface = MupaPalette.Surface,
    onSurface = MupaPalette.OnSurface,
    surfaceVariant = MupaPalette.SurfaceContainerHigh,
    onSurfaceVariant = MupaPalette.OnSurfaceVariant,
    surfaceContainerLowest = MupaPalette.SurfaceContainerLowest,
    surfaceContainerLow = MupaPalette.SurfaceContainerLow,
    surfaceContainer = MupaPalette.SurfaceContainer,
    surfaceContainerHigh = MupaPalette.SurfaceContainerHigh,
    surfaceContainerHighest = MupaPalette.SurfaceContainerHighest,
    outline = MupaPalette.Outline,
    outlineVariant = MupaPalette.OutlineVariant,
    error = MupaPalette.Error,
    onError = MupaPalette.OnError,
    errorContainer = MupaPalette.ErrorContainer,
    onErrorContainer = MupaPalette.OnErrorContainer,
)

val MupaOledColorScheme = MupaEmberColorScheme.copy(
    background = MupaPalette.OledBackground,
    onBackground = MupaPalette.OledOnSurface,
    surface = MupaPalette.OledSurface,
    onSurface = MupaPalette.OledOnSurface,
    surfaceVariant = MupaPalette.OledCardHigh,
    surfaceContainerLowest = MupaPalette.OledBackground,
    surfaceContainerLow = MupaPalette.OledBackground,
    surfaceContainer = MupaPalette.OledCard,
    surfaceContainerHigh = MupaPalette.OledCardHigh,
    surfaceContainerHighest = Color(0xFF1A1A22),
    outlineVariant = MupaPalette.OledBorder,
)
