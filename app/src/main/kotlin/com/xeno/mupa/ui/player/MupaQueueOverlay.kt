package com.xeno.mupa.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Track

@Composable
fun MupaQueueOverlay(
    queue: List<Track>,
    currentIndex: Int,
    onSelectIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalMupaAppearance.current
    val backgroundColor = if (appearance.isOledMode) {
        MupaPalette.OledBackground
    } else {
        MupaPalette.Background
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Antrean Putar",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${queue.size} lagu dalam antrean",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup antrean",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Antrean masih kosong.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(
                    items = queue,
                    key = { index, track -> "${track.id}_$index" },
                ) { index, track ->
                    val isCurrent = index == currentIndex
                    val rowBg = when {
                        isCurrent && appearance.isOledMode -> MupaPalette.OledCardHigh
                        isCurrent -> MupaPalette.CoralPrimary.copy(alpha = 0.16f)
                        appearance.isOledMode -> MupaPalette.OledCard
                        else -> MupaPalette.SurfaceContainer
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MupaRadii.Chip)
                            .background(rowBg)
                            .clickable { onSelectIndex(index) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val swatch = track.accentColorArgb?.let { Color(it.toInt()) } ?: MupaPalette.CoralPrimary
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MupaRadii.ArtworkSmall)
                                .background(swatch.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Rounded.Equalizer,
                                    contentDescription = "Sedang diputar",
                                    tint = MupaPalette.LogoCharcoal,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MupaPalette.LogoCharcoal,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCurrent && !appearance.isOledMode) {
                                    MupaPalette.CoralBright
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artistName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (index > 0) {
                                IconButton(
                                    onClick = { onMoveItem(index, index - 1) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowUp,
                                        contentDescription = "Geser naik ${track.title}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index < queue.lastIndex) {
                                IconButton(
                                    onClick = { onMoveItem(index, index + 1) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.KeyboardArrowDown,
                                        contentDescription = "Geser turun ${track.title}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (queue.size > 1) {
                                IconButton(
                                    onClick = { onRemoveItem(index) },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DeleteOutline,
                                        contentDescription = "Hapus ${track.title} dari antrean",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "Mupa Queue Overlay", showBackground = true)
@Composable
private fun MupaQueueOverlayPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface {
            MupaQueueOverlay(
                queue = listOf(
                    Track(id = "1", title = "Senja di Kota Tua", artistName = "Hindia", accentColorArgb = 0xFFFF5A3C),
                    Track(id = "2", title = "Lampu Lalu Lintas", artistName = "Nadin Amizah", accentColorArgb = 0xFF4ECDC4),
                ),
                currentIndex = 0,
                onSelectIndex = {},
                onMoveItem = { _, _ -> },
                onRemoveItem = {},
                onClose = {},
            )
        }
    }
}
