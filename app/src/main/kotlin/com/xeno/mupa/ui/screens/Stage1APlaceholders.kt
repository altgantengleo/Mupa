package com.xeno.mupa.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.ui.CoverAccentPreset

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeFoundationScreen(
    contentPadding: PaddingValues,
    activePreset: CoverAccentPreset,
    onCoverAccentPresetChange: (CoverAccentPreset) -> Unit,
) {
    val appearance = LocalMupaAppearance.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            MupaBrandMark(glyphSize = 30.dp)
        }

        item(key = "hero") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Mix Mupa & Untukmu",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Fondasi Beranda siap. Rak personalisasi berbasis artis onboarding, Mix Mupa, Ulangi Lagi, dan Belum Diputar akan diisi pada Tahap 1B.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MupaPalette.CoralPrimary,
                            contentColor = MupaPalette.LogoCharcoal,
                        ),
                    ) {
                        Text(text = "Putar Mix Mupa")
                    }
                }
            }
        }

        item(key = "aurora_demo") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Pratinjau Warna Dinamis Sampul",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (appearance.shouldRenderAurora) {
                            "Aurora aktif dengan aksen sampul terpilih."
                        } else {
                            "Aurora nonaktif karena Mode OLED, Mode Performa, atau toggle Aurora dimatikan."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CoverAccentPreset.entries.forEach { preset ->
                            val selected = preset == activePreset
                            FilterChip(
                                selected = selected,
                                onClick = { onCoverAccentPresetChange(preset) },
                                label = { Text(text = preset.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MupaPalette.CoralPrimary,
                                    selectedLabelColor = MupaPalette.LogoCharcoal,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SearchFoundationScreen(
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Cari",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item(key = "placeholder") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Pencarian Lagu, Artis & Lirik",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Kolom cari, chip filter, histori, dan hasil dummy lintas sumber akan dibangun pada Tahap 1B.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryFoundationScreen(
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Pustaka",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        item(key = "placeholder") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Lagu, Favorit & Playlist",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Daftar lagu, playlist pintar, dan pengaturan urutan pustaka akan diisi pada Tahap 1B.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountFoundationScreen(
    contentPadding: PaddingValues,
    preferences: UserPreferences,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "title") {
            Text(
                text = "Akun",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        item(key = "appearance_card") {
            MupaGlassSurface(
                shape = MupaRadii.Card,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = "Tampilan & Kaca",
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

                    PreferenceSwitchRow(
                        title = "Mode OLED",
                        subtitle = "Hitam pekat #000000, menonaktifkan blur dan aurora",
                        checked = preferences.isOledMode,
                        onCheckedChange = onOledModeChange,
                    )
                    PreferenceSwitchRow(
                        title = "Aurora Latar",
                        subtitle = "Cahaya lembut dari warna sampul atau palet Ember",
                        checked = preferences.isAuroraEnabled,
                        onCheckedChange = onAuroraEnabledChange,
                    )
                    PreferenceSwitchRow(
                        title = "Mode Performa",
                        subtitle = "Kurangi blur dan matikan efek warna dinamis",
                        checked = preferences.isPerformanceMode,
                        onCheckedChange = onPerformanceModeChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
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
