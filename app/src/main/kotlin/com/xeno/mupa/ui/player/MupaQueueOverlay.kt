package com.xeno.mupa.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaTextButton
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.TABULAR_NUMS_FEATURE
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules

/**
 * Sheet Antrean Putar Mupa Calm:
 * - Lagu sekarang tersemat (pinned) di bagian atas.
 * - Daftar "Berikutnya" dengan total durasi (tabular) dan tombol "Bersihkan".
 * - Reorder via long-press drag (implementasi clean-room tanpa dependensi ekstra) & geser untuk hapus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MupaQueueOverlay(
    queue: List<Track>,
    currentIndex: Int,
    stripArtistName: Boolean = true,
    onSelectIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onRemoveItem: (Int) -> Unit,
    onClearUpcoming: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalMupaAppearance.current
    val backgroundColor = if (appearance.isOledMode) MupaPalette.OledBackground else MupaPalette.Background
    val currentTrack = queue.getOrNull(currentIndex)
    val upcoming = if (currentIndex in queue.indices) {
        queue.mapIndexedNotNull { idx, t -> if (idx != currentIndex) idx to t else null }
    } else {
        queue.mapIndexed { idx, t -> idx to t }
    }
    val totalDurationMs = queue.sumOf { it.durationMs.coerceAtLeast(0L) }
    val totalDurationLabel = TrackDisplayRules.formatDuration(totalDurationMs)

    val rowHeightPx = with(LocalDensity.current) { 68.dp.toPx() }
    var draggingQueueIndex by remember { mutableIntStateOf(-1) }
    var dragAccumPx by remember { mutableFloatStateOf(0f) }

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
                    text = "Antrean",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val summary = buildString {
                    append("${queue.size} lagu")
                    if (!totalDurationLabel.isNullOrBlank()) {
                        append(" · $totalDurationLabel")
                    }
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFeatureSettings = TABULAR_NUMS_FEATURE,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup antrean",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Lagu sekarang tersemat di atas
        if (currentTrack != null) {
            val disp = TrackDisplayRules.formatTrack(currentTrack, stripArtistName)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Sedang diputar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MupaRadii.ArtworkMedium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MupaPalette.CoralPrimary.copy(alpha = 0.35f), MupaRadii.ArtworkMedium)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MupaArtworkBox(
                        artworkUrl = currentTrack.artworkUrl,
                        contentDescription = disp.title,
                        size = 48.dp,
                        shape = MupaRadii.ArtworkSmall,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = disp.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MupaPalette.CoralPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = disp.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    disp.durationText?.let { dur ->
                        Text(
                            text = dur,
                            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMS_FEATURE),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Header Berikutnya + tombol Bersihkan
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Berikutnya (${upcoming.size})",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (upcoming.isNotEmpty()) {
                MupaTextButton(
                    text = "Bersihkan",
                    onClick = onClearUpcoming,
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        if (upcoming.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Tidak ada lagu berikutnya di antrean.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(
                    items = upcoming,
                    key = { _, (qIdx, track) -> "${track.id}_$qIdx" },
                ) { _, (queueIdx, track) ->
                    val disp = TrackDisplayRules.formatTrack(track, stripArtistName)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                onRemoveItem(queueIdx)
                                true
                            } else {
                                false
                            }
                        },
                    )
                    val isDraggingThis = draggingQueueIndex == queueIdx

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MupaRadii.ArtworkSmall)
                                    .background(MupaPalette.ErrorContainer)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Hapus dari antrean",
                                    tint = MupaPalette.OnErrorContainer,
                                )
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    if (isDraggingThis) {
                                        translationY = dragAccumPx
                                        scaleX = 1.02f
                                        scaleY = 1.02f
                                    }
                                }
                                .clip(MupaRadii.ArtworkSmall)
                                .background(
                                    if (isDraggingThis) {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    } else {
                                        backgroundColor
                                    },
                                )
                                .clickable { onSelectIndex(queueIdx) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MupaArtworkBox(
                                artworkUrl = track.artworkUrl,
                                contentDescription = disp.title,
                                size = 44.dp,
                                shape = MupaRadii.ArtworkSmall,
                                iconSize = 18.dp,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = disp.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = disp.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            disp.durationText?.let { dur ->
                                Text(
                                    text = dur,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMS_FEATURE),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            // Grip long-press untuk drag reorder
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .pointerInput(queueIdx, queue.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingQueueIndex = queueIdx
                                                dragAccumPx = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragAccumPx += dragAmount.y
                                                if (dragAccumPx > rowHeightPx * 0.75f && draggingQueueIndex < queue.lastIndex) {
                                                    val from = draggingQueueIndex
                                                    val to = from + 1
                                                    onMoveItem(from, to)
                                                    draggingQueueIndex = to
                                                    dragAccumPx -= rowHeightPx
                                                } else if (dragAccumPx < -rowHeightPx * 0.75f && draggingQueueIndex > 0) {
                                                    val from = draggingQueueIndex
                                                    val to = from - 1
                                                    onMoveItem(from, to)
                                                    draggingQueueIndex = to
                                                    dragAccumPx += rowHeightPx
                                                }
                                            },
                                            onDragEnd = {
                                                draggingQueueIndex = -1
                                                dragAccumPx = 0f
                                            },
                                            onDragCancel = {
                                                draggingQueueIndex = -1
                                                dragAccumPx = 0f
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DragHandle,
                                    contentDescription = "Tahan dan seret untuk ubah urutan ${disp.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
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
                    Track(id = "1", title = "blue", artistName = "yung kai", durationMs = 214_000L),
                    Track(id = "2", title = "Bertaut", artistName = "Nadin Amizah", durationMs = 256_000L),
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
