package com.xeno.mupa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xeno.mupa.BuildConfig
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences

/**
 * Tab Akun ringkas (sesuai Q9): profil lokal, statistik dasar, artis favorit + ubah tanpa hapus data,
 * dan akses cepat Pengaturan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountScreen(
    preferences: UserPreferences,
    favoriteArtists: List<Artist>,
    totalTracksCount: Int,
    favoriteTracksCount: Int,
    contentPadding: PaddingValues,
    onEditFavoriteArtists: () -> Unit,
    onOpenSettings: () -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "acc_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Akun",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Buka Pengaturan",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        item(key = "profile_summary") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MupaBrandMark(glyphSize = 28.dp)
                    Text(
                        text = "Pendengar Mupa · Mode Lokal & Streaming Tanpa Login",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        StatBadge(label = "Lagu", value = "$totalTracksCount")
                        StatBadge(label = "Favorit", value = "$favoriteTracksCount")
                        StatBadge(label = "Artis Pilihan", value = "${favoriteArtists.size}")
                    }
                }
            }
        }

        item(key = "favorite_artists_card") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Artis Favorit",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (favoriteArtists.isEmpty()) {
                        Text(
                            text = "Belum ada artis favorit terpilih.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            favoriteArtists.forEach { artist ->
                                AssistChip(
                                    onClick = onEditFavoriteArtists,
                                    label = { Text(artist.name) },
                                )
                            }
                        }
                    }
                    Button(
                        onClick = onEditFavoriteArtists,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MupaPalette.CoralPrimary,
                            contentColor = MupaPalette.LogoCharcoal,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                        )
                        Text(" Ubah Artis Favorit")
                    }
                }
            }
        }

        item(key = "quick_toggles") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsSwitchRow(
                        title = "Mode OLED",
                        subtitle = "Hitam pekat #000000, matikan blur dan aurora",
                        checked = preferences.isOledMode,
                        onCheckedChange = onOledModeChange,
                    )
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Buka Semua Pengaturan")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBadge(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MupaPalette.CoralBright,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Layar Pengaturan ringkas (sesuai Q9 & Aturan #7):
 * - Gaya Kaca (Crystal / Liquid+)
 * - Mode OLED (1 toggle)
 * - Aurora Latar (1 toggle)
 * - Mode Performa (1 toggle)
 * - Hapus Nama Artis dari Judul (1 toggle, non-destruktif)
 * - Ubah Artis Favorit
 * - Tentang Mupa (GPL-3.0)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    onEditFavoriteArtists: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "settings_topbar") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Pengaturan",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item(key = "settings_appearance") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Tampilan & Performa",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = preferences.glassStyle == GlassStyle.CRYSTAL,
                            onClick = { onGlassStyleChange(GlassStyle.CRYSTAL) },
                            label = { Text("Crystal") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                        FilterChip(
                            selected = preferences.glassStyle == GlassStyle.LIQUID_PLUS,
                            onClick = { onGlassStyleChange(GlassStyle.LIQUID_PLUS) },
                            label = { Text("Liquid+") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                    }
                    SettingsSwitchRow(
                        title = "Mode OLED",
                        subtitle = "Latar hitam #000000, menonaktifkan blur dan aurora",
                        checked = preferences.isOledMode,
                        onCheckedChange = onOledModeChange,
                    )
                    SettingsSwitchRow(
                        title = "Aurora Latar",
                        subtitle = "Gradien lembut adaptif dari sampul lagu",
                        checked = preferences.isAuroraEnabled,
                        onCheckedChange = onAuroraEnabledChange,
                    )
                    SettingsSwitchRow(
                        title = "Mode Performa",
                        subtitle = "Kurangi blur dan efek dinamis untuk HP spek rendah",
                        checked = preferences.isPerformanceMode,
                        onCheckedChange = onPerformanceModeChange,
                    )
                }
            }
        }

        item(key = "settings_library") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Pustaka & Personalisasi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    SettingsSwitchRow(
                        title = "Hapus Nama Artis dari Judul",
                        subtitle = "Rapikan format \"Artis - Judul\" secara non-destruktif dari nama file asli",
                        checked = preferences.stripArtistName,
                        onCheckedChange = onStripArtistNameChange,
                    )
                    OutlinedButton(
                        onClick = onEditFavoriteArtists,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Ubah Artis Favorit")
                    }
                }
            }
        }

        item(key = "settings_about") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Tentang Mupa",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Versi ${BuildConfig.VERSION_NAME} · Lisensi GNU GPL-3.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Pemutar musik lokal (Rust) dan streaming modular. Tanpa pelacakan, tanpa iklan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MupaPalette.LogoCharcoal,
                checkedTrackColor = MupaPalette.CoralPrimary,
            ),
        )
    }
}
