package com.xeno.mupa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xeno.mupa.core.designsystem.component.UltraDynamicBackground
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.LocalMupaHazeState
import com.xeno.mupa.core.designsystem.theme.LocalPlayerChromeInsets
import com.xeno.mupa.core.designsystem.theme.PlayerChromeInsets
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.xeno.mupa.navigation.MupaFloatingBottomBar
import com.xeno.mupa.navigation.MupaNavHost
import com.xeno.mupa.navigation.MupaTopLevelDestination
import com.xeno.mupa.navigation.navigateToTopLevel
import com.xeno.mupa.ui.onboarding.ArtistOnboardingScreen
import com.xeno.mupa.ui.onboarding.ArtistOnboardingViewModel
import com.xeno.mupa.ui.player.MupaPlayerMorphMath
import com.xeno.mupa.ui.player.MupaPlayerOverlayHost
import com.xeno.mupa.ui.player.MupaPlayerSheetState
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.player.PlayerOverlayPane
import com.xeno.mupa.ui.player.rememberMupaPlayerSheetState

@Composable
fun MupaApp(
    uiState: MupaAppUiState,
    playerState: MupaPlayerUiState,
    onExitApp: () -> Unit,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onPlayFromSearch: (Track) -> Unit = { onPlayTracks(listOf(it), 0) },
    onPlayShuffled: (List<Track>, com.xeno.mupa.core.model.QueueSource) -> Unit = { tracks, _ -> onPlayTracks(tracks, 0) },
    onActiveSourceChange: (com.xeno.mupa.core.model.AudioSourceType) -> Unit = {},
    onImportLocalFiles: (List<android.net.Uri>) -> Unit = {},
    onImportLocalFolder: (android.net.Uri) -> Unit = {},
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    onOpenEditFavoriteArtists: () -> Unit,
    onCloseEditFavoriteArtists: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenOverlay: (PlayerOverlayPane) -> Unit,
    onCloseOverlay: () -> Unit,
    onOpenCinema: () -> Unit = {},
    onCloseCinema: () -> Unit = {},
    onRetryLyrics: () -> Unit = {},
    positionProvider: () -> Long = { playerState.positionMs },
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onSelectEqPreset: (com.xeno.mupa.playback.eq.EqPreset) -> Unit,
    onEqBandChange: (Int, Float) -> Unit,
    onResetEq: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
    onClearUpcomingQueue: () -> Unit = {},
    onRescanLocalFolders: () -> Unit = {},
    onClearAudioCache: () -> Unit = {},
    onClearImageAndLyricsCache: () -> Unit = {},
    onExportBackupUri: (android.net.Uri) -> Unit = {},
    onImportBackupUri: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!uiState.isReady) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    // Onboarding Artis Favorit wajib saat pertama kali dibuka, atau mode edit dari Akun/Pengaturan
    val showOnboarding = !uiState.preferences.onboardingCompleted || uiState.isEditingFavoriteArtists
    if (showOnboarding) {
        val isEditMode = uiState.preferences.onboardingCompleted && uiState.isEditingFavoriteArtists
        val onboardingVm: ArtistOnboardingViewModel = hiltViewModel()
        val onboardingState by onboardingVm.uiState.collectAsStateWithLifecycle()

        ArtistOnboardingScreen(
            uiState = onboardingState,
            isEditMode = isEditMode,
            onQueryChange = onboardingVm::onQueryChange,
            onToggleArtist = onboardingVm::toggleArtist,
            onRetry = onboardingVm::retry,
            onSubmit = {
                onboardingVm.submitSelection(isEditMode = isEditMode) {
                    if (isEditMode) onCloseEditFavoriteArtists()
                }
            },
            onNavigateBackOrExit = {
                if (isEditMode) onCloseEditFavoriteArtists() else onExitApp()
            },
            modifier = modifier,
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = MupaTopLevelDestination.entries.firstOrNull {
        it.route == currentRoute
    }
    val showBottomBar = currentDestination != null

    val playerSheetState = rememberMupaPlayerSheetState()
    val density = LocalDensity.current

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val hasActiveTrack = playerState.currentTrack != null && showBottomBar
    val navBarTotal = if (showBottomBar) 64.dp + 12.dp else 0.dp
    val miniTotal = if (hasActiveTrack) MupaPlayerSheetState.MiniPlayerHeight + 8.dp else 0.dp
    val chromeHeight = navBarBottom + navBarTotal + miniTotal
    val chromeInsets = PlayerChromeInsets(
        topInset = statusBarTop,
        bottomChromeHeight = chromeHeight,
        bottomContentPadding = chromeHeight + 28.dp,
        hasMiniPlayer = hasActiveTrack,
    )
    val screenContentPadding = chromeInsets.screenPaddingValues()
    val hazeState = remember { HazeState() }
    val bgColor = MaterialTheme.colorScheme.background

    CompositionLocalProvider(
        LocalPlayerChromeInsets provides chromeInsets,
        LocalMupaHazeState provides hazeState,
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .hazeSource(state = hazeState),
            ) {
                UltraDynamicBackground(
                    palette = LocalMupaAppearance.current.effectiveUltraDynamicPalette,
                    headerOnly = true,
                )

                MupaNavHost(
                    navController = navController,
                    uiState = uiState,
                    currentPlayingTrackId = playerState.currentTrack?.id,
                    contentPadding = screenContentPadding,
                    onPlayTracks = onPlayTracks,
                    onPlayFromSearch = onPlayFromSearch,
                    onPlayShuffled = onPlayShuffled,
                    onActiveSourceChange = onActiveSourceChange,
                    onImportLocalFiles = onImportLocalFiles,
                    onImportLocalFolder = onImportLocalFolder,
                    onGlassStyleChange = onGlassStyleChange,
                    onOledModeChange = onOledModeChange,
                    onAuroraEnabledChange = onAuroraEnabledChange,
                    onPerformanceModeChange = onPerformanceModeChange,
                    onStripArtistNameChange = onStripArtistNameChange,
                    onCrossfadeSecondsChange = onCrossfadeSecondsChange,
                    onEditFavoriteArtists = onOpenEditFavoriteArtists,
                    continueTrack = playerState.currentTrack,
                    continueQueue = playerState.queue,
                    continueIndex = playerState.currentIndex,
                    eqPreset = playerState.eqPreset,
                    eqGainsDb = playerState.eqGainsDb,
                    onSelectEqPreset = onSelectEqPreset,
                    onEqBandChange = onEqBandChange,
                    onResetEq = onResetEq,
                    onRescanLocalFolders = onRescanLocalFolders,
                    onClearAudioCache = onClearAudioCache,
                    onClearImageAndLyricsCache = onClearImageAndLyricsCache,
                    onExportBackupUri = onExportBackupUri,
                    onImportBackupUri = onImportBackupUri,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = playerSheetState.progress
                            val scale = 1f - 0.03f * p
                            scaleX = scale
                            scaleY = scale
                        },
                )

                // Scrim status bar global agar konten scroll di Cari/Pustaka/Akun/Pengaturan tidak terpotong di bawah jam (N3)
                if (currentRoute != MupaTopLevelDestination.HOME.route) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(statusBarTop + 18.dp)
                            .testTag("mupa_top_status_scrim")
                            .graphicsLayer {
                                val p = playerSheetState.progress
                                alpha = (1f - p / 0.30f).coerceIn(0f, 1f)
                            }
                            .background(
                                Brush.verticalGradient(
                                    0.0f to bgColor.copy(alpha = 0.98f),
                                    0.55f to bgColor.copy(alpha = 0.80f),
                                    1.0f to Color.Transparent,
                                ),
                            ),
                    )
                }
            }

            // Scrim gradasi bawah menutup penuh celah Mini Player <-> Bottom Nav dan belakang Liquid+ (perbaikan B1 & N2)
            if (showBottomBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(chromeHeight + 28.dp)
                        .graphicsLayer {
                            val p = playerSheetState.progress
                            alpha = (1f - p / 0.35f).coerceIn(0f, 1f)
                        }
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.16f to bgColor.copy(alpha = 0.90f),
                                0.45f to bgColor.copy(alpha = 0.97f),
                                1.0f to bgColor.copy(alpha = 0.99f),
                            ),
                        ),
                )

                MupaPlayerOverlayHost(
                    playerState = playerState,
                    sheetState = playerSheetState,
                    stripArtistName = uiState.preferences.stripArtistName,
                    bottomBarClearance = navBarBottom + 64.dp + 12.dp + 8.dp,
                    positionProvider = positionProvider,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onSkipNext = onSkipNext,
                    onSkipPrevious = onSkipPrevious,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onToggleFavorite = onToggleFavorite,
                    onOpenOverlay = onOpenOverlay,
                    onCloseOverlay = onCloseOverlay,
                    onOpenCinema = onOpenCinema,
                    onCloseCinema = onCloseCinema,
                    onRetryLyrics = onRetryLyrics,
                    onAdjustLyricsOffset = onAdjustLyricsOffset,
                    onResetLyricsOffset = onResetLyricsOffset,
                    onCycleSleepTimer = onCycleSleepTimer,
                    onSelectEqPreset = onSelectEqPreset,
                    onEqBandChange = onEqBandChange,
                    onResetEq = onResetEq,
                    onSelectQueueIndex = onSelectQueueIndex,
                    onMoveQueueItem = onMoveQueueItem,
                    onRemoveQueueItem = onRemoveQueueItem,
                    onClearUpcomingQueue = onClearUpcomingQueue,
                )

                val navSlidePx = with(density) { (navBarBottom + 88.dp).toPx() }
                MupaFloatingBottomBar(
                    currentDestination = currentDestination,
                    onSelectDestination = { destination ->
                        if (destination != currentDestination) {
                            navController.navigateToTopLevel(destination)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .graphicsLayer {
                            val p = playerSheetState.progress
                            translationY = navSlidePx * MupaPlayerMorphMath.bottomNavSlideFraction(p)
                            alpha = MupaPlayerMorphMath.bottomNavAlpha(p)
                        },
                )
            }
        }
    }
}
