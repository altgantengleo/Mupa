package com.xeno.mupa

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.playback.MupaPlaybackService
import com.xeno.mupa.ui.MupaApp
import com.xeno.mupa.ui.MupaAppViewModel
import com.xeno.mupa.ui.player.MupaPlayerViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val openNowPlayingSignal = mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MupaPlaybackService.EXTRA_OPEN_NOW_PLAYING, false)) {
            openNowPlayingSignal.intValue += 1
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(MupaPlaybackService.EXTRA_OPEN_NOW_PLAYING, false) == true) {
            openNowPlayingSignal.intValue += 1
        }

        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        setContent {
            val appViewModel: MupaAppViewModel = hiltViewModel()
            val playerViewModel: MupaPlayerViewModel = hiltViewModel()
            val uiState by appViewModel.uiState.collectAsStateWithLifecycle()
            val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()

            keepSplash = !uiState.isReady

            val resolvedAppearance = uiState.appearance.copy(
                dynamicCoverAccent = if (playerState.currentTrack != null) {
                    playerState.ultraDynamicPalette.toDynamicCoverAccent()
                } else {
                    uiState.appearance.dynamicCoverAccent
                },
                ultraDynamicPalette = if (playerState.currentTrack != null) {
                    playerState.ultraDynamicPalette
                } else {
                    uiState.appearance.ultraDynamicPalette
                },
            )

            MupaTheme(appearance = resolvedAppearance) {
                MupaApp(
                    uiState = uiState,
                    playerState = playerState,
                    openNowPlayingSignal = openNowPlayingSignal.intValue,
                    onExitApp = { finish() },
                    onPlayTracks = playerViewModel::playQueue,
                    onPlayFromSearch = playerViewModel::playFromSearch,
                    onPlayShuffled = playerViewModel::playShuffled,
                    onActiveSourceChange = appViewModel::setActiveSource,
                    onImportLocalFiles = appViewModel::importLocalFiles,
                    onImportLocalFolder = appViewModel::importLocalFolder,
                    onGlassStyleChange = appViewModel::setGlassStyle,
                    onOledModeChange = appViewModel::setOledMode,
                    onAuroraEnabledChange = appViewModel::setAuroraEnabled,
                    onPerformanceModeChange = appViewModel::setPerformanceMode,
                    onStripArtistNameChange = appViewModel::setStripArtistName,
                    onCrossfadeSecondsChange = appViewModel::setCrossfadeSeconds,
                    onPlayerSwipeSensitivityChange = appViewModel::setPlayerSwipeSensitivity,
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
                    onOpenCinema = playerViewModel::openCinemaMode,
                    onCloseCinema = playerViewModel::closeCinemaMode,
                    onRetryLyrics = { playerViewModel.fetchLyricsForCurrentTrack(forceRefresh = true) },
                    positionProvider = playerViewModel::currentPlaybackPositionMs,
                    onAdjustLyricsOffset = playerViewModel::adjustLyricsOffset,
                    onResetLyricsOffset = playerViewModel::resetLyricsOffset,
                    onCycleSleepTimer = playerViewModel::cycleSleepTimer,
                    onSelectEqPreset = playerViewModel::selectEqPreset,
                    onEqBandChange = playerViewModel::updateEqBand,
                    onResetEq = playerViewModel::resetEq,
                    onSelectQueueIndex = playerViewModel::selectQueueIndex,
                    onMoveQueueItem = playerViewModel::moveQueueItem,
                    onRemoveQueueItem = playerViewModel::removeQueueItem,
                    onClearUpcomingQueue = playerViewModel::clearUpcomingQueue,
                    onRescanLocalFolders = appViewModel::rescanLocalFolders,
                    onClearAudioCache = appViewModel::clearAudioCache,
                    onClearImageAndLyricsCache = appViewModel::clearImageAndLyricsCache,
                    onExportBackupUri = { uri ->
                        appViewModel.exportBackupToUri(
                            uri = uri,
                            eqPreset = playerState.eqPreset,
                            eqGainsDb = playerState.eqGainsDb,
                        )
                    },
                    onImportBackupUri = { uri ->
                        appViewModel.importBackupFromUri(uri) { preset, gains ->
                            playerViewModel.selectEqPreset(preset)
                            gains.forEachIndexed { idx, gain -> playerViewModel.updateEqBand(idx, gain) }
                        }
                    },
                )
            }
        }
    }
}
