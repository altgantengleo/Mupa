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
        installSplashScreen()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            val appViewModel: MupaAppViewModel = hiltViewModel()
            val playerViewModel: MupaPlayerViewModel = hiltViewModel()
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

            val resolvedAppearance = uiState.appearance.copy(
                dynamicCoverAccent = if (playerState.currentTrack != null &&
                    uiState.coverAccentPreset == com.xeno.mupa.ui.CoverAccentPreset.EMBER_DEFAULT
                ) {
                    playerState.coverAccent
                } else {
                    uiState.coverAccentPreset.accent
                },
            )

            MupaTheme(appearance = resolvedAppearance) {
                MupaApp(
                    uiState = uiState,
                    playerState = playerState,
                    onGlassStyleChange = appViewModel::setGlassStyle,
                    onOledModeChange = appViewModel::setOledMode,
                    onAuroraEnabledChange = appViewModel::setAuroraEnabled,
                    onPerformanceModeChange = appViewModel::setPerformanceMode,
                    onCoverAccentPresetChange = appViewModel::setCoverAccentPreset,
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
                    onSelectQueueIndex = playerViewModel::selectQueueIndex,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    onRemoveQueueItem = playerViewModel::removeQueueItem,
                )
            }
        }
    }
}
