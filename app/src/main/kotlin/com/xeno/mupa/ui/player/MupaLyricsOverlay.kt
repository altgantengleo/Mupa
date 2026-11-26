package com.xeno.mupa.ui.player

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.LyricsLine
import java.util.Locale

/**
 * Layar Lirik dengan wipe warna adaptif (mengikuti docs/AUDIT.md §7.7):
 * - Warna aktif diangkat luminansinya via LyricsSyncCalculator.ensureReadableWipeColor()
 *   sehingga tetap terbaca jelas pada cover art gelap.
 * - Progres sapuan horizontal lembut (28% feather) pada baris aktif.
 * - Ketuk baris untuk seek langsung; kontrol offset ±0.5 dtk.
 */
@Composable
fun MupaLyricsOverlay(
    trackTitle: String,
    artistName: String,
    lines: List<LyricsLine>,
    positionMs: Long,
    offsetMs: Long,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalMupaAppearance.current
    val baseAccent = appearance.effectiveCoverAccent.primary
    val sungColor = remember(baseAccent, appearance.isOledMode) {
        if (appearance.isOledMode) {
            MupaPalette.LogoCream
        } else {
            LyricsSyncCalculator.ensureReadableWipeColor(baseAccent)
        }
    }
    val unsungColor = Color.White.copy(alpha = 0.44f)

    val activeState = remember(lines, positionMs, offsetMs) {
        LyricsSyncCalculator.compute(lines = lines, positionMs = positionMs, offsetMs = offsetMs)
    }

    val listState = rememberLazyListState()
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            lastUserScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(activeState.index) {
        val now = System.currentTimeMillis()
        if (activeState.index >= 0 && now - lastUserScrollMs > 3_000L) {
            listState.animateScrollToItem(
                index = activeState.index,
                scrollOffset = -180,
            )
        }
    }

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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trackTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Tutup lirik",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (lines.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Lirik belum tersedia untuk lagu ini.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 48.dp,
                    bottom = 120.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                itemsIndexed(
                    items = lines,
                    key = { index, line -> "${index}_${line.timestampMs}" },
                ) { index, line ->
                    val isActive = index == activeState.index
                    val isPast = activeState.index >= 0 && index < activeState.index
                    val lineAlpha = when {
                        isActive -> 1f
                        isPast -> 0.56f
                        else -> 0.42f
                    }
                    val lineScale = if (isActive) 1.0f else 0.96f

                    val wipeProgress = activeState.lineProgress
                    val feather = 0.22f
                    val sweep = wipeProgress * (1f + feather)
                    val stopA = (sweep - feather).coerceIn(0f, 1f)
                    val stopB = sweep.coerceIn(0f, 1f)

                    val textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        lineHeight = 32.sp,
                        brush = if (isActive) {
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to sungColor,
                                    stopA to sungColor,
                                    stopB to unsungColor,
                                    1f to unsungColor,
                                ),
                            )
                        } else {
                            null
                        },
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MupaRadii.Chip)
                            .clickable { onSeekTo(line.timestampMs) }
                            .graphicsLayer {
                                alpha = lineAlpha
                                scaleX = lineScale
                                scaleY = lineScale
                            }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    ) {
                        Text(
                            text = line.text,
                            style = textStyle,
                            color = if (isActive) Color.Unspecified else if (isPast) sungColor.copy(alpha = 0.72f) else unsungColor,
                        )
                    }
                }
            }
        }

        // Bilah sinkronisasi offset lirik ringkas
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = { onAdjustOffset(-500L) }) {
                Icon(
                    imageVector = Icons.Rounded.Remove,
                    contentDescription = "Perlambat lirik 0,5 detik",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onResetOffset) {
                val sec = offsetMs / 1000.0
                val label = String.format(Locale("id", "ID"), "Offset %+.1f dtk", sec)
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = { onAdjustOffset(500L) }) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Percepat lirik 0,5 detik",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Preview(name = "Mupa Lyrics Adaptive Wipe", showBackground = true)
@Composable
private fun MupaLyricsOverlayPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Surface {
            MupaLyricsOverlay(
                trackTitle = "Senja di Kota Tua",
                artistName = "Hindia",
                lines = listOf(
                    LyricsLine(0L, 4_000L, "Langit jingga mulai turun di ujung jalan"),
                    LyricsLine(4_000L, 9_000L, "Langkah pelan menyusuri sisa kenangan"),
                    LyricsLine(9_000L, 14_000L, "Lampu kota menyala satu per satu"),
                ),
                positionMs = 6_500L,
                offsetMs = 0L,
                onSeekTo = {},
                onAdjustOffset = {},
                onResetOffset = {},
                onClose = {},
            )
        }
    }
}
