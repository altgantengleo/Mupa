package com.xeno.mupa.ui.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import kotlin.math.roundToInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xeno.mupa.BuildConfig
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.component.MupaFilterChip
import com.xeno.mupa.core.designsystem.component.MupaSecondaryButton
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSegmentedTabs
import com.xeno.mupa.core.designsystem.component.MupaSettingRow
import com.xeno.mupa.core.designsystem.component.MupaStatTile
import com.xeno.mupa.core.designsystem.component.MupaTextButton
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import java.util.Locale

/**
 * Tab Akun ringkas (perbaikan N6 & N7):
 * - Header profil ringkas + ikon gerigi kanan-atas membuka layar Pengaturan terpisah.
 * - 3 StatTile konsisten dengan sumber aktif (Lagu Lokal / Lagu Streaming, Favorit, Artis Pilihan).
 * - Artis Favorit (+ "Ubah").
 * - Satu baris pintasan "Pengaturan" (tanpa menumpuk kontrol pengaturan / atribusi panjang).
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
    onOpenSettings: () -> Unit = {},
    onGlassStyleChange: (GlassStyle) -> Unit = {},
    onOledModeChange: (Boolean) -> Unit = {},
    onAuroraEnabledChange: (Boolean) -> Unit = {},
    onPerformanceModeChange: (Boolean) -> Unit = {},
    onStripArtistNameChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Parameter callback tampilan dipertahankan agar kompatibel dengan pengujian lama
    @Suppress("UNUSED_EXPRESSION")
    listOf(onGlassStyleChange, onOledModeChange, onAuroraEnabledChange, onPerformanceModeChange, onStripArtistNameChange)

    val sourceTracksLabel = if (preferences.activeSource == AudioSourceType.LOCAL) {
        "Lagu Lokal"
    } else {
        "Lagu"
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MupaSpacing.SectionGap),
    ) {
        // Header profil ringkas + tombol gerigi kanan-atas
        item(key = "acc_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
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
                            text = "· Tanpa login · ${if (preferences.activeSource == AudioSourceType.LOCAL) "Lokal" else "Streaming"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("account_settings_gear"),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Buka Pengaturan",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }

        // 3 StatTile satu baris konsisten dengan sumber aktif (N6)
        item(key = "acc_stats") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MupaStatTile(
                    label = sourceTracksLabel,
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

        // Satu baris pintasan menuju layar Pengaturan terpisah (N7)
        item(key = "acc_settings_shortcut") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_settings_row")
                    .clip(MupaRadii.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = MupaPalette.CoralPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Pengaturan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Tampilan, pemutaran, pustaka, cadangan & lisensi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Layar Pengaturan terpisah (Fase 2S — D & N7):
 * 5 grup: Tampilan, Pemutaran, Pustaka, Data, Tentang.
 * Setiap baris: judul 16sp, deskripsi maks 2 baris, kontrol kanan. Hanya kontrol yang benar-benar bekerja.
 */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    connectedFoldersCount: Int,
    audioCacheBytes: Long,
    statusMessage: String?,
    onBack: () -> Unit,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onUltraDynamicChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    onOpenEqualizer: () -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    onClearAudioCache: () -> Unit,
    onImportLocalFolder: () -> Unit,
    onRescanLocalFolders: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearImageAndLyricsCache: () -> Unit,
    onOpenAttribution: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val audioCacheMbText = remember(audioCacheBytes) {
        val mb = audioCacheBytes.toDouble() / (1024.0 * 1024.0)
        String.format(Locale.US, "%.1f MB terpakai", mb)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = MupaSpacing.ScreenHorizontal,
            end = MupaSpacing.ScreenHorizontal,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "settings_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("settings_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali ke Akun",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Pengaturan",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            item(key = "settings_status") {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MupaPalette.CoralPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MupaRadii.ArtworkSmall)
                        .background(MupaPalette.CoralTint14)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // Grup 1: Tampilan
        item(key = "group_appearance") {
            SettingsGroupCard(title = "Tampilan") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Permukaan Melayang",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            "Pilih tampilan pill navigasi & pemutar mini (Crystal & Liquid+ memakai Haze)."
                        } else {
                            "Solid aktif. Efek kaca memerlukan Android 12+."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    SurfaceStylePreviewStrip(
                        selectedStyle = preferences.glassStyle,
                        isOled = preferences.isOledMode,
                    )
                    MupaSegmentedTabs(
                        items = listOf("Solid", "Crystal", "Liquid+"),
                        selectedIndex = preferences.glassStyle.ordinal,
                        onSelect = { idx -> onGlassStyleChange(GlassStyle.entries[idx]) },
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                MupaSettingRow(
                    title = "Ultra Dynamic",
                    subtitle = "Latar & kilau warna mengikuti palet sampul lagu secara halus",
                    checked = preferences.isAuroraEnabled,
                    onCheckedChange = onUltraDynamicChange,
                    enabled = !preferences.isOledMode,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                MupaSettingRow(
                    title = "Mode OLED",
                    subtitle = "Hitam pekat #000000 tanpa kilau latar atau blur",
                    checked = preferences.isOledMode,
                    onCheckedChange = onOledModeChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                MupaSettingRow(
                    title = "Mode Performa",
                    subtitle = "Hemat baterai & GPU: latar statis tanpa blur atau drift",
                    checked = preferences.isPerformanceMode,
                    onCheckedChange = onPerformanceModeChange,
                )
            }
        }

        // Grup 2: Pemutaran
        item(key = "group_playback") {
            SettingsGroupCard(title = "Pemutaran") {
                // Crossfade Equal-Power ala Spotify (0 = Mati, 1..12 detik)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Crossfade",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (preferences.crossfadeSeconds <= 0) {
                                "Mati"
                            } else {
                                "${preferences.crossfadeSeconds} detik"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = if (preferences.crossfadeSeconds > 0) {
                                MupaPalette.CoralPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    Text(
                        text = "Perpaduan halus equal-power antar lagu tanpa jeda (0–12 dtk).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                    Slider(
                        value = preferences.crossfadeSeconds.toFloat(),
                        onValueChange = { onCrossfadeSecondsChange(it.roundToInt().coerceIn(0, 12)) },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = MupaPalette.CoralPrimary,
                            activeTrackColor = MupaPalette.CoralPrimary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            activeTickColor = MupaPalette.LogoCharcoal.copy(alpha = 0.45f),
                            inactiveTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.Equalizer,
                    title = "Equalizer",
                    subtitle = "Atur 5-band equalizer dan preset suara",
                    onClick = onOpenEqualizer,
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.DeleteSweep,
                    title = "Cache audio",
                    subtitle = audioCacheMbText,
                    onClick = onClearAudioCache,
                    trailing = {
                        MupaTextButton(
                            text = "Hapus",
                            accent = true,
                            onClick = onClearAudioCache,
                        )
                    },
                )
            }
        }

        // Grup 3: Pustaka
        item(key = "group_library") {
            SettingsGroupCard(title = "Pustaka") {
                MupaSettingRow(
                    title = "Hapus Nama Artis dari Judul",
                    subtitle = "Rapikan format \"Artis - Judul\" tanpa menghilangkan nama artis",
                    checked = preferences.stripArtistName,
                    onCheckedChange = onStripArtistNameChange,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.Folder,
                    title = "Folder musik lokal",
                    subtitle = if (connectedFoldersCount > 0) {
                        "$connectedFoldersCount folder tersambung · Ketuk untuk tambah folder"
                    } else {
                        "Belum ada folder SAF · Ketuk untuk memilih folder musik"
                    },
                    onClick = onImportLocalFolder,
                    trailing = {
                        if (connectedFoldersCount > 0) {
                            MupaTextButton(
                                text = "Pindai",
                                accent = true,
                                onClick = onRescanLocalFolders,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }

        // Grup 4: Data
        item(key = "group_data") {
            SettingsGroupCard(title = "Data") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Cadangkan & Pulihkan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Ekspor atau impor preferensi, artis favorit, dan preset EQ dalam format JSON.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MupaSecondaryButton(
                            text = "Cadangkan",
                            icon = Icons.Rounded.SaveAlt,
                            onClick = onExportBackup,
                            modifier = Modifier.weight(1f),
                        )
                        MupaSecondaryButton(
                            text = "Pulihkan",
                            icon = Icons.Rounded.Restore,
                            onClick = onImportBackup,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.CleaningServices,
                    title = "Hapus cache gambar & lirik",
                    subtitle = "Bersihkan thumbnail sampul, palet UD, dan cache lirik tersimpan",
                    onClick = onClearImageAndLyricsCache,
                    trailing = {
                        MupaTextButton(
                            text = "Bersihkan",
                            accent = true,
                            onClick = onClearImageAndLyricsCache,
                        )
                    },
                )
            }
        }

        // Grup 5: Tentang
        item(key = "group_about") {
            SettingsGroupCard(title = "Tentang") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Mupa v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Lisensi GNU GPL-3.0 · Clean-room Open Source",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                SettingsActionRow(
                    icon = Icons.Rounded.Gavel,
                    title = "Atribusi & Lisensi",
                    subtitle = "Lihat daftar pustaka sumber terbuka & teks NOTICE",
                    onClick = onOpenAttribution,
                    modifier = Modifier.testTag("settings_attribution_row"),
                    trailing = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
        }
    }
}

/**
 * Layar Atribusi & Lisensi terpisah membaca teks penuh dari assets/NOTICE.txt (Fase 2S — D).
 */
@Composable
fun AttributionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val noticeText = remember(context) {
        runCatching {
            context.assets.open("NOTICE.txt").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrElse {
            "Mupa — Open-Source Android Music Player (GNU GPL-3.0).\n" +
                "Pustaka: AndroidX/Media3 (Apache-2.0), Kotlin & Coroutines (Apache-2.0), " +
                "Hilt (Apache-2.0), OkHttp & Coil 3 (Apache-2.0), Haze (Apache-2.0), " +
                "Plus Jakarta Sans (OFL-1.1), NewPipeExtractor (GPL-3.0), Rust crates (MIT/Apache-2.0/MPL-2.0)."
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(
            start = MupaSpacing.ScreenHorizontal,
            end = MupaSpacing.ScreenHorizontal,
            top = 8.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "attr_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("attribution_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali ke Pengaturan",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Atribusi & Lisensi",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        item(key = "attr_body") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MupaRadii.Card)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
                    .padding(16.dp),
            ) {
                Text(
                    text = noticeText.trim(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MupaPalette.CoralPrimary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MupaRadii.Card)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}

/** Pratinjau mini visual gaya permukaan (Solid / Crystal / Liquid+) untuk membantu pengguna memilih. */
@Composable
private fun SurfaceStylePreviewStrip(
    selectedStyle: GlassStyle,
    isOled: Boolean,
) {
    val pillBg = when {
        isOled -> Color(0xFF0A0A0A)
        selectedStyle == GlassStyle.SOLID -> MupaPalette.Surface2
        selectedStyle == GlassStyle.CRYSTAL -> MupaPalette.Surface2.copy(alpha = 0.80f)
        else -> MupaPalette.Surface2.copy(alpha = 0.74f)
    }
    val pillBorder = when {
        isOled -> Color.White.copy(alpha = 0.14f)
        selectedStyle == GlassStyle.LIQUID_PLUS -> Color.White.copy(alpha = 0.24f)
        selectedStyle == GlassStyle.CRYSTAL -> Color.White.copy(alpha = 0.16f)
        else -> MupaPalette.Outline
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isOled) {
                    Brush.horizontalGradient(listOf(Color.Black, Color(0xFF101010)))
                } else {
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1A293C),
                            Color(0xFF2B2220),
                            Color(0xFF1F2A26),
                        ),
                    )
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .height(34.dp)
                .clip(MupaRadii.Pill)
                .background(pillBg)
                .border(1.dp, pillBorder, MupaRadii.Pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.CoralPrimary),
                )
                Text(
                    text = when (selectedStyle) {
                        GlassStyle.SOLID -> "Pratinjau Solid"
                        GlassStyle.CRYSTAL -> "Pratinjau Crystal"
                        GlassStyle.LIQUID_PLUS -> "Pratinjau Liquid+"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MupaPalette.TextPrimary,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MupaPalette.TextSecondary.copy(alpha = 0.45f)),
            )
        }
    }
}
