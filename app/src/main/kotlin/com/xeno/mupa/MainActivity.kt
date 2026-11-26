package com.xeno.mupa

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.ui.MupaApp
import com.xeno.mupa.ui.MupaAppViewModel
import com.xeno.mupa.ui.player.MupaPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val appViewModel: MupaAppViewModel = hiltViewModel()
            val playerViewModel: MupaPlayerViewModel = hiltViewModel()
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

            keepSplash = !uiState.isReady

            val resolvedAppearance = uiState.appearance.copy(
                dynamicCoverAccent = if (playerState.currentTrack != null) {
                    playerState.coverAccent
                } else {
                    uiState.appearance.dynamicCoverAccent
                },
            )

            MupaTheme(appearance = resolvedAppearance) {
                MupaApp(
                    uiState = uiState,
                    playerState = playerState,
                    onExitApp = { finish() },
                    onPlayTracks = playerViewModel::playQueue,
                    onGlassStyleChange = appViewModel::setGlassStyle,
                    onOledModeChange = appViewModel::setOledMode,
                    onAuroraEnabledChange = appViewModel::setAuroraEnabled,
                    onPerformanceModeChange = appViewModel::setPerformanceMode,
                    onStripArtistNameChange = appViewModel::setStripArtistName,
                    onOpenEditFavoriteArtists = appViewModel::openEditFavoriteArtists,
                    onCloseEditFavoriteArtists = appViewModel::closeEditFavoriteArtists,
                    onTogglePlayPause = playerViewModel::togglePlayPause,
                    onSeekTo = playerViewModel::seekTo,
                    onSkipNext = playerViewModel::skipNext,
                    onSkipPrevious = playerViewModel::skipPrevious,
                    onToggleShuffle = playerViewModel::toggleShuffle,
                    onCycleRepeat = playerViewModel::cycleRepeatMode,
                    onToggleFavorite = playerViewModel::toggleFavoriteCurrent,
                    onOpenOverlay = playerViewModel::openOverlay,
                    onCloseOverlay = playerViewModel::closeOverlay,
                    onAdjustLyricsOffset = playerViewModel::adjustLyricsOffset,
                    onResetLyricsOffset = playerViewModel::resetLyricsOffset,
                    onCycleSleepTimer = playerViewModel::cycleSleepTimer,
                    onSelectEqPreset = playerViewModel::selectEqPreset,
                    onEqBandChange = playerViewModel::updateEqBand,
                    onResetEq = playerViewModel::resetEq,
                    onSelectQueueIndex = playerViewModel::selectQueueIndex,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    onRemoveQueueItem = playerViewModel::removeQueueItem,
                )
            }
        }
    }
}
