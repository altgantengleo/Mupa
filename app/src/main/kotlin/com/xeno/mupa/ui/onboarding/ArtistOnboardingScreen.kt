package com.xeno.mupa.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.component.MupaAuroraBackground
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import kotlin.math.abs

/**
 * Layar Onboarding Artis Favorit wajib (tanpa tombol lewati).
 * - Wajib pilih >= 2 artis, anjuran 4, maksimal 8.
 * - Tombol Back pada mode onboarding awal memanggil onExitApp() (keluar aplikasi, tidak melewati).
 * - Mode edit (dari Akun/Pengaturan) mengizinkan kembali ke layar sebelumnya.
 */
@Composable
fun ArtistOnboardingScreen(
    uiState: ArtistOnboardingUiState,
    isEditMode: Boolean,
    onQueryChange: (String) -> Unit,
    onToggleArtist: (String) -> Unit,
    onRetry: () -> Unit,
    onSubmit: () -> Unit,
    onNavigateBackOrExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = true) {
        onNavigateBackOrExit()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MupaAuroraBackground(primaryAlpha = 0.24f, secondaryAlpha = 0.14f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header (tanpa tombol Lewati!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isEditMode) {
                        IconButton(onClick = onNavigateBackOrExit) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Kembali",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    } else {
                        MupaBrandMark(glyphSize = 26.dp)
                    }
                }

                Text(
                    text = if (isEditMode) "Ubah Artis Favorit" else "Pilih Artis Favoritmu",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Pilih minimal ${ArtistOnboardingUiState.MIN_ARTISTS} artis (anjuran ${ArtistOnboardingUiState.RECOMMENDED_ARTISTS}) untuk meracik Beranda & Mix Mupa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Counter jelas
                val helperText = when {
                    uiState.selectedCount < ArtistOnboardingUiState.MIN_ARTISTS ->
                        "Pilih ${ArtistOnboardingUiState.MIN_ARTISTS - uiState.selectedCount} artis lagi untuk melanjutkan"
                    uiState.selectedCount < ArtistOnboardingUiState.RECOMMENDED_ARTISTS ->
                        "Siap lanjut — pilih sampai ${ArtistOnboardingUiState.RECOMMENDED_ARTISTS} untuk hasil terbaik"
                    uiState.reachedLimit ->
                        "Batas maksimal ${ArtistOnboardingUiState.MAX_ARTISTS} artis tercapai"
                    else ->
                        "Pilihan mantap untuk rekomendasi personal"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (uiState.canContinue) MupaPalette.CoralSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${uiState.selectedCount} / ${ArtistOnboardingUiState.MAX_ARTISTS}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (uiState.canContinue) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Cari artis atau genre...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus pencarian",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    shape = MupaRadii.Chip,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MupaPalette.CoralPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // Grid artis + state loading / kosong / error
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(
                            color = MupaPalette.CoralPrimary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    uiState.errorMessage != null -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = uiState.errorMessage,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                            OutlinedButton(onClick = onRetry) {
                                Text("Coba Lagi")
                            }
                        }
                    }
                    uiState.artists.isEmpty() -> {
                        Text(
                            text = "Artis tidak ditemukan untuk \"${uiState.query}\".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 104.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                items = uiState.artists,
                                key = { it.id },
                            ) { artist ->
                                val isSelected = artist.id in uiState.selectedArtistIds
                                ArtistBubbleItem(
                                    artist = artist,
                                    isSelected = isSelected,
                                    isDisabled = !isSelected && uiState.reachedLimit,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onToggleArtist(artist.id)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // Tombol Lanjut / Simpan (nonaktif bila < 2 artis)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Button(
                    onClick = onSubmit,
                    enabled = uiState.canContinue,
                    shape = MupaRadii.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MupaPalette.CoralPrimary,
                        contentColor = MupaPalette.LogoCharcoal,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                ) {
                    Text(
                        text = if (isEditMode) {
                            "Simpan Pilihan (${uiState.selectedCount})"
                        } else {
                            "Lanjut (${uiState.selectedCount})"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistBubbleItem(
    artist: Artist,
    isSelected: Boolean,
    isDisabled: Boolean,
    onClick: () -> Unit,
) {
    // Animasi pilih halus tanpa overshoot (StandardDecelerate)
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 0.95f else 1f,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "artistScale_${artist.id}",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MupaPalette.CoralPrimary else Color.Transparent,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "artistBorder_${artist.id}",
    )

    val avatarColors = listOf(
        Color(0xFFFF5A3C),
        Color(0xFFFFB067),
        Color(0xFF4ECDC4),
        Color(0xFF7AA2F7),
        Color(0xFFE87A90),
        Color(0xFFC77DFF),
    )
    val baseTint = avatarColors[abs(artist.id.hashCode()) % avatarColors.size]
    val initials = artist.name
        .split(" ")
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MupaRadii.Card)
            .semantics {
                selected = isSelected
                contentDescription = if (isSelected) {
                    "${artist.name}, dipilih"
                } else {
                    "Pilih ${artist.name}"
                }
            }
            .clickable(
                enabled = !isDisabled,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isDisabled) 0.45f else 1f
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            baseTint.copy(alpha = 0.86f),
                            MupaPalette.SurfaceContainerHigh,
                        ),
                    ),
                )
                .border(width = 3.dp, color = borderColor, shape = CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MupaPalette.LogoCream,
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.CoralPrimary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MupaPalette.LogoCharcoal,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MupaPalette.CoralBright else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(name = "Mupa Onboarding Artis", showBackground = true)
@Composable
private fun ArtistOnboardingPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface {
            ArtistOnboardingScreen(
                uiState = ArtistOnboardingUiState(
                    artists = listOf(
                        Artist("1", "Hindia", genre = "Indie"),
                        Artist("2", "Nadin Amizah", genre = "Folk"),
                        Artist("3", "Tulus", genre = "Pop"),
                        Artist("4", "Reality Club", genre = "Rock"),
                    ),
                    selectedArtistIds = setOf("1", "2"),
                    isLoading = false,
                ),
                isEditMode = false,
                onQueryChange = {},
                onToggleArtist = {},
                onRetry = {},
                onSubmit = {},
                onNavigateBackOrExit = {},
            )
        }
    }
}
