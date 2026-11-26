package com.xeno.mupa.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
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
 * Container terpadu Mini Player -> Full Now Playing -> Cinema (perbaikan B6 & B11).
 *
 * Anti-kedip (menghindari 6 sumber kedip di docs/AUDIT.md):
 * 1. Backdrop solid sinkron sejak frame 0 (penuh di p=0.18 sebelum mini memudar di p=0.25).
 * 2. Nol CompositingStrategy.Offscreen layar penuh pada konten belakang.
 * 3. Komponen utama tetap terkomposisi stabil; visibilitas/posisi dibaca di fase layout/draw saja.
 * 4. MupaPlayerSheetState tidak di-recreate saat WindowInsets berubah.
 * 5. Drag 1:1 memakai CoroutineStart.UNDISPATCHED pada koordinat STASIONER (tanpa loop koordinat translationY).
 * 6. Default Solid tanpa fallback blur asinkron kosong.
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
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val miniHeightPx = with(density) { MupaPlayerSheetState.MiniPlayerHeight.toPx() }
        val miniBottomOffsetPx = with(density) { bottomBarClearance.toPx() }
        val collapsedTopPx = (fullHeightPx - miniBottomOffsetPx - miniHeightPx).coerceAtLeast(0f)
        sheetState.travelDistancePx = collapsedTopPx.coerceAtLeast(1f)

        val progressProvider = remember(sheetState) { { sheetState.progress } }
        val isSheetOpen = !sheetState.isCollapsed

        // Helper drag vertikal berbasis koordinat layar (nodeTop + localY) agar 1:1 tanpa feedback loop
        // dan TIDAK pernah memblokir sentuhan/scroll layar belakang saat Mini Player ciut!
        val miniDragUpModifier = Modifier.pointerInput(sheetState, collapsedTopPx) {
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (sheetState.progress >= 0.5f || playerState.isCinemaMode) return@awaitEachGesture
                var lastScreenY = collapsedTopPx * (1f - sheetState.progress) + down.position.y
                velocityTracker.resetTracking()
                velocityTracker.addPosition(down.uptimeMillis, Offset(down.position.x, lastScreenY))
                var dragStarted = false
                val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over < 0f) {
                        change.consume()
                        dragStarted = true
                        val curScreenY = collapsedTopPx * (1f - sheetState.progress) + change.position.y
                        val dy = curScreenY - lastScreenY
                        lastScreenY = curScreenY
                        sheetState.onDragStart()
                        sheetState.onDragDelta(dy)
                    }
                }
                if (dragChange != null && dragStarted) {
                    verticalDrag(dragChange.id) { change ->
                        change.consume()
                        val curScreenY = collapsedTopPx * (1f - sheetState.progress) + change.position.y
                        val dy = curScreenY - lastScreenY
                        lastScreenY = curScreenY
                        velocityTracker.addPosition(change.uptimeMillis, Offset(change.position.x, curScreenY))
                        sheetState.onDragDelta(dy)
                    }
                    sheetState.onDragEnd(velocityTracker.calculateVelocity().y)
                }
            }
        }

        val fullDragDownModifier = Modifier.pointerInput(sheetState, collapsedTopPx, sheetOverlayVisible, playerState.isCinemaMode) {
            val velocityTracker = VelocityTracker()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (sheetState.progress < 0.5f || playerState.isCinemaMode || sheetOverlayVisible) {
                    return@awaitEachGesture
                }
                var lastScreenY = collapsedTopPx * (1f - sheetState.progress) + down.position.y
                velocityTracker.resetTracking()
                velocityTracker.addPosition(down.uptimeMillis, Offset(down.position.x, lastScreenY))
                var dragStarted = false
                val dragChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, over ->
                    if (over > 0f) {
                        change.consume()
                        dragStarted = true
                        val curScreenY = collapsedTopPx * (1f - sheetState.progress) + change.position.y
                        val dy = curScreenY - lastScreenY
                        lastScreenY = curScreenY
                        sheetState.onDragStart()
                        sheetState.onDragDelta(dy)
                    }
                }
                if (dragChange != null && dragStarted) {
                    verticalDrag(dragChange.id) { change ->
                        change.consume()
                        val curScreenY = collapsedTopPx * (1f - sheetState.progress) + change.position.y
                        val dy = curScreenY - lastScreenY
                        lastScreenY = curScreenY
                        velocityTracker.addPosition(change.uptimeMillis, Offset(change.position.x, curScreenY))
                        sheetState.onDragDelta(dy)
                    }
                    sheetState.onDragEnd(velocityTracker.calculateVelocity().y)
                }
            }
        }

        // Lapisan wadah tanpa pointerInput global -> scroll & klik layar belakang 100% lancar saat Mini ciut!
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val p = progressProvider()
                    if (p > 0.005f) {
                        drawRect(Color.Black.copy(alpha = (0.60f * p).coerceIn(0f, 0.60f)))
                    }
                },
        ) {
            val fullBackdropBase = if (appearance.isOledMode) MupaPalette.OledBackground else MupaPalette.Background
            val coverAccent = appearance.effectiveCoverAccent

            // Sheet terpadu bergerak 0f (Mini) -> 1f (Full) hanya via graphicsLayer
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progressProvider()
                        translationY = collapsedTopPx * (1f - p)
                    },
            ) {
                // Lapisan 1: Backdrop Full Now Playing (hanya menangkap sentuhan saat sheet terbuka)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (isSheetOpen) {
                                Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = {},
                                    )
                                    .then(fullDragDownModifier)
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            val p = progressProvider()
                            alpha = (p / 0.18f).coerceIn(0f, 1f)
                        }
                        .background(fullBackdropBase)
                        .drawBehind {
                            if (!appearance.isOledMode && !appearance.isPerformanceMode) {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            coverAccent.primary.copy(alpha = 0.20f),
                                            coverAccent.secondary.copy(alpha = 0.08f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.24f),
                                        radius = size.width * 0.85f,
                                    ),
                                )
                            }
                        },
                )

                // Lapisan 2: Full Now Playing Content (halus tanpa lompatan artwork palsu)
                FullNowPlayingContent(
                    track = track,
                    stripArtistName = stripArtistName,
                    playerState = playerState,
                    progressProvider = progressProvider,
                    positionProvider = positionProvider,
                    maxWidthDp = containerMaxWidth,
                    onCollapse = { sheetState.collapse() },
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    onToggleLyrics = { onOpenOverlay(PlayerOverlayPane.LYRICS) },
                    onOpenQueue = { onOpenOverlay(PlayerOverlayPane.QUEUE) },
                    onOpenEqualizer = { onOpenOverlay(PlayerOverlayPane.EQUALIZER) },
                    onOpenCinema = onOpenCinema,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    onCycleSleepTimer = onCycleSleepTimer,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isSheetOpen) fullDragDownModifier else Modifier)
                        .layout { measurable, constraints ->
                            val p = progressProvider()
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (p > 0.04f) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                        .graphicsLayer {
                            val p = progressProvider()
                            alpha = ((p - 0.10f) / 0.42f).coerceIn(0f, 1f)
                        },
                )

                // Lapisan 3: Mini Player (hanya area 64dp yang menangkap tap & drag-up!)
                MiniPlayerBar(
                    track = track,
                    stripArtistName = stripArtistName,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    progressFraction = playerState.progressFraction,
                    onExpand = { sheetState.expand() },
                    onTogglePlayPause = onTogglePlayPause,
                    onSkipNext = onSkipNext,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(MupaPlayerSheetState.MiniPlayerHeight)
                        .then(miniDragUpModifier)
                        .layout { measurable, constraints ->
                            val p = progressProvider()
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (p < 0.28f) {
                                    placeable.placeRelative(0, 0)
                                }
                            }
                        }
                        .graphicsLayer {
                            val p = progressProvider()
                            alpha = (1f - p / 0.22f).coerceIn(0f, 1f)
                        },
                )

                // Lapisan 4: Sheet Antrean & Equalizer (Emphasized Decelerate, 2-frame pre-mount)
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
            }

            // Lapisan 5: Mode Cinema Layar Penuh (transisi halus tanpa lompatan)
            AnimatedVisibility(
                visible = playerState.isCinemaMode,
                enter = fadeIn(MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs)) +
                    slideInVertically(MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs)) { it / 18 },
                exit = fadeOut(MupaMotion.standardTween(MupaMotion.DurationShortMs)) +
                    slideOutVertically(MupaMotion.standardTween(MupaMotion.DurationShortMs)) { it / 18 },
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
 * Mini Player Mupa Calm (perbaikan B1, B6, B11):
 * - Permukaan solid 64dp radius 20dp + hairline 1dp.
 * - Tombol Play/Pause koral 40dp berikon #16161A (proporsional, tidak mendominasi).
 * - Progres tipis 2dp di dasar mini player.
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
) {
    val disp = remember(track, stripArtistName) { TrackDisplayRules.formatTrack(track, stripArtistName) }

    MupaGlassSurface(
        shape = MupaRadii.MiniPlayer,
        modifier = modifier
            .testTag("mupa_mini_player")
            .semantics { contentDescription = "Mini player: ${disp.title}, ${disp.subtitle}. Ketuk untuk membuka pemutar penuh" }
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
                MupaArtworkBox(
                    artworkUrl = track.artworkUrl,
                    contentDescription = disp.title,
                    size = 44.dp,
                    shape = MupaRadii.ArtworkSmall,
                    iconSize = 20.dp,
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

                // Tombol Play/Pause 40dp koral berikon gelap (perbaikan B6)
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

            // Garis progres 2dp di dasar Mini Player
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
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenCinema: () -> Unit,
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appearance = LocalMupaAppearance.current
    val disp = remember(track, stripArtistName) { TrackDisplayRules.formatTrack(track, stripArtistName) }
    val showInlineLyrics = playerState.activeOverlay == PlayerOverlayPane.LYRICS
    var topMenuOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .testTag("mupa_full_now_playing")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Pegangan drag-down
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Top bar: chevron, "Sedang diputar" + badge sumber (Lokal/Streaming), menu
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
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Sedang diputar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = disp.sourceLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MupaPalette.CoralSoft,
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
                        tint = MaterialTheme.colorScheme.onSurface,
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

        // Area Cover / Panel Lirik (crossfade 250ms, kontrol tetap terlihat di bawah)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showInlineLyrics,
                transitionSpec = {
                    fadeIn(MupaMotion.standardTween(250)) togetherWith fadeOut(MupaMotion.standardTween(200))
                },
                label = "coverLyricsCrossfade",
            ) { lyricsOpen ->
                if (lyricsOpen) {
                    MupaInlineLyricsPanel(
                        playerState = playerState,
                        positionProvider = positionProvider,
                        onSeekTo = onSeekTo,
                        onAdjustOffset = onAdjustLyricsOffset,
                        onResetOffset = onResetLyricsOffset,
                        onRetry = onToggleLyrics,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    val coverSize = (maxWidthDp - 48.dp).coerceIn(220.dp, 340.dp)
                    val coverTint = appearance.effectiveCoverAccent.primary
                    Box(
                        modifier = Modifier
                            .size(coverSize)
                            .drawBehind {
                                if (!appearance.isOledMode && !appearance.isPerformanceMode) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                coverTint.copy(alpha = 0.24f),
                                                Color.Transparent,
                                            ),
                                            center = Offset(size.width * 0.5f, size.height * 0.58f),
                                            radius = size.width * 0.64f,
                                        ),
                                    )
                                }
                            }
                            .graphicsLayer {
                                val p = progressProvider()
                                val s = 0.92f + 0.08f * p
                                scaleX = s
                                scaleY = s
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MupaArtworkBox(
                            artworkUrl = track.artworkUrl,
                            contentDescription = disp.title,
                            size = coverSize,
                            shape = MupaRadii.ArtworkLarge,
                            iconSize = 68.dp,
                        )
                    }
                }
            }
        }

        // Judul 24 SemiBold + Artis 16 sekunder + Tombol Favorit
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = disp.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    tint = if (track.isFavorite) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Seekbar custom (4dp -> 6dp saat drag, thumb saat drag, segmen buffer hanya Streaming, waktu tabular)
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

        Spacer(modifier = Modifier.height(16.dp))

        // 5 Kontrol Transport Utama (Play/Pause koral 72dp)
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
                    tint = if (playerState.isShuffleEnabled) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MupaPalette.CoralPrimary)
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
                    tint = MaterialTheme.colorScheme.onSurface,
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
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Baris Aksi: Lirik, Antrean (+badge), EQ, Cinema (hanya ikon + contentDescription)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NowPlayingActionIcon(
                icon = Icons.Rounded.Lyrics,
                description = "Lirik",
                active = showInlineLyrics,
                onClick = onToggleLyrics,
            )
            BadgedBox(
                badge = {
                    if (playerState.queue.isNotEmpty()) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ) {
                            Text("${playerState.queue.size}")
                        }
                    }
                },
            ) {
                NowPlayingActionIcon(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    description = "Antrean putar",
                    active = playerState.activeOverlay == PlayerOverlayPane.QUEUE,
                    onClick = onOpenQueue,
                )
            }
            NowPlayingActionIcon(
                icon = Icons.Rounded.GraphicEq,
                description = "Equalizer 5-band",
                active = playerState.activeOverlay == PlayerOverlayPane.EQUALIZER || playerState.eqPreset != com.xeno.mupa.playback.eq.EqPreset.FLAT,
                onClick = onOpenEqualizer,
            )
            NowPlayingActionIcon(
                icon = Icons.Rounded.Movie,
                description = "Mode Cinema",
                active = playerState.isCinemaMode,
                onClick = onOpenCinema,
            )
        }
    }
}

@Composable
private fun NowPlayingActionIcon(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (active) MupaPalette.CoralTint14 else Color.Transparent),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) MupaPalette.CoralPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
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

                    // Base track
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.14f),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, h),
                        cornerRadius = radius,
                    )

                    // Segmen buffer samar hanya untuk Streaming
                    if (showBuffer && bufferedFraction > 0f) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.24f),
                            topLeft = Offset(0f, y),
                            size = Size(size.width * bufferedFraction.coerceIn(0f, 1f), h),
                            cornerRadius = radius,
                        )
                    }

                    // Active progress koral
                    val activeW = size.width * displayedFraction
                    if (activeW > 0f) {
                        drawRoundRect(
                            color = MupaPalette.CoralPrimary,
                            topLeft = Offset(0f, y),
                            size = Size(activeW, h),
                            cornerRadius = radius,
                        )
                    }

                    // Thumb hanya saat drag
                    if (thumbAlpha > 0.01f) {
                        drawCircle(
                            color = MupaPalette.CoralPrimary.copy(alpha = thumbAlpha),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrackDisplayRules.formatDuration(durationMs)?.let { total ->
                Text(
                    text = total,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMS_FEATURE),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
