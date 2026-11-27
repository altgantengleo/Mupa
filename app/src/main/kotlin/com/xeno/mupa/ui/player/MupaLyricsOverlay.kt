package com.xeno.mupa.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
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
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
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
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Panel Lirik inline premium di atas latar Ultra Dynamic Now Playing:
 * - Sorotan kata-per-kata (Enhanced LRC nyata + aliran progresif halus untuk LRC standar)
 *   menggunakan SpanStyle sehingga aman untuk baris multi-line wrap tanpa garis potong.
 * - Masking gradasi halus di tepi atas & bawah + titik jeda instrumental animatif.
 * - Kontrol sinkronisasi presisi ±0,25 detik + tombol cepat kembali ke baris aktif saat scroll manual.
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
    }
    @Suppress("UNUSED_EXPRESSION")
    listOf(onAdjustOffset, onResetOffset)
}

/**
 * Daftar lirik tersinkron bergaya Apple Music / Spotify:
 * - Ticker 33ms (~30fps) memberi sorotan kata-per-kata halus pada baris aktif via SpanStyle
 *   (mendukung Enhanced LRC `<mm:ss.xx>kata` maupun estimasi kata halus untuk LRC standar).
 * - Hierarki kedalaman: baris aktif terang + glow lembut (scale 1.03f), baris berikutnya 0.62f,
 *   baris jauh 0.36f, baris lampau 0.25f.
 * - Masking gradasi tepi atas/bawah + indikator titik instrumental saat jeda panjang.
 * - Auto-scroll memusatkan baris aktif di ~32% viewport; jeda 3,5 detik saat user scroll manual
 *   dengan tombol cepat "Ke baris aktif".
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
    val scope = rememberCoroutineScope()
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }
    var userScrolledAway by remember { mutableStateOf(false) }

    var activeIndex by remember(lines) { mutableIntStateOf(if (isPlain) -1 else -1) }
    var activeLineProgress by remember(lines) { mutableFloatStateOf(0f) }
    var liveEffectivePosMs by remember(lines) { mutableLongStateOf(0L) }

    LaunchedEffect(lines, offsetMs, isPlain) {
        if (isPlain || lines.isEmpty()) {
            activeIndex = -1
            activeLineProgress = 0f
            return@LaunchedEffect
        }
        while (true) {
            val pos = positionProvider()
            val eff = (pos + offsetMs).coerceAtLeast(0L)
            liveEffectivePosMs = eff
            val computed = LyricsSyncCalculator.compute(lines, pos, offsetMs)
            if (computed.index != activeIndex) {
                activeIndex = computed.index
            }
            activeLineProgress = computed.lineProgress
            if (userScrolledAway && System.currentTimeMillis() - lastUserScrollMs >= 3_500L) {
                userScrolledAway = false
            }
            delay(33L)
        }
    }

    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isUserDragging) {
        if (isUserDragging) {
            lastUserScrollMs = System.currentTimeMillis()
            userScrolledAway = true
        }
    }

    LaunchedEffect(activeIndex, viewportHeightPx, userScrolledAway) {
        if (isPlain || activeIndex < 0 || viewportHeightPx <= 0 || userScrolledAway) return@LaunchedEffect
        val targetOffset = (viewportHeightPx * 0.32f).toInt()
        listState.animateScrollToItem(index = activeIndex, scrollOffset = -targetOffset)
    }

    val appearance = LocalMupaAppearance.current
    val activeAccentColor = if (appearance.isOledMode) {
        Color.White
    } else {
        LyricsSyncCalculator.ensureReadableWipeColor(appearance.effectiveUltraDynamicPalette.onAccent)
    }
    val textSize = if (isCinemaScale) 28.sp else 23.sp
    val lineHeight = if (isCinemaScale) 38.sp else 32.sp
    val textAlign = if (isCinemaScale) TextAlign.Center else TextAlign.Start

    // Deteksi jeda instrumental di awal lagu sebelum baris pertama masuk
    val firstLineTs = lines.firstOrNull()?.timestampMs ?: 0L
    val isIntroInstrumental = !isPlain && activeIndex < 0 && firstLineTs > 3_500L

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewportHeightPx = it.height }
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    // Masking halus di tepi atas & bawah agar teks meluruh ke latar Ultra Dynamic
                    val fadeHeight = (size.height * 0.12f).coerceIn(24f, 96f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black),
                            startY = 0f,
                            endY = fadeHeight,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startY = size.height - fadeHeight,
                            endY = size.height,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            contentPadding = PaddingValues(
                horizontal = if (isCinemaScale) 24.dp else 6.dp,
                vertical = if (isCinemaScale) 80.dp else 44.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isCinemaScale) 26.dp else 20.dp),
        ) {
            if (isIntroInstrumental) {
                item(key = "lyrics_intro_dots") {
                    InstrumentalPulseDots(
                        tint = activeAccentColor,
                        centered = isCinemaScale,
                    )
                }
            }

            itemsIndexed(
                items = lines,
                key = { idx, line -> "${idx}_${line.timestampMs}" },
            ) { index, line ->
                val isActive = !isPlain && index == activeIndex
                val isNext = !isPlain && activeIndex >= 0 && index == activeIndex + 1
                val isPast = !isPlain && activeIndex >= 0 && index < activeIndex

                val targetAlpha = when {
                    isPlain -> 0.92f
                    isActive -> 1.0f
                    isNext -> 0.62f
                    isPast -> 0.25f
                    else -> 0.36f
                }
                val targetScale = when {
                    isPlain -> 1.0f
                    isActive -> 1.03f
                    isNext -> 0.98f
                    else -> 0.95f
                }

                val animAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = MupaMotion.standardTween(240),
                    label = "lyrAlpha_$index",
                )
                val animScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = MupaMotion.emphasizedTween(260),
                    label = "lyrScale_$index",
                )
                val baseInactiveColor by animateColorAsState(
                    targetValue = Color.White.copy(alpha = animAlpha),
                    animationSpec = MupaMotion.standardTween(220),
                    label = "lyrColor_$index",
                )

                val styledText = if (isActive) {
                    buildProgressiveLyricText(
                        line = line,
                        effectivePosMs = liveEffectivePosMs,
                        lineProgress = activeLineProgress,
                        litColor = activeAccentColor,
                        unlitColor = Color.White.copy(alpha = 0.44f),
                    )
                } else {
                    null
                }

                // Cek jeda instrumental panjang setelah baris aktif (>= 6 detik)
                val nextLineTs = lines.getOrNull(index + 1)?.timestampMs
                val hasLongInterludeAfter = isActive && nextLineTs != null &&
                    (nextLineTs - line.timestampMs) >= 7_500L &&
                    activeLineProgress >= 0.68f

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isActive) Modifier.testTag("mupa_lyrics_active_line") else Modifier)
                            .clip(MupaRadii.ArtworkSmall)
                            .clickable(enabled = !isPlain) {
                                userScrolledAway = false
                                onSeekTo(line.timestampMs)
                            }
                            .graphicsLayer {
                                scaleX = animScale
                                scaleY = animScale
                                transformOrigin = if (isCinemaScale) TransformOrigin.Center else TransformOrigin(0f, 0.5f)
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    ) {
                        if (styledText != null) {
                            Text(
                                text = styledText,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = textSize,
                                    lineHeight = lineHeight,
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = Shadow(
                                        color = activeAccentColor.copy(alpha = 0.28f),
                                        offset = Offset(0f, 2f),
                                        blurRadius = 12f,
                                    ),
                                ),
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = textSize,
                                    lineHeight = lineHeight,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = baseInactiveColor,
                                textAlign = textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }

                    if (hasLongInterludeAfter) {
                        InstrumentalPulseDots(
                            tint = activeAccentColor.copy(alpha = 0.85f),
                            centered = isCinemaScale,
                        )
                    }
                }
            }
        }

        // Tombol ringkas kembali ke baris aktif bila user scroll manual menjauh
        AnimatedVisibility(
            visible = userScrolledAway && !isPlain && activeIndex >= 0,
            enter = fadeIn(MupaMotion.standardTween(180)),
            exit = fadeOut(MupaMotion.standardTween(160)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 6.dp, bottom = 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MupaPalette.Surface2.copy(alpha = 0.90f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                    .clickable {
                        userScrolledAway = false
                        val targetOffset = (viewportHeightPx * 0.32f).toInt()
                        scope.launch {
                            listState.animateScrollToItem(index = activeIndex.coerceAtLeast(0), scrollOffset = -targetOffset)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.MyLocation,
                    contentDescription = "Kembali ke baris aktif",
                    tint = MupaPalette.CoralPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Ikuti",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Membangun teks lirik dengan sorotan kata-per-kata (Enhanced LRC nyata atau aliran kata halus).
 * Menggunakan AnnotatedString SpanStyle sehingga 100% bebas artefak clip pada baris wrap panjang.
 */
private fun buildProgressiveLyricText(
    line: LyricsLine,
    effectivePosMs: Long,
    lineProgress: Float,
    litColor: Color,
    unlitColor: Color,
) = buildAnnotatedString {
    val words = line.wordTimings
    if (words.isNotEmpty()) {
        words.forEachIndexed { idx, w ->
            val wordProgress = when {
                effectivePosMs >= w.endMs -> 1f
                effectivePosMs <= w.startMs -> 0f
                else -> ((effectivePosMs - w.startMs).toFloat() / (w.endMs - w.startMs).coerceAtLeast(1L).toFloat())
                    .coerceIn(0f, 1f)
            }
            val color = androidx.compose.ui.graphics.lerp(unlitColor, litColor, wordProgress)
            withStyle(SpanStyle(color = color)) {
                append(w.text)
                if (idx < words.lastIndex) append(" ")
            }
        }
        return@buildAnnotatedString
    }

    // LRC standar (per baris): distribusikan aliran cahaya halus antar kata berdasarkan panjang huruf
    val tokens = line.text.split(" ").filter { it.isNotEmpty() }
    if (tokens.size <= 1) {
        withStyle(SpanStyle(color = litColor)) {
            append(line.text)
        }
        return@buildAnnotatedString
    }

    val totalWeight = tokens.sumOf { (it.length + 1) }.coerceAtLeast(1).toFloat()
    // Sedikit percepat progres efektif (1.12x) agar kata terakhir selesai menyala sebelum baris ganti
    val boostedProgress = (lineProgress * 1.12f).coerceIn(0f, 1f)
    var cumulative = 0f

    tokens.forEachIndexed { idx, token ->
        val weight = (token.length + 1).toFloat()
        val startFrac = cumulative / totalWeight
        val endFrac = (cumulative + weight) / totalWeight
        cumulative += weight

        val tokenLit = when {
            boostedProgress >= endFrac -> 1f
            boostedProgress <= startFrac -> 0f
            else -> ((boostedProgress - startFrac) / (endFrac - startFrac).coerceAtLeast(0.001f))
                .coerceIn(0f, 1f)
        }
        val color = androidx.compose.ui.graphics.lerp(unlitColor, litColor, tokenLit)
        withStyle(SpanStyle(color = color)) {
            append(token)
            if (idx < tokens.lastIndex) append(" ")
        }
    }
}

@Composable
private fun InstrumentalPulseDots(
    tint: Color,
    centered: Boolean,
) {
    val infinite = rememberInfiniteTransition(label = "instrumental_dots")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = if (centered) Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally) else Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val alpha by infinite.animateFloat(
                initialValue = 0.28f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 750, delayMillis = i * 200),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$i",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = alpha)),
            )
        }
    }
}
