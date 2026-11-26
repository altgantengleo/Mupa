package com.xeno.mupa.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Palet Mupa Calm — Opsi A: Ember Editorial (berbasis warna logo terverifikasi).
 * Prinsip: permukaan SOLID bertingkat, garis hairline 8–10%, koral dipakai hemat hanya untuk
 * tombol primer/Play, status aktif, dan progres. Teks di atas koral wajib #16161A (WCAG AA 5.83:1+).
 */
object MupaPalette {
    val LogoCoral = Color(0xFFFF5A3C)
    val LogoCharcoal = Color(0xFF16161A)
    val LogoCream = Color(0xFFF6F4EF)

    // Mupa Calm Solid Surfaces
    val Background = LogoCharcoal           // #16161A
    val Surface = LogoCharcoal              // #16161A
    val Surface1 = Color(0xFF1D1D22)        // Card / Floating Chrome Solid
    val Surface2 = Color(0xFF25252B)        // Elevated Control / Input / Tile
    val Surface3 = Color(0xFF2E2E36)        // Pressed / Hover / Higher elevation
    val Hairline = Color(0x17FFFFFF)        // Putih ~9%

    // Legacy aliases mapped cleanly to Mupa Calm scale
    val SurfaceContainerLowest = Color(0xFF111115)
    val SurfaceContainerLow = Color(0xFF19191E)
    val SurfaceContainer = Surface1
    val SurfaceContainerHigh = Surface2
    val SurfaceContainerHighest = Surface3

    // Accents (hemat)
    val CoralPrimary = LogoCoral            // #FF5A3C
    val CoralPressed = Color(0xFFE0482C)    // Pressed lebih gelap
    val CoralBright = Color(0xFFFF7357)
    val CoralSoft = Color(0xFFFF9A84)
    val CoralTint14 = Color(0x24FF5A3C)     // Pill indikator nav aktif alpha 14%
    val CoralContainer = Color(0xFF331D1A)
    val OnCoralContainer = Color(0xFFFFDAD2)

    val WarmAmber = Color(0xFFFFB067)       // Hanya aksen kecil
    val WarmAmberContainer = Color(0xFF2A241E)
    val OnWarmAmberContainer = Color(0xFFFFE1C4)

    // Text hierarchy (Krem #F6F4EF pada 100% / 62% / 42%)
    val TextPrimary = LogoCream
    val TextSecondary = Color(0x9EF6F4EF)   // ~62%
    val TextTertiary = Color(0x6BF6F4EF)    // ~42%
    val OnSurface = TextPrimary
    val OnSurfaceVariant = TextSecondary
    val OnSurfaceMuted = TextTertiary
    val Outline = Color(0x24FFFFFF)
    val OutlineVariant = Hairline

    // Semantic Status
    val Error = Color(0xFFFF6B6B)
    val OnError = LogoCharcoal
    val ErrorContainer = Color(0xFF3B1B1E)
    val OnErrorContainer = Color(0xFFFFD6D6)

    // OLED Pure Black Surfaces
    val OledBackground = Color(0xFF000000)
    val OledSurface = Color(0xFF000000)
    val OledCard = Color(0xFF0A0A0E)
    val OledCardHigh = Color(0xFF14141A)
    val OledBorder = Color(0x14FFFFFF)      // ~8%
    val OledOnSurface = Color(0xFFF0EEE9)
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
    secondary = MupaPalette.Surface2,
    onSecondary = MupaPalette.TextPrimary,
    secondaryContainer = MupaPalette.Surface2,
    onSecondaryContainer = MupaPalette.TextPrimary,
    tertiary = MupaPalette.WarmAmber,
    onTertiary = MupaPalette.LogoCharcoal,
    background = MupaPalette.Background,
    onBackground = MupaPalette.TextPrimary,
    surface = MupaPalette.Surface,
    onSurface = MupaPalette.TextPrimary,
    surfaceVariant = MupaPalette.Surface2,
    onSurfaceVariant = MupaPalette.TextSecondary,
    surfaceContainerLowest = MupaPalette.SurfaceContainerLowest,
    surfaceContainerLow = MupaPalette.SurfaceContainerLow,
    surfaceContainer = MupaPalette.Surface1,
    surfaceContainerHigh = MupaPalette.Surface2,
    surfaceContainerHighest = MupaPalette.Surface3,
    outline = MupaPalette.Outline,
    outlineVariant = MupaPalette.Hairline,
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
    surfaceContainerHighest = Color(0xFF1C1C24),
    outlineVariant = MupaPalette.OledBorder,
)
