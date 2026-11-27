package com.xeno.mupa.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.component.UltraDynamicBackground
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.TABULAR_NUMS_FEATURE
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Container terpadu Mini Player <-> Full Now Playing <-> Cinema (Fase 2S — S2):
 * - SATU composable cover (MupaArtworkBox dengan crossfade=false & shared memoryCacheKey) yang
 *   posisi, ukuran (44dp -> lebar-48dp), radius (12dp -> 28dp), dan bayangannya di-lerp simetris
 *   oleh satu progres 0..1 (buka 520 ms, tutup 440 ms, cubic-bezier(0.2, 0, 0, 1)).
 * - Koreografi progres: isi mini memudar 0-25%; cover 0-100%; latar UD 0-70% (edge-to-edge + scrim status bar);
 *   header/judul/seekbar/kontrol masuk 35-100% bertahap (fade + 16dp slide).
 * - Saat Mini ciut (p == 0), NOL pointerInput/touch-blocker layar penuh -> scroll layar belakang 100% bebas.
 */
@Composable
fun MupaPlayerOverlayHost(
    playerState: MupaPlayerUiState,
    sheetState: MupaPlayerSheetState,
    stripArtistName: Boolean,
    bottomBarClearance: Dp,
    positionProvider: () -> Long,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenOverlay: (PlayerOverlayPane) -> Unit,
    onCloseOverlay: () -> Unit,
    onOpenCinema: () -> Unit,
    onCloseCinema: () -> Unit,
    onRetryLyrics: () -> Unit = {},
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onSelectEqPreset: (com.xeno.mupa.playback.eq.EqPreset) -> Unit,
    onEqBandChange: (Int, Float) -> Unit,
    onResetEq: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playerState.currentTrack ?: return
    val appearance = LocalMupaAppearance.current
    val density = LocalDensity.current
    val statusBarTopDp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Back bertahap: Cinema -> Overlay (Queue/EQ/Lyrics) -> Full -> Mini
    BackHandler(enabled = playerState.isCinemaMode || sheetState.isExpanded) {
        when {
            playerState.isCinemaMode -> onCloseCinema()
            playerState.activeOverlay != PlayerOverlayPane.NONE -> onCloseOverlay()
            else -> sheetState.collapse()
        }
    }

    // 2-frame pre-mount warm-up untuk sheet berat (Queue / EQ)
    var renderedSheetOverlay by remember { mutableStateOf(PlayerOverlayPane.NONE) }
    var sheetOverlayVisible by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.activeOverlay) {
        val target = playerState.activeOverlay
        if (target == PlayerOverlayPane.QUEUE || target == PlayerOverlayPane.EQUALIZER) {
            renderedSheetOverlay = target
            withFrameNanos { }
            withFrameNanos { }
            sheetOverlayVisible = true
        } else {
            sheetOverlayVisible = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerMaxWidth = maxWidth
        val fullWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val fullHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val miniHeightPx = with(density) { MupaPlayerSheetState.MiniPlayerHeight.toPx() }
        val bottomClearancePx = with(density) { bottomBarClearance.toPx() }
        val statusTopPx = with(density) { statusBarTopDp.toPx() }
        sheetState.travelDistancePx = (fullHeightPx - bottomClearancePx - miniHeightPx).coerceAtLeast(1f)

        val progressProvider = remember(sheetState) { { sheetState.progress } }
        val showFullLayer = sheetState.isVisible || playerState.isCinemaMode
        val showInlineLyrics = playerState.activeOverlay == PlayerOverlayPane.LYRICS

        // Koordinat slot cover mini & full (diukur langsung agar 0.0px jump pada layar apa pun!)
        val miniCoverSizeDp = 44.dp
        val fullCoverSizeDp = (containerMaxWidth - 48.dp).coerceIn(220.dp, 340.dp)
        val defaultMiniX = with(density) { (20.dp + 10.dp).toPx() }
        val defaultMiniY = (fullHeightPx - bottomClearancePx - miniHeightPx + with(density) { 10.dp.toPx() }).coerceAtLeast(0f)
        val defaultFullX = ((fullWidthPx - with(density) { fullCoverSizeDp.toPx() }) / 2f).coerceAtLeast(0f)
        val defaultFullY = statusTopPx + with(density) { 104.dp.toPx() }

        var hostOriginX by remember { mutableFloatStateOf(0f) }
        var hostOriginY by remember { mutableFloatStateOf(0f) }
        var measuredMiniX by remember { mutableFloatStateOf(Float.NaN) }
        var measuredMiniY by remember { mutableFloatStateOf(Float.NaN) }
        var measuredFullX by remember { mutableFloatStateOf(Float.NaN) }
        var measuredFullY by remember { mutableFloatStateOf(Float.NaN) }

        val miniDragUpModifier = Modifier.pointerInput(sheetState, fullHeightPx) {
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (sheetState.progress >= 0.5f || playerState.isCinemaMode) return@awaitEachGesture
                var lastY = down.position.y
                velocityTracker.resetTracking()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var dragStarted = false
                val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over < 0f) {
                        change.consume()
                        dragStarted = true
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        sheetState.onDragStart()
                        sheetState.onDragDelta(dy)
                    }
                }
                if (dragChange != null && dragStarted) {
                    verticalDrag(dragChange.id) { change ->
                        change.consume()
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        sheetState.onDragDelta(dy)
                    }
                    sheetState.onDragEnd(velocityTracker.calculateVelocity().y)
                }
            }
        }

        val fullDragDownModifier = Modifier.pointerInput(sheetState, fullHeightPx, sheetOverlayVisible, playerState.isCinemaMode) {
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (sheetState.progress < 0.2f || playerState.isCinemaMode || sheetOverlayVisible) {
                    return@awaitEachGesture
                }
                var lastY = down.position.y
                velocityTracker.resetTracking()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var dragStarted = false
                val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) {
                        change.consume()
                        dragStarted = true
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        sheetState.onDragStart()
                        sheetState.onDragDelta(dy)
                    }
                }
                if (dragChange != null && dragStarted) {
                    verticalDrag(dragChange.id) { change ->
                        change.consume()
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        sheetState.onDragDelta(dy)
                    }
                    sheetState.onDragEnd(velocityTracker.calculateVelocity().y)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInRoot()
                    hostOriginX = pos.x
                    hostOriginY = pos.y
                },
        ) {
            // 1. Latar Ultra Dynamic Full Now Playing (muncul mulus 0..70%, edge-to-edge sampai tepi atas + scrim status bar!)
            if (showFullLayer) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = MupaPlayerMorphMath.udBackdropAlpha(progressProvider())
                        }
                        // Touch blocker non-semantik agar layar belakang tidak tersentuh saat Full terbuka
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent()
                                }
                            }
                        }
                        .then(fullDragDownModifier),
                ) {
                    UltraDynamicBackground(
                        palette = appearance.effectiveUltraDynamicPalette,
                        headerOnly = false,
                    )

                    // Scrim halus di bawah status bar agar ikon jam/baterai selalu terbaca (perbaikan N1)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(statusBarTopDp + 36.dp)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.42f),
                                        Color.Transparent,
                                    ),
                                ),
                            ),
                    )
                }
            }

            // 2. Mini Player (hanya area 64dp; isi memudar 0..25%; artwork dirender oleh SATU composable morphing cover!)
            if (!sheetState.isSettledExpanded) {
                MiniPlayerBar(
                    track = track,
                    stripArtistName = stripArtistName,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    progressFraction = playerState.progressFraction,
                    showArtwork = false,
                    onArtworkSlotPositioned = { rootX, rootY ->
                        measuredMiniX = rootX - hostOriginX
                        measuredMiniY = rootY - hostOriginY
                    },
                    onExpand = { sheetState.expand() },
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 20.dp, end = 20.dp, bottom = bottomBarClearance)
                        .fillMaxWidth()
                        .height(MupaPlayerSheetState.MiniPlayerHeight)
                        .then(miniDragUpModifier)
                        .graphicsLayer {
                            alpha = MupaPlayerMorphMath.miniContentAlpha(progressProvider())
                        },
                )
            }

            // 3. Full Now Playing Content (elemen masuk bertahap 35..100% fade + geser 16dp, simetris saat tutup!)
            if (showFullLayer) {
                FullNowPlayingContent(
                    track = track,
                    stripArtistName = stripArtistName,
                    playerState = playerState,
                    progressProvider = progressProvider,
                    positionProvider = positionProvider,
                    maxWidthDp = containerMaxWidth,
                    renderEmbeddedCover = false,
                    onCoverSlotPositioned = { rootX, rootY ->
                        measuredFullX = rootX - hostOriginX
                        measuredFullY = rootY - hostOriginY
                    },
                    onCollapse = { sheetState.collapse() },
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    onToggleLyrics = { onOpenOverlay(PlayerOverlayPane.LYRICS) },
                    onRetryLyrics = onRetryLyrics,
                    onOpenQueue = { onOpenOverlay(PlayerOverlayPane.QUEUE) },
                    onOpenEqualizer = { onOpenOverlay(PlayerOverlayPane.EQUALIZER) },
                    onOpenCinema = onOpenCinema,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    onCycleSleepTimer = onCycleSleepTimer,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(fullDragDownModifier),
                )
            }

            // 4. SATU COMPOSABLE COVER MORPHING (0..100% posisi, ukuran, radius 12dp->28dp, & bayangan naik simetris!)
            val coverLyricsAlpha by animateFloatAsState(
                targetValue = if (showInlineLyrics && sheetState.isExpanded) 0f else 1f,
                animationSpec = MupaMotion.standardTween(220),
                label = "singleCoverLyricsFade",
            )
            val coverPlayBreath by animateFloatAsState(
                targetValue = if (playerState.isPlaying || !sheetState.isExpanded) 1f else 0.95f,
                animationSpec = MupaMotion.emphasizedTween(280),
                label = "singleCoverBreath",
            )
            if (!playerState.isCinemaMode && !sheetOverlayVisible) {
                Box(
                    modifier = Modifier
                        .testTag("mupa_morphing_cover")
                        .layout { measurable, _ ->
                            val m = MupaPlayerMorphMath.coverMorphFraction(progressProvider())
                            val sizeDp = lerp(miniCoverSizeDp, fullCoverSizeDp, m)
                            val sizePx = sizeDp.roundToPx().coerceAtLeast(1)
                            val placeable = measurable.measure(Constraints.fixed(sizePx, sizePx))

                            val sx = if (!measuredMiniX.isNaN()) measuredMiniX else defaultMiniX
                            val sy = if (!measuredMiniY.isNaN()) measuredMiniY else defaultMiniY
                            val ex = if (!measuredFullX.isNaN()) measuredFullX else defaultFullX
                            val ey = if (!measuredFullY.isNaN()) measuredFullY else defaultFullY

                            val x = (sx + (ex - sx) * m).roundToInt()
                            val y = (sy + (ey - sy) * m).roundToInt()
                            layout(0, 0) {
                                placeable.placeRelative(x, y)
                            }
                        }
                        .graphicsLayer {
                            val m = MupaPlayerMorphMath.coverMorphFraction(progressProvider())
                            val cornerPx = lerp(12.dp, 28.dp, m).toPx()
                            val elevationPx = (24.dp * MupaPlayerMorphMath.coverShadowFraction(m)).toPx()
                            shadowElevation = elevationPx
                            ambientShadowColor = Color.Black.copy(alpha = 0.65f)
                            spotShadowColor = Color.Black.copy(alpha = 0.72f)
                            shape = RoundedCornerShape(cornerPx)
                            clip = true
                            alpha = coverLyricsAlpha
                            val breathScale = 1f + (coverPlayBreath - 1f) * m
                            scaleX = breathScale
                            scaleY = breathScale
                        }
                        .clickable(
                            enabled = coverLyricsAlpha > 0.5f,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (sheetState.isExpanded) {
                                onOpenOverlay(PlayerOverlayPane.LYRICS)
                            } else {
                                sheetState.expand()
                            }
                        },
                ) {
                    MupaArtworkBox(
                        artworkUrl = track.artworkUrl,
                        contentDescription = track.title,
                        size = fullCoverSizeDp,
                        shape = RoundedCornerShape(0.dp),
                        iconSize = 52.dp,
                        memoryCacheKey = "mupa_cover_${track.id}",
                        enableCrossfade = false,
                        decodeSizePxOverride = 768,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            // 5. Sheet Antrean & Equalizer (2-frame pre-mount warm-up, Emphasized Decelerate)
            AnimatedVisibility(
                visible = sheetOverlayVisible && sheetState.isExpanded,
                enter = slideInVertically(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                    initialOffsetY = { it / 6 },
                ) + fadeIn(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
                exit = slideOutVertically(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationShortMs),
                    targetOffsetY = { it / 8 },
                ) + fadeOut(MupaMotion.standardTween(MupaMotion.DurationShortMs)),
            ) {
                when (renderedSheetOverlay) {
                    PlayerOverlayPane.QUEUE -> MupaQueueOverlay(
                        queue = playerState.queue,
                        currentIndex = playerState.currentIndex,
                        stripArtistName = stripArtistName,
                        queueSourceLabel = playerState.queueSource.displayLabel,
                        onSelectIndex = onSelectQueueIndex,
                        onMoveItem = onMoveQueueItem,
                        onRemoveItem = onRemoveQueueItem,
                        onClearUpcoming = onClearUpcomingQueue,
                        onClose = onCloseOverlay,
                    )
                    PlayerOverlayPane.EQUALIZER -> MupaEqualizerOverlay(
                        activePreset = playerState.eqPreset,
                        bandGainsDb = playerState.eqGainsDb,
                        onSelectPreset = onSelectEqPreset,
                        onBandGainChange = onEqBandChange,
                        onReset = onResetEq,
                        onClose = onCloseOverlay,
                    )
                    else -> Unit
                }
            }

            // 6. Mode Cinema Layar Penuh
            AnimatedVisibility(
                visible = playerState.isCinemaMode,
                enter = fadeIn(MupaMotion.emphasizedTween(280)),
                exit = fadeOut(MupaMotion.standardTween(200)),
            ) {
                MupaCinemaOverlay(
                    track = track,
                    stripArtistName = stripArtistName,
                    playerState = playerState,
                    positionProvider = positionProvider,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onExitCinema = onCloseCinema,
                )
            }
        }
    }
}

/**
 * Mini Player Mupa Calm dengan tint Ultra Dynamic tipis di sudut & progres 2dp.
 */
@Composable
fun MiniPlayerBar(
    track: Track,
    isPlaying: Boolean,
    progressFraction: Float,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
    stripArtistName: Boolean = true,
    isBuffering: Boolean = false,
    showArtwork: Boolean = true,
    onArtworkSlotPositioned: ((Float, Float) -> Unit)? = null,
) {
    val disp = remember(track, stripArtistName) { TrackDisplayRules.formatTrack(track, stripArtistName) }
    val appearance = LocalMupaAppearance.current
    val udPalette = appearance.effectiveUltraDynamicPalette

    MupaGlassSurface(
        shape = MupaRadii.MiniPlayer,
        modifier = modifier
            .testTag("mupa_mini_player")
            .semantics { contentDescription = "Mini player: ${disp.title}, ${disp.subtitle}. Ketuk untuk membuka pemutar penuh" }
            .clickable(onClick = onExpand),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    if (appearance.shouldRenderUltraDynamic && udPalette.glowIntensity > 0.05f) {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(udPalette.glow1.copy(alpha = 0.22f), Color.Transparent),
                                center = Offset(size.width * 0.08f, 0f),
                                radius = size.width * 0.65f,
                            ),
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(udPalette.glow2.copy(alpha = 0.15f), Color.Transparent),
                                center = Offset(size.width * 0.94f, size.height),
                                radius = size.width * 0.55f,
                            ),
                        )
                    }
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .onGloballyPositioned { coords ->
                            val pos = coords.positionInRoot()
                            onArtworkSlotPositioned?.invoke(pos.x, pos.y)
                        },
                ) {
                    if (showArtwork) {
                        MupaArtworkBox(
                            artworkUrl = track.artworkUrl,
                            contentDescription = disp.title,
                            size = 44.dp,
                            shape = MupaRadii.ArtworkSmall,
                            iconSize = 20.dp,
                            memoryCacheKey = "mupa_cover_${track.id}",
                            enableCrossfade = false,
                        )
                    }
                }

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

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.CoralPrimary)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            color = MupaPalette.LogoCharcoal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Jeda" else "Putar",
                            tint = MupaPalette.LogoCharcoal,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Lagu berikutnya",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

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
internal fun FullNowPlayingContent(
    track: Track,
    stripArtistName: Boolean,
    playerState: MupaPlayerUiState,
    progressProvider: () -> Float,
    positionProvider: () -> Long,
    maxWidthDp: Dp,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleLyrics: () -> Unit,
    onRetryLyrics: () -> Unit = {},
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenCinema: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
    renderEmbeddedCover: Boolean = true,
    onCoverSlotPositioned: ((Float, Float) -> Unit)? = null,
) {
    val disp = remember(track, stripArtistName) { TrackDisplayRules.formatTrack(track, stripArtistName) }
    val showInlineLyrics = playerState.activeOverlay == PlayerOverlayPane.LYRICS
    var topMenuOpen by remember { mutableStateOf(false) }
    val coverSize = (maxWidthDp - 48.dp).coerceIn(220.dp, 340.dp)

    Column(
        modifier = modifier
            .testTag("mupa_full_now_playing")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Step 0 (35..100%): Pegangan + Top Bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progressProvider()
                    alpha = MupaPlayerMorphMath.staggeredElementProgress(p, stepIndex = 0)
                    translationY = MupaPlayerMorphMath.staggeredSlideDp(p, stepIndex = 0).dp.toPx()
                },
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Ciutkan pemutar",
                        tint = MupaPalette.LogoCream,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = playerState.queueSource.displayLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MupaPalette.LogoCream.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = disp.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MupaPalette.CoralSoft,
                        maxLines = 1,
                    )
                }

                Box {
                    IconButton(
                        onClick = { topMenuOpen = true },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Menu pemutar",
                            tint = MupaPalette.LogoCream,
                        )
                    }
                    DropdownMenu(
                        expanded = topMenuOpen,
                        onDismissRequest = { topMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        DropdownMenuItem(
                            text = {
                                val label = playerState.sleepTimerMinutes?.let { "Timer tidur ($it mnt)" } ?: "Timer tidur (Mati)"
                                Text(label)
                            },
                            leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                            onClick = {
                                topMenuOpen = false
                                onCycleSleepTimer()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Mode Cinema") },
                            leadingIcon = { Icon(Icons.Rounded.Movie, contentDescription = null) },
                            onClick = {
                                topMenuOpen = false
                                onOpenCinema()
                            },
                        )
                    }
                }
            }
        }

        // Area Slot Cover / Panel Lirik Inline
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Slot anchor presisi untuk cover tunggal
            Box(
                modifier = Modifier
                    .size(coverSize)
                    .onGloballyPositioned { coords ->
                        val pos = coords.positionInRoot()
                        onCoverSlotPositioned?.invoke(pos.x, pos.y)
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (renderEmbeddedCover && !showInlineLyrics) {
                    MupaArtworkBox(
                        artworkUrl = track.artworkUrl,
                        contentDescription = disp.title,
                        size = coverSize,
                        shape = MupaRadii.ArtworkLarge,
                        iconSize = 68.dp,
                        memoryCacheKey = "mupa_cover_${track.id}",
                        enableCrossfade = false,
                        decodeSizePxOverride = 768,
                    )
                }
            }

            if (showInlineLyrics) {
                MupaInlineLyricsPanel(
                    playerState = playerState,
                    positionProvider = positionProvider,
                    onSeekTo = onSeekTo,
                    onAdjustOffset = onAdjustLyricsOffset,
                    onResetOffset = onResetLyricsOffset,
                    onRetry = onRetryLyrics,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = MupaPlayerMorphMath.staggeredElementProgress(progressProvider(), stepIndex = 1)
                        },
                )
            }
        }

        // Step 1: Judul + Artis + Favorit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progressProvider()
                    alpha = MupaPlayerMorphMath.staggeredElementProgress(p, stepIndex = 1)
                    translationY = MupaPlayerMorphMath.staggeredSlideDp(p, stepIndex = 1).dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = disp.title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MupaPalette.LogoCream,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = disp.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MupaPalette.LogoCream.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (track.isFavorite) "Hapus dari favorit" else "Tambah ke favorit",
                    tint = if (track.isFavorite) MupaPalette.CoralPrimary else MupaPalette.LogoCream.copy(alpha = 0.78f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Step 2: Seekbar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progressProvider()
                    alpha = MupaPlayerMorphMath.staggeredElementProgress(p, stepIndex = 2)
                    translationY = MupaPlayerMorphMath.staggeredSlideDp(p, stepIndex = 2).dp.toPx()
                },
        ) {
            MupaCustomSeekbar(
                progress = playerState.progressFraction,
                bufferedFraction = playerState.bufferedFraction,
                showBuffer = track.sourceType == AudioSourceType.STREAMING,
                positionMs = playerState.positionMs,
                durationMs = playerState.durationMs,
                onSeekTo = onSeekTo,
            )

            playerState.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MupaPalette.CoralSoft,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Step 3: Transport Utama (tombol Play/Pause KREM #F6F4EF kontras di atas warna dinamis apa pun) + Baris Aksi Berlabel (N9)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val p = progressProvider()
                    alpha = MupaPlayerMorphMath.staggeredElementProgress(p, stepIndex = 3)
                    translationY = MupaPlayerMorphMath.staggeredSlideDp(p, stepIndex = 3).dp.toPx()
                },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Mode acak",
                        tint = if (playerState.isShuffleEnabled) MupaPalette.CoralPrimary else MupaPalette.LogoCream.copy(alpha = 0.72f),
                    )
                }
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Lagu sebelumnya",
                        tint = MupaPalette.LogoCream,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                        .background(MupaPalette.LogoCream)
                        .clickable(onClick = onTogglePlayPause),
                    contentAlignment = Alignment.Center,
                ) {
                    if (playerState.isBuffering) {
                        CircularProgressIndicator(
                            color = MupaPalette.LogoCharcoal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(28.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Jeda" else "Putar",
                            tint = MupaPalette.LogoCharcoal,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Lagu berikutnya",
                        tint = MupaPalette.LogoCream,
                        modifier = Modifier.size(34.dp),
                    )
                }
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.size(48.dp),
                ) {
                    val repeatIcon = if (playerState.repeatMode == RepeatMode.ONE) {
                        Icons.Rounded.RepeatOne
                    } else {
                        Icons.Rounded.Repeat
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Mode ulangi",
                        tint = if (playerState.repeatMode != RepeatMode.OFF) {
                            MupaPalette.CoralPrimary
                        } else {
                            MupaPalette.LogoCream.copy(alpha = 0.72f)
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Baris Aksi Bawah dengan Ikon + Label 11sp + Badge Antrean rapi di pojok ikon (perbaikan N9)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NowPlayingActionButton(
                    icon = Icons.Rounded.Lyrics,
                    label = "Lirik",
                    contentDesc = "Lirik",
                    active = showInlineLyrics,
                    onClick = onToggleLyrics,
                )
                NowPlayingActionButton(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Antrean",
                    contentDesc = "Antrean putar",
                    active = playerState.activeOverlay == PlayerOverlayPane.QUEUE,
                    badgeCount = playerState.queue.size.takeIf { it > 0 },
                    onClick = onOpenQueue,
                )
                NowPlayingActionButton(
                    icon = Icons.Rounded.GraphicEq,
                    label = "EQ",
                    contentDesc = "Equalizer 5-band",
                    active = playerState.activeOverlay == PlayerOverlayPane.EQUALIZER ||
                        playerState.eqPreset != com.xeno.mupa.playback.eq.EqPreset.FLAT,
                    onClick = onOpenEqualizer,
                )
                NowPlayingActionButton(
                    icon = Icons.Rounded.Movie,
                    label = "Cinema",
                    contentDesc = "Mode Cinema",
                    active = playerState.isCinemaMode,
                    onClick = onOpenCinema,
                )
            }
        }
    }
}

@Composable
private fun NowPlayingActionButton(
    icon: ImageVector,
    label: String,
    contentDesc: String,
    active: Boolean,
    onClick: () -> Unit,
    badgeCount: Int? = null,
) {
    val tint = if (active) MupaPalette.CoralPrimary else MupaPalette.LogoCream.copy(alpha = 0.78f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) MupaPalette.CoralPrimary.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
            if (badgeCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(if (active) MupaPalette.CoralPrimary else MupaPalette.Surface2)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (badgeCount > 99) "99+" else "$badgeCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFeatureSettings = TABULAR_NUMS_FEATURE,
                        ),
                        color = if (active) MupaPalette.LogoCharcoal else MupaPalette.LogoCream,
                    )
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = tint,
            maxLines = 1,
        )
    }
}

/**
 * Seekbar custom Mupa Calm:
 * - Track 4dp (idle) -> 6dp (saat drag); thumb 12dp muncul halus saat drag.
 * - Segmen buffer samar HANYA untuk Streaming; disembunyikan untuk Lokal.
 * - Waktu angka tabular; total durasi disembunyikan jika <= 0ms.
 */
@Composable
fun MupaCustomSeekbar(
    progress: Float,
    bufferedFraction: Float,
    showBuffer: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableStateOf(false) }
    var draftFraction by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }

    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "seekbarHeight",
    )
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
        label = "seekbarThumbAlpha",
    )

    val displayedFraction = (if (isDragging) draftFraction else progress).coerceIn(0f, 1f)
    val displayedPosMs = if (isDragging) (draftFraction * durationMs).toLong() else positionMs

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        if (durationMs > 0L) {
                            val frac = (offset.x / barWidthPx).coerceIn(0f, 1f)
                            onSeekTo((frac * durationMs).toLong())
                        }
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            draftFraction = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            draftFraction = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (durationMs > 0L) {
                                onSeekTo((draftFraction * durationMs).toLong())
                            }
                            isDragging = false
                        },
                        onDragCancel = { isDragging = false },
                    )
                }
                .drawBehind {
                    val h = trackHeight.toPx()
                    val y = (size.height - h) / 2f
                    val radius = CornerRadius(h / 2f, h / 2f)

                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, h),
                        cornerRadius = radius,
                    )

                    if (showBuffer && bufferedFraction > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.28f),
                            topLeft = Offset(0f, y),
                            size = Size(size.width * bufferedFraction.coerceIn(0f, 1f), h),
                            cornerRadius = radius,
                        )
                    }

                    val activeW = size.width * displayedFraction
                    if (activeW > 0f) {
                        drawRoundRect(
                            color = MupaPalette.LogoCream,
                            topLeft = Offset(0f, y),
                            size = Size(activeW, h),
                            cornerRadius = radius,
                        )
                    }

                    if (thumbAlpha > 0.01f) {
                        drawCircle(
                            color = MupaPalette.LogoCream.copy(alpha = thumbAlpha),
                            radius = 6.dp.toPx(),
                            center = Offset(activeW.coerceIn(6.dp.toPx(), size.width - 6.dp.toPx()), size.height / 2f),
                        )
                    }
                },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDurationMs(displayedPosMs),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMS_FEATURE),
                color = MupaPalette.LogoCream.copy(alpha = 0.72f),
            )
            TrackDisplayRules.formatDuration(durationMs)?.let { total ->
                Text(
                    text = total,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMS_FEATURE),
                    color = MupaPalette.LogoCream.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
fun TrackArtworkTile(
    track: Track,
    modifier: Modifier = Modifier,
    iconSize: Dp = 28.dp,
) {
    MupaArtworkBox(
        artworkUrl = track.artworkUrl,
        contentDescription = track.title,
        size = 48.dp,
        iconSize = iconSize,
        modifier = modifier,
    )
}

fun formatDurationMs(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Preview(name = "Mupa Mini Player Calm", showBackground = true, backgroundColor = 0xFF16161A)
@Composable
private fun MiniPlayerPreview() {
    MupaTheme(appearance = MupaAppearance()) {
        Box(modifier = Modifier.padding(16.dp)) {
            MiniPlayerBar(
                track = Track(id = "1", title = "blue", artistName = "yung kai"),
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
