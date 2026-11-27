package com.xeno.mupa.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaSkeletonBox
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.model.LyricsLine
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Panel Lirik inline transparan di atas full-bleed blurred cover Now Playing (tanpa garis hitam).
 */
@Composable
fun MupaInlineLyricsPanel(
    playerState: MupaPlayerUiState,
    positionProvider: () -> Long,
    onSeekTo: (Long) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            when {
                playerState.lyricsLoading -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        repeat(5) { idx ->
                            val frac = listOf(0.85f, 0.65f, 0.92f, 0.58f, 0.78f)[idx]
                            MupaSkeletonBox(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(22.dp),
                                shape = CircleShape,
                            )
                        }
                    }
                }

                playerState.lyricsError != null -> {
                    MupaErrorState(
                        message = playerState.lyricsError,
                        onRetry = onRetry,
                    )
                }

                playerState.lyrics.isEmpty() -> {
                    MupaEmptyState(
                        icon = Icons.Rounded.Lyrics,
                        title = "Lirik tidak tersedia",
                        subtitle = "Belum ada lirik yang cocok untuk lagu ini.",
                        actionLabel = "Cari ulang",
                        onAction = onRetry,
                    )
                }

                else -> {
                    MupaSyncedLyricsList(
                        lines = playerState.lyrics,
                        isPlain = playerState.lyricsIsPlain,
                        positionProvider = positionProvider,
                        offsetMs = playerState.lyricsOffsetMs,
                        isCinemaScale = false,
                        onSeekTo = onSeekTo,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        if (!playerState.lyricsIsPlain && playerState.lyrics.isNotEmpty() && !playerState.lyricsLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { onAdjustOffset(-500L) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = "Perlambat lirik 0,5 detik",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                TextButton(onClick = onResetOffset) {
                    val sec = playerState.lyricsOffsetMs / 1000.0
                    val label = buildString {
                        append(String.format(Locale("id", "ID"), "Offset %+.1f dtk", sec))
                        playerState.lyricsProviderLabel?.let { append(" · $it") }
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.78f),
                    )
                }
                IconButton(onClick = { onAdjustOffset(500L) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Percepat lirik 0,5 detik",
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Daftar lirik tersinkron halus (bebas bug garis potong multi-line & bebas rekomposisi layar utama):
 * - Baris aktif menyala putih bersih #FFFFFF (bold, skala 1.02f), baris lampau 0.40f, baris depan 0.54f.
 * - Tanpa Offscreen layer / BlendMode.SrcIn yang memotong baris wrap.
 * - Auto-scroll halus memusatkan baris aktif di ~35% viewport; jeda 3 detik bila user scroll manual.
 */
@Composable
fun MupaSyncedLyricsList(
    lines: List<LyricsLine>,
    isPlain: Boolean,
    positionProvider: () -> Long,
    offsetMs: Long,
    isCinemaScale: Boolean,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    var activeIndex by remember(lines) { mutableIntStateOf(if (isPlain) -1 else 0) }

    LaunchedEffect(lines, offsetMs, isPlain) {
        if (isPlain || lines.isEmpty()) {
            activeIndex = -1
            return@LaunchedEffect
        }
        while (true) {
            val pos = positionProvider()
            val computed = LyricsSyncCalculator.compute(lines, pos, offsetMs)
            if (computed.index != activeIndex) {
                activeIndex = computed.index
            }
            delay(80L)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            lastUserScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(activeIndex, viewportHeightPx) {
        if (isPlain || activeIndex < 0 || viewportHeightPx <= 0) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScrollMs < 3_000L) return@LaunchedEffect
        val targetOffset = (viewportHeightPx * 0.34f).toInt()
        listState.animateScrollToItem(index = activeIndex, scrollOffset = -targetOffset)
    }

    val appearance = LocalMupaAppearance.current
    val activeAccentColor = if (appearance.isOledMode) {
        MupaPalette.TextPrimary
    } else {
        appearance.effectiveUltraDynamicPalette.onAccent
    }
    val textSize = if (isCinemaScale) 27.sp else 22.sp
    val lineHeight = if (isCinemaScale) 36.sp else 30.sp
    val textAlign = if (isCinemaScale) TextAlign.Center else TextAlign.Start

    LazyColumn(
        state = listState,
        modifier = modifier.onSizeChanged { viewportHeightPx = it.height },
        contentPadding = PaddingValues(
            horizontal = if (isCinemaScale) 24.dp else 4.dp,
            vertical = if (isCinemaScale) 72.dp else 36.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (isCinemaScale) 24.dp else 18.dp),
    ) {
        itemsIndexed(
            items = lines,
            key = { idx, line -> "${idx}_${line.timestampMs}" },
        ) { index, line ->
            val isActive = !isPlain && index == activeIndex
            val isPast = !isPlain && activeIndex >= 0 && index < activeIndex

            val targetColor = when {
                isPlain -> Color.White.copy(alpha = 0.90f)
                isActive -> activeAccentColor
                isPast -> Color.White.copy(alpha = 0.38f)
                else -> Color.White.copy(alpha = 0.52f)
            }
            val targetScale = when {
                isPlain -> 1.0f
                isActive -> 1.02f
                else -> 0.96f
            }

            val animColor by animateColorAsState(
                targetValue = targetColor,
                animationSpec = MupaMotion.standardTween(220),
                label = "lyrColor_$index",
            )
            val animScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = MupaMotion.standardTween(220),
                label = "lyrScale_$index",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isActive) Modifier.testTag("mupa_lyrics_active_line") else Modifier)
                    .clip(MupaRadii.ArtworkSmall)
                    .clickable(enabled = !isPlain) { onSeekTo(line.timestampMs) }
                    .graphicsLayer {
                        scaleX = animScale
                        scaleY = animScale
                        transformOrigin = if (isCinemaScale) TransformOrigin.Center else TransformOrigin(0f, 0.5f)
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = textSize,
                        lineHeight = lineHeight,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = animColor,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
