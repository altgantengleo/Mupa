package com.xeno.mupa.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.xeno.mupa.BuildConfig
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.component.MupaChipRow
import com.xeno.mupa.core.designsystem.component.MupaFilterChip
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSettingRow
import com.xeno.mupa.core.designsystem.component.MupaStatTile
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences

/**
 * Tab Akun Mupa Calm (perbaikan B8):
 * - Header profil ringkas tanpa ikon gerigi berlebihan.
 * - 3 StatTile dalam satu baris dengan angka krem (bukan oranye).
 * - Artis Favorit (chip + tautan teks "Ubah", tanpa tombol pill oranye besar).
 * - Seluruh SettingRow inline langsung di Akun (tanpa tombol "Buka Semua Pengaturan").
 * - Baris versi, lisensi GPL-3.0, & atribusi NOTICE.
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
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MupaSpacing.SectionGap),
    ) {
        // Header profil ringkas
        item(key = "acc_header") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Akun",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MupaBrandMark(glyphSize = 22.dp)
                    Text(
                        text = "· Tanpa login · Lokal & Streaming",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 3 StatTile satu baris (angka krem)
        item(key = "acc_stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MupaStatTile(
                    label = "Lagu Lokal",
                    value = "$totalTracksCount",
                    modifier = Modifier.weight(1f),
                )
                MupaStatTile(
                    label = "Favorit",
                    value = "$favoriteTracksCount",
                    modifier = Modifier.weight(1f),
                )
                MupaStatTile(
                    label = "Artis Pilihan",
                    value = "${favoriteArtists.size}",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Artis Favorit (chip + tombol teks "Ubah")
        item(key = "acc_artists") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MupaSectionHeader(
                    title = "Artis Favorit",
                    actionText = "Ubah",
                    onActionClick = onEditFavoriteArtists,
                )
                if (favoriteArtists.isEmpty()) {
                    Text(
                        text = "Belum ada artis favorit terpilih.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        favoriteArtists.forEach { artist ->
                            MupaFilterChip(
                                label = artist.name,
                                selected = false,
                                onClick = onEditFavoriteArtists,
                            )
                        }
                    }
                }
            }
        }

        // Pengaturan inline (tanpa tombol "Buka Semua Pengaturan")
        item(key = "acc_settings_inline") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MupaSectionHeader(title = "Tampilan & Pustaka")

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MupaRadii.Card)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Permukaan Melayang",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Solid direkomendasikan. Crystal & Liquid+ memakai blur latar belakang Haze (API 31+)."
                        } else {
                            "Solid aktif (blur latar belakang memerlukan Android 12+)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    MupaChipRow(modifier = Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                        MupaFilterChip(
                            label = "Solid",
                            selected = preferences.glassStyle == GlassStyle.SOLID,
                            onClick = { onGlassStyleChange(GlassStyle.SOLID) },
                        )
                        MupaFilterChip(
                            label = "Crystal",
                            selected = preferences.glassStyle == GlassStyle.CRYSTAL,
                            onClick = { onGlassStyleChange(GlassStyle.CRYSTAL) },
                        )
                        MupaFilterChip(
                            label = "Liquid+",
                            selected = preferences.glassStyle == GlassStyle.LIQUID_PLUS,
                            onClick = { onGlassStyleChange(GlassStyle.LIQUID_PLUS) },
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    MupaSettingRow(
                        title = "Mode OLED",
                        subtitle = "Hitam pekat #000000, menonaktifkan blur dan aurora",
                        checked = preferences.isOledMode,
                        onCheckedChange = onOledModeChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    MupaSettingRow(
                        title = "Aurora Latar",
                        subtitle = "Tint lembut dari sampul di bagian atas layar",
                        checked = preferences.isAuroraEnabled,
                        onCheckedChange = onAuroraEnabledChange,
                        enabled = !preferences.isOledMode && !preferences.isPerformanceMode,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    MupaSettingRow(
                        title = "Mode Performa",
                        subtitle = "Matikan blur & efek dinamis untuk HP spek rendah",
                        checked = preferences.isPerformanceMode,
                        onCheckedChange = onPerformanceModeChange,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    MupaSettingRow(
                        title = "Hapus Nama Artis dari Judul",
                        subtitle = "Rapikan format \"Artis - Judul\" secara non-destruktif tanpa mengosongkan nama artis",
                        checked = preferences.stripArtistName,
                        onCheckedChange = onStripArtistNameChange,
                    )
                }
            }
        }

        // Versi, lisensi GPL-3.0, & atribusi
        item(key = "acc_about") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Mupa v${BuildConfig.VERSION_NAME} · GNU GPL-3.0",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Atribusi: AndroidX/Media3, Coil 3, Haze (Apache-2.0), Plus Jakarta Sans (OFL-1.1), NewPipeExtractor (GPL-3.0), Rust crates — selengkapnya di berkas LICENSE & NOTICE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                )
            }
        }
    }
}
