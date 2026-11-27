package com.xeno.mupa.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.GlassStyle

@Preview(name = "Mupa Brand & Glass — Ember Editorial", showBackground = true)
@Composable
private fun MupaDesignSystemPreview() {
    MupaTheme(
        appearance = MupaAppearance(
            glassStyle = GlassStyle.LIQUID_PLUS,
            isOledMode = false,
            isUltraDynamicEnabled = true,
        ),
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth(),
        ) {
            UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MupaBrandMark()
                MupaGlassSurface(
                    shape = MupaRadii.Card,
                    styleOverride = GlassStyle.CRYSTAL,
                ) {
                    Text(
                        text = "Gaya Kaca Crystal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                MupaGlassSurface(
                    shape = MupaRadii.Card,
                    styleOverride = GlassStyle.LIQUID_PLUS,
                ) {
                    Text(
                        text = "Gaya Kaca Liquid+",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {}) {
                        Text(text = "Mulai Dengarkan")
                    }
                }
            }
        }
    }
}

@Preview(name = "Mupa — Mode OLED", showBackground = true)
@Composable
private fun MupaOledPreview() {
    MupaTheme(
        appearance = MupaAppearance(
            glassStyle = GlassStyle.LIQUID_PLUS,
            isOledMode = true,
            isUltraDynamicEnabled = true,
        ),
    ) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MupaBrandMark()
                MupaGlassSurface(shape = MupaRadii.Card) {
                    Text(
                        text = "Mode OLED (#000000, tanpa blur/aurora)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
