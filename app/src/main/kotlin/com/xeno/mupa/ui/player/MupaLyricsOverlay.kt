package com.xeno.mupa.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
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
 * Panel Lirik inline di area cover Now Playing (kontrol tetap terlihat di bawah).
 * Menangani state: memuat (skeleton), tidak ada lirik (pesan singkat), error + coba lagi, plain, & sinkron.
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
                    )
                }

                else -> {
                    val fadeBg = MaterialTheme.colorScheme.background
                    Box(modifier = Modifier.fillMaxSize()) {
                        MupaSyncedLyricsList(
                            lines = playerState.lyrics,
                            isPlain = playerState.lyricsIsPlain,
                            positionProvider = positionProvider,
                            offsetMs = playerState.lyricsOffsetMs,
                            isCinemaScale = false,
                            onSeekTo = onSeekTo,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Gradien halus tepi atas & bawah agar baris tidak terpotong kasar (tanpa Offscreen layer)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(24.dp)
                                .background(Brush.verticalGradient(listOf(fadeBg.copy(alpha = 0.85f), Color.Transparent))),
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(32.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, fadeBg.copy(alpha = 0.96f)))),
                        )
                    }
                }
            }
        }

        // Kontrol offset ringkas (hanya saat lirik tersinkron tersedia)
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAdjustOffset(500L) }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "Percepat lirik 0,5 detik",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/**
 * Daftar lirik tanpa rekomposisi seluruh LazyColumn per frame:
 * - Ticker ringan 45ms hanya memperbarui state internal lirik (activeIndex + draw-phase wipeProgress).
 * - Tinggi item TETAP (skala 1.0 vs 0.94 hanya lewat graphicsLayer, tidak mengubah layout).
 * - Baris aktif alpha 1.0, baris lain alpha 0.38; tanpa blur per baris.
 * - Auto-scroll memusatkan baris aktif di ~40% tinggi layar (tween 350ms); scroll manual jeda 3 detik.
 * - Wipe warna adaptif OKLCH (L >= 0.87, fallback krem) digambar di fase draw hanya pada baris aktif.
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
    val appearance = LocalMupaAppearance.current
    val baseAccent = appearance.effectiveCoverAccent.primary
    val activeWipeColor = remember(baseAccent, appearance.isOledMode) {
        if (appearance.isOledMode) {
            MupaPalette.LogoCream
        } else {
            LyricsSyncCalculator.ensureReadableWipeColor(baseAccent)
        }
    }

    val listState = rememberLazyListState()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    // Ticker terisolasi (45ms): hanya activeIndex memicu update baris; wipeProgress dibaca di fase draw saja
    var activeIndex by remember(lines) { mutableIntStateOf(-1) }
    val wipeProgressHolder = remember(lines) { mutableStateOf(0f) }

    LaunchedEffect(lines, offsetMs, isPlain) {
        if (isPlain || lines.isEmpty()) {
            activeIndex = -1
            wipeProgressHolder.value = 0f
            return@LaunchedEffect
        }
        while (true) {
            val pos = positionProvider()
            val computed = LyricsSyncCalculator.compute(lines, pos, offsetMs)
            if (computed.index != activeIndex) {
                activeIndex = computed.index
            }
            wipeProgressHolder.value = computed.lineProgress
            delay(45L)
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            lastUserScrollMs = System.currentTimeMillis()
        }
    }

    // Auto-scroll memusatkan baris aktif di ~40% tinggi viewport dengan tween 350ms
    LaunchedEffect(activeIndex, viewportHeightPx) {
        if (isPlain || activeIndex < 0 || viewportHeightPx <= 0) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastUserScrollMs < 3_000L) return@LaunchedEffect

        val targetOffset = (viewportHeightPx * 0.40f).toInt()
        val visibleItem = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
        if (visibleItem != null) {
            val delta = (visibleItem.offset - targetOffset).toFloat()
            listState.animateScrollBy(
                value = delta,
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationLyricsScrollMs),
            )
        } else {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = -targetOffset)
        }
    }

    val textSize = if (isCinemaScale) 26.sp else 20.sp
    val lineHeight = if (isCinemaScale) 34.sp else 28.sp
    val textAlign = if (isCinemaScale) TextAlign.Center else TextAlign.Start

    LazyColumn(
        state = listState,
        modifier = modifier.onSizeChanged { viewportHeightPx = it.height },
        contentPadding = PaddingValues(
            horizontal = if (isCinemaScale) 28.dp else 4.dp,
            vertical = if (isCinemaScale) 96.dp else 48.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(if (isCinemaScale) 22.dp else 16.dp),
    ) {
        itemsIndexed(
            items = lines,
            key = { idx, line -> "${idx}_${line.timestampMs}" },
        ) { index, line ->
            val isActive = !isPlain && index == activeIndex
            val targetAlpha = when {
                isPlain -> 0.88f
                isActive -> 1.0f
                else -> 0.38f
            }
            val targetScale = when {
                isPlain -> 1.0f
                isActive -> 1.0f
                else -> 0.94f
            }
            val animAlpha by animateFloatAsState(
                targetValue = targetAlpha,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "lyrAlpha_$index",
            )
            val animScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "lyrScale_$index",
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isActive) Modifier.testTag("mupa_lyrics_active_line") else Modifier)
                    .clip(MupaRadii.ArtworkSmall)
                    .clickable(enabled = !isPlain) { onSeekTo(line.timestampMs) }
                    .graphicsLayer {
                        alpha = animAlpha
                        scaleX = animScale
                        scaleY = animScale
                        transformOrigin = if (isCinemaScale) TransformOrigin.Center else TransformOrigin(0f, 0.5f)
                        if (isActive) {
                            // Offscreen hanya pada SATU baris aktif kecil untuk BlendMode.SrcIn wipe
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                    }
                    .drawWithContent {
                        drawContent()
                        if (isActive) {
                            val p = wipeProgressHolder.value.coerceIn(0f, 1f)
                            val feather = 0.24f
                            val sweep = p * (1f + feather)
                            val stopA = (sweep - feather).coerceIn(0f, 1f)
                            val stopB = sweep.coerceIn(0f, 1f)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colorStops = arrayOf(
                                        0f to activeWipeColor,
                                        stopA to activeWipeColor,
                                        stopB to MupaPalette.LogoCream.copy(alpha = 0.50f),
                                        1f to MupaPalette.LogoCream.copy(alpha = 0.50f),
                                    ),
                                ),
                                blendMode = BlendMode.SrcIn,
                            )
                        }
                    }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            ) {
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = textSize,
                        lineHeight = lineHeight,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MupaPalette.LogoCream,
                    textAlign = textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
