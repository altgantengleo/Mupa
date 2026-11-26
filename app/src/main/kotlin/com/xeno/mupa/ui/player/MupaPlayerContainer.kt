package com.xeno.mupa.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Container terpadu Mini Player <-> Full Now Playing (TANPA mode Semi).
 *
 * Mekanisme animasi:
 * - Satu state progress 0f..1f dibaca HANYA di fase layout/draw (graphicsLayer & Modifier.layout).
 * - Tanpa spring overshoot; tanpa CompositingStrategy.Offscreen layar penuh.
 * - Backdrop sinkron sejak frame 0 (tanpa kedip hitam saat membuka/menutup panel).
 * - 2-frame pre-mount warm-up saat membuka overlay Lirik / Antrean.
 */
@Composable
fun MupaPlayerOverlayHost(
    playerState: MupaPlayerUiState,
    sheetState: MupaPlayerSheetState,
    bottomBarClearance: Dp,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenOverlay: (PlayerOverlayPane) -> Unit,
    onCloseOverlay: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack ?: return
    val appearance = LocalMupaAppearance.current
    val density = LocalDensity.current

    // Back handler konsisten: tutup overlay Lirik/Antrean dulu, baru ciutkan Full -> Mini.
    BackHandler(enabled = sheetState.isExpanded) {
        if (playerState.activeOverlay != PlayerOverlayPane.NONE) {
            onCloseOverlay()
        } else {
            sheetState.collapse()
        }
    }

    // 2-frame warm-up untuk overlay berat (Lirik / Antrean) sebelum animasi masuk
    var renderedOverlay by remember { mutableStateOf(PlayerOverlayPane.NONE) }
    var overlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.activeOverlay) {
        val target = playerState.activeOverlay
        if (target == PlayerOverlayPane.NONE) {
            overlayVisible = false
        } else {
            renderedOverlay = target
            withFrameNanos { }
            withFrameNanos { }
            overlayVisible = true
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerMaxWidth = maxWidth
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val miniHeightPx = with(density) { MupaPlayerSheetState.MiniPlayerHeight.toPx() }
        val miniBottomOffsetPx = with(density) { bottomBarClearance.toPx() }
        val collapsedTopPx = (fullHeightPx - miniBottomOffsetPx - miniHeightPx).coerceAtLeast(0f)
        sheetState.travelDistancePx = collapsedTopPx.coerceAtLeast(1f)

        val progressProvider = remember(sheetState) { { sheetState.progress } }

        // Scrim ringan di belakang sheet (bukan Offscreen FBO)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = progressProvider()
                    if (p > 0.005f) {
                        drawRect(Color.Black.copy(alpha = (0.62f * p).coerceIn(0f, 0.62f)))
                    }
                },
        )

        val dragModifier = Modifier.pointerInput(sheetState) {
            val velocityTracker = VelocityTracker()
            detectVerticalDragGestures(
                onDragStart = {
                    velocityTracker.resetTracking()
                    sheetState.onDragStart()
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    sheetState.onDragDelta(dragAmount)
                },
                onDragEnd = {
                    val velY = velocityTracker.calculateVelocity().y
                    sheetState.onDragEnd(velY)
                },
                onDragCancel = {
                    sheetState.onDragEnd(0f)
                },
            )
        }

        val fullBackdropBase = if (appearance.isOledMode) {
            MupaPalette.OledBackground
        } else {
            MupaPalette.Background
        }
        val coverAccent = appearance.effectiveCoverAccent

        // Sheet terpadu yang bergerak mulus dari posisi Mini (p=0) ke Full (p=1)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = progressProvider()
                    translationY = collapsedTopPx * (1f - p)
                }
                .then(dragModifier),
        ) {
            // Lapisan 1: Backdrop Full Now Playing (sinkron sejak frame 0, overlap penuh di p=0.20)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progressProvider()
                        alpha = (p / 0.20f).coerceIn(0f, 1f)
                    }
                    .background(fullBackdropBase)
                    .drawBehind {
                        if (!appearance.isOledMode && !appearance.isPerformanceMode) {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        coverAccent.primary.copy(alpha = 0.34f),
                                        coverAccent.secondary.copy(alpha = 0.14f),
                                        Color.Transparent,
                                    ),
                                    center = Offset(size.width * 0.5f, size.height * 0.26f),
                                    radius = size.width * 0.95f,
                                ),
                            )
                        }
                    },
            )

            // Lapisan 2: Full Now Playing Content (ditempatkan hanya saat p > 0.10 agar tidak mengganggu klik mini)
            FullNowPlayingContent(
                track = track,
                playerState = playerState,
                progressProvider = progressProvider,
                onCollapse = { sheetState.collapse() },
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onToggleFavorite = onToggleFavorite,
                onOpenLyrics = { onOpenOverlay(PlayerOverlayPane.LYRICS) },
                onOpenQueue = { onOpenOverlay(PlayerOverlayPane.QUEUE) },
                onOpenEqualizer = onOpenEqualizer,
                onCycleSleepTimer = onCycleSleepTimer,
                modifier = Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val p = progressProvider()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (p > 0.08f) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = progressProvider()
                        alpha = ((p - 0.18f) / 0.65f).coerceIn(0f, 1f)
                        val scale = 0.96f + 0.04f * p
                        scaleX = scale
                        scaleY = scale
                    },
            )

            // Lapisan 3: Mini Player (memudar halus 0..0.32; overlap aman dengan backdrop yang penuh di 0.20)
            MiniPlayerBar(
                track = track,
                isPlaying = playerState.isPlaying,
                progressFraction = playerState.progressFraction,
                onExpand = { sheetState.expand() },
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(MupaPlayerSheetState.MiniPlayerHeight)
                    .layout { measurable, constraints ->
                        val p = progressProvider()
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            if (p < 0.35f) {
                                placeable.placeRelative(0, 0)
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = progressProvider()
                        alpha = (1f - p / 0.32f).coerceIn(0f, 1f)
                    },
            )

            // Lapisan 4: Morphing Artwork Indicator saat transisi berlangsung (0.02..0.96)
            val heroSize = (containerMaxWidth - 64.dp).coerceIn(220.dp, 320.dp)
            Box(
                modifier = Modifier
                    .layout { measurable, _ ->
                        val p = progressProvider()
                        val sizeDp = lerp(44.dp, heroSize, p)
                        val sizePx = sizeDp.roundToPx().coerceAtLeast(1)
                        val placeable = measurable.measure(
                            androidx.compose.ui.unit.Constraints.fixed(sizePx, sizePx),
                        )
                        val startX = 30.dp.toPx()
                        val endX = ((containerMaxWidth.toPx() - sizePx) / 2f).coerceAtLeast(0f)
                        val startY = 10.dp.toPx()
                        val endY = 116.dp.toPx()
                        val x = (startX + (endX - startX) * p).roundToInt()
                        val y = (startY + (endY - startY) * p).roundToInt()
                        layout(0, 0) {
                            if (p in 0.02f..0.96f) {
                                placeable.placeRelative(x, y)
                            }
                        }
                    }
                    .graphicsLayer {
                        val p = progressProvider()
                        alpha = when {
                            p < 0.08f -> (p / 0.08f).coerceIn(0f, 1f)
                            p > 0.88f -> ((0.96f - p) / 0.08f).coerceIn(0f, 1f)
                            else -> 1f
                        }
                        val cornerPx = lerp(10.dp, 24.dp, p).toPx()
                        shape = RoundedCornerShape(cornerPx)
                        clip = true
                    },
            ) {
                TrackArtworkTile(
                    track = track,
                    modifier = Modifier.fillMaxSize(),
                    iconSize = 42.dp,
                )
            }

            // Lapisan 5: Overlay Lirik & Antrean (Emphasized Decelerate, nol overshoot)
            AnimatedVisibility(
                visible = overlayVisible && sheetState.isExpanded,
                enter = slideInVertically(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                    initialOffsetY = { it / 6 },
                ) + fadeIn(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
                exit = slideOutVertically(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationShortMs),
                    targetOffsetY = { it / 8 },
                ) + fadeOut(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
            ) {
                when (renderedOverlay) {
                    PlayerOverlayPane.LYRICS -> MupaLyricsOverlay(
                        trackTitle = track.title,
                        artistName = track.artistName,
                        lines = playerState.lyrics,
                        positionMs = playerState.positionMs,
                        offsetMs = playerState.lyricsOffsetMs,
                        onSeekTo = onSeekTo,
                        onAdjustOffset = onAdjustLyricsOffset,
                        onResetOffset = onResetLyricsOffset,
                        onClose = onCloseOverlay,
                    )
                    PlayerOverlayPane.QUEUE -> MupaQueueOverlay(
                        queue = playerState.queue,
                        currentIndex = playerState.currentIndex,
                        onSelectIndex = onSelectQueueIndex,
                        onMoveItem = onMoveQueueItem,
                        onRemoveItem = onRemoveQueueItem,
                        onClose = onCloseOverlay,
                    )
                    PlayerOverlayPane.NONE -> Unit
                }
            }
        }
    }
}

@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progressFraction: Float,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MupaGlassSurface(
        shape = MupaRadii.MiniPlayer,
        modifier = modifier
            .semantics { contentDescription = "Mini player: ${track.title}, ${track.artistName}" }
            .clickable(onClick = onExpand),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackArtworkTile(
                    track = track,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MupaRadii.ArtworkSmall),
                    iconSize = 20.dp,
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.CoralPrimary),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Jeda" else "Putar",
                        tint = MupaPalette.LogoCharcoal,
                        modifier = Modifier.size(22.dp),
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Lagu berikutnya",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Garis progres tipis di bagian bawah mini player
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(MupaPalette.CoralPrimary),
                )
            }
        }
    }
}

@Composable
private fun FullNowPlayingContent(
    track: Track,
    playerState: MupaPlayerUiState,
    progressProvider: () -> Float,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingSlider by remember { mutableStateOf(false) }
    var sliderDraft by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pegangan drag-down
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.28f)),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Top Bar Now Playing
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Ciutkan pemutar",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "SEDANG DIPUTAR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (track.sourceType == AudioSourceType.LOCAL) "Pustaka Lokal" else "Streaming Mupa",
                    style = MaterialTheme.typography.labelMedium,
                    color = MupaPalette.CoralSoft,
                )
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        Spacer(modifier = Modifier.weight(0.5f))

        // Sampul utama (transparan saat morphing berlangsung agar tidak dobel)
        TrackArtworkTile(
            track = track,
            modifier = Modifier
                .size(280.dp)
                .clip(MupaRadii.ArtworkLarge)
                .graphicsLayer {
                    val p = progressProvider()
                    alpha = if (p > 0.90f) ((p - 0.90f) / 0.08f).coerceIn(0f, 1f) else 0f
                },
            iconSize = 68.dp,
        )

        Spacer(modifier = Modifier.weight(0.6f))

        // Judul, Artis & Favorit
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Hapus dari favorit" else "Tambah ke favorit",
                    tint = if (track.isFavorite) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Seekbar & Waktu
        val sliderValue = if (draggingSlider) sliderDraft else playerState.progressFraction
        Slider(
            value = sliderValue,
            onValueChange = { fraction ->
                draggingSlider = true
                sliderDraft = fraction
            },
            onValueChangeFinished = {
                draggingSlider = false
                onSeekTo((sliderDraft * playerState.durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = MupaPalette.CoralPrimary,
                activeTrackColor = MupaPalette.CoralPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPos = if (draggingSlider) (sliderDraft * playerState.durationMs).toLong() else playerState.positionMs
            Text(
                text = formatDurationMs(shownPos),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDurationMs(playerState.durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5 Kontrol Transport Utama
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    imageVector = Icons.Rounded.Shuffle,
                    contentDescription = "Mode acak",
                    tint = if (playerState.isShuffleEnabled) MupaPalette.CoralBright else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onSkipPrevious,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "Lagu sebelumnya",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(MupaPalette.CoralPrimary),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                    tint = MupaPalette.LogoCharcoal,
                    modifier = Modifier.size(36.dp),
                )
            }
            IconButton(
                onClick = onSkipNext,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Lagu berikutnya",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(34.dp),
                )
            }
            IconButton(onClick = onCycleRepeat) {
                val repeatIcon = if (playerState.repeatMode == RepeatMode.ONE) {
                    Icons.Rounded.RepeatOne
                } else {
                    Icons.Rounded.Repeat
                }
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Mode ulangi",
                    tint = if (playerState.repeatMode != RepeatMode.OFF) {
                        MupaPalette.CoralBright
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Baris Aksi Sekunder (dipindah rapi dari mode Semi yang dihapus)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryPlayerAction(
                icon = Icons.Rounded.Lyrics,
                label = "Lirik",
                active = playerState.activeOverlay == PlayerOverlayPane.LYRICS,
                onClick = onOpenLyrics,
            )
            SecondaryPlayerAction(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Antrean",
                badgeCount = playerState.queue.size,
                active = playerState.activeOverlay == PlayerOverlayPane.QUEUE,
                onClick = onOpenQueue,
            )
            SecondaryPlayerAction(
                icon = Icons.Rounded.GraphicEq,
                label = "EQ",
                active = false,
                onClick = onOpenEqualizer,
            )
            SecondaryPlayerAction(
                icon = Icons.Rounded.Timer,
                label = playerState.sleepTimerMinutes?.let { "${it}m" } ?: "Timer",
                active = playerState.sleepTimerMinutes != null,
                onClick = onCycleSleepTimer,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SecondaryPlayerAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    badgeCount: Int? = null,
) {
    val tint = if (active) MupaPalette.CoralBright else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .clip(MupaRadii.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (badgeCount != null && badgeCount > 0) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MupaPalette.CoralPrimary,
                        contentColor = MupaPalette.LogoCharcoal,
                    ) {
                        Text(text = if (badgeCount > 9) "9+" else "$badgeCount")
                    }
                },
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        } else {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
fun TrackArtworkTile(
    track: Track,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
) {
    val base = track.accentColorArgb?.let { Color(it.toInt()) } ?: MupaPalette.CoralPrimary
    Box(
        modifier = modifier.background(
            brush = Brush.linearGradient(
                colors = listOf(
                    base.copy(alpha = 0.92f),
                    MupaPalette.SurfaceContainerHigh,
                ),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MupaPalette.LogoCream.copy(alpha = 0.88f),
            modifier = Modifier.size(iconSize),
        )
    }
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Preview(name = "Mupa Mini Player", showBackground = true, backgroundColor = 0xFF0E0E11)
@Composable
private fun MiniPlayerPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Box(modifier = Modifier.padding(16.dp)) {
            MiniPlayerBar(
                track = Track(
                    id = "1",
                    title = "Senja di Kota Tua",
                    artistName = "Hindia",
                    accentColorArgb = 0xFFFF5A3C,
                ),
                isPlaying = true,
                progressFraction = 0.42f,
                onExpand = {},
                onTogglePlayPause = {},
                onSkipNext = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MupaPlayerSheetState.MiniPlayerHeight),
            )
        }
    }
}

@Preview(name = "Mupa Full Now Playing", showBackground = true)
@Composable
private fun FullNowPlayingPreview() {
    val track = Track(
        id = "1",
        title = "Senja di Kota Tua",
        artistName = "Hindia",
        durationMs = 214_000L,
        accentColorArgb = 0xFFFF5A3C,
        isFavorite = true,
    )
    MupaTheme(appearance = MupaAppearance()) {
        Surface(color = MupaPalette.Background) {
            FullNowPlayingContent(
                track = track,
                playerState = MupaPlayerUiState(
                    currentTrack = track,
                    queue = listOf(track),
                    isPlaying = true,
                    positionMs = 68_000L,
                    durationMs = 214_000L,
                ),
                progressProvider = { 1f },
                onCollapse = {},
                onTogglePlayPause = {},
                onSeekTo = {},
                onSkipNext = {},
                onSkipPrevious = {},
                onToggleShuffle = {},
                onCycleRepeat = {},
                onToggleFavorite = {},
                onOpenLyrics = {},
                onOpenQueue = {},
                onOpenEqualizer = {},
                onCycleSleepTimer = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
