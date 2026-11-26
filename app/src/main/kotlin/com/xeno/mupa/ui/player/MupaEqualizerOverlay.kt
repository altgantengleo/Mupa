package com.xeno.mupa.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.playback.eq.EqPreset
import com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor
import kotlin.math.roundToInt

/**
 * Overlay Equalizer 5-Band (60 Hz, 250 Hz, 1 kHz, 4 kHz, 12 kHz; -12..+12 dB)
 * dengan 5 preset standar (Flat, Vocal, Bass, Treble, Warm) + Kustom.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MupaEqualizerOverlay(
    activePreset: EqPreset,
    bandGainsDb: List<Float>,
    onSelectPreset: (EqPreset) -> Unit,
    onBandGainChange: (Int, Float) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalMupaAppearance.current
    val bg = if (appearance.isOledMode) MupaPalette.OledBackground else MupaPalette.Background

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Equalizer 5-Band",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Preset aktif: ${activePreset.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup Equalizer",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            EqPreset.entries.filter { it != EqPreset.CUSTOM }.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MupaPalette.CoralPrimary,
                        selectedLabelColor = MupaPalette.LogoCharcoal,
                    ),
                )
            }
        }

        FiveBandEqualizerProcessor.BAND_LABELS.forEachIndexed { index, label ->
            val gain = bandGainsDb.getOrElse(index) { 0f }
            val rounded = gain.roundToInt()
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${if (rounded > 0) "+" else ""}$rounded dB",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (rounded != 0) MupaPalette.CoralBright else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Slider(
                    value = gain,
                    onValueChange = { value -> onBandGainChange(index, value.roundToInt().toFloat()) },
                    valueRange = FiveBandEqualizerProcessor.MIN_GAIN_DB..FiveBandEqualizerProcessor.MAX_GAIN_DB,
                    colors = SliderDefaults.colors(
                        thumbColor = MupaPalette.CoralPrimary,
                        activeTrackColor = MupaPalette.CoralPrimary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset ke Flat (0 dB)")
        }
    }
}

@Preview(name = "Mupa 5-Band EQ", showBackground = true)
@Composable
private fun MupaEqualizerPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface {
            MupaEqualizerOverlay(
                activePreset = EqPreset.BASS,
                bandGainsDb = EqPreset.BASS.gainsDb,
                onSelectPreset = {},
                onBandGainChange = { _, _ -> },
                onReset = {},
                onClose = {},
            )
        }
    }
}
