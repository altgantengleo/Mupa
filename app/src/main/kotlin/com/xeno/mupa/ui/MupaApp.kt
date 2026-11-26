package com.xeno.mupa.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xeno.mupa.core.designsystem.component.MupaAuroraBackground
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.navigation.MupaFloatingBottomBar
import com.xeno.mupa.navigation.MupaNavHost
import com.xeno.mupa.navigation.MupaTopLevelDestination
import com.xeno.mupa.navigation.navigateToTopLevel
import com.xeno.mupa.ui.onboarding.ArtistOnboardingScreen
import com.xeno.mupa.ui.onboarding.ArtistOnboardingViewModel
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
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
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
    onAdjustLyricsOffset: (Long) -> Unit,
    onResetLyricsOffset: () -> Unit,
    onCycleSleepTimer: () -> Unit,
    onSelectEqPreset: (com.xeno.mupa.playback.eq.EqPreset) -> Unit,
    onEqBandChange: (Int, Float) -> Unit,
    onResetEq: () -> Unit,
    onSelectQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int) -> Unit,
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
    val miniPlayerClearance = if (hasActiveTrack) {
        MupaPlayerSheetState.MiniPlayerHeight + 12.dp
    } else {
        0.dp
    }
    val bottomChrome = if (showBottomBar) MupaPlayerSheetState.BottomNavReservedHeight else 0.dp
    val screenContentPadding = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = statusBarTop + 16.dp,
        bottom = navBarBottom + bottomChrome + miniPlayerClearance + 16.dp,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        MupaAuroraBackground()

        MupaNavHost(
            navController = navController,
            uiState = uiState,
            contentPadding = screenContentPadding,
            onPlayTracks = onPlayTracks,
            onGlassStyleChange = onGlassStyleChange,
            onOledModeChange = onOledModeChange,
            onAuroraEnabledChange = onAuroraEnabledChange,
            onPerformanceModeChange = onPerformanceModeChange,
            onStripArtistNameChange = onStripArtistNameChange,
            onEditFavoriteArtists = onOpenEditFavoriteArtists,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = playerSheetState.progress
                    val scale = 1f - 0.03f * p
                    scaleX = scale
                    scaleY = scale
                },
        )

        if (showBottomBar) {
            MupaPlayerOverlayHost(
                playerState = playerState,
                sheetState = playerSheetState,
                bottomBarClearance = navBarBottom + MupaPlayerSheetState.BottomNavReservedHeight + 6.dp,
                onTogglePlayPause = onTogglePlayPause,
                onSeekTo = onSeekTo,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
                onToggleFavorite = onToggleFavorite,
                onOpenOverlay = onOpenOverlay,
                onCloseOverlay = onCloseOverlay,
                onAdjustLyricsOffset = onAdjustLyricsOffset,
                onResetLyricsOffset = onResetLyricsOffset,
                onCycleSleepTimer = onCycleSleepTimer,
                onSelectEqPreset = onSelectEqPreset,
                onEqBandChange = onEqBandChange,
                onResetEq = onResetEq,
                onSelectQueueIndex = onSelectQueueIndex,
                onMoveQueueItem = onMoveQueueItem,
                onRemoveQueueItem = onRemoveQueueItem,
            )

            val navSlidePx = with(density) { (navBarBottom + MupaPlayerSheetState.BottomNavReservedHeight + 24.dp).toPx() }
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
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .graphicsLayer {
                        val p = playerSheetState.progress
                        translationY = navSlidePx * p
                        alpha = (1f - p / 0.50f).coerceIn(0f, 1f)
                    },
            )
        }
    }
}
