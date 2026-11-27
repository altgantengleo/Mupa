package com.xeno.mupa.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.local.LocalImportRules
import com.xeno.mupa.playback.eq.EqPreset
import com.xeno.mupa.ui.MupaAppUiState
import com.xeno.mupa.ui.player.MupaEqualizerOverlay
import com.xeno.mupa.ui.screens.AccountScreen
import com.xeno.mupa.ui.screens.AttributionScreen
import com.xeno.mupa.ui.screens.HomeScreen
import com.xeno.mupa.ui.screens.HomeViewModel
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryViewModel
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchViewModel
import com.xeno.mupa.ui.screens.SettingsScreen

fun NavHostController.navigateToTopLevel(destination: MupaTopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun MupaNavHost(
    navController: NavHostController,
    uiState: MupaAppUiState,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onPlayTracks: (List<Track>, Int) -> Unit,
    onPlayFromSearch: (Track) -> Unit = { onPlayTracks(listOf(it), 0) },
    onPlayShuffled: (List<Track>, QueueSource) -> Unit = { tracks, _ -> onPlayTracks(tracks, 0) },
    onActiveSourceChange: (AudioSourceType) -> Unit,
    onImportLocalFiles: (List<android.net.Uri>) -> Unit,
    onImportLocalFolder: (android.net.Uri) -> Unit,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    onCrossfadeSecondsChange: (Int) -> Unit = {},
    onEditFavoriteArtists: () -> Unit,
    continueTrack: Track? = null,
    continueQueue: List<Track> = emptyList(),
    continueIndex: Int = 0,
    eqPreset: EqPreset = EqPreset.FLAT,
    eqGainsDb: List<Float> = EqPreset.FLAT.gainsDb,
    onSelectEqPreset: (EqPreset) -> Unit = {},
    onEqBandChange: (Int, Float) -> Unit = { _, _ -> },
    onResetEq: () -> Unit = {},
    onRescanLocalFolders: () -> Unit = {},
    onClearAudioCache: () -> Unit = {},
    onClearImageAndLyricsCache: () -> Unit = {},
    onExportBackupUri: (android.net.Uri) -> Unit = {},
    onImportBackupUri: (android.net.Uri) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val tabOrder = MupaTopLevelDestination.entries.associate { it.route to it.ordinal }
    val stripArtist = uiState.preferences.stripArtistName

    val pickFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) onImportLocalFiles(uris)
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) onImportLocalFolder(treeUri)
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) onExportBackupUri(uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImportBackupUri(uri)
    }

    val launchFileImport: () -> Unit = {
        pickFilesLauncher.launch(LocalImportRules.SUPPORTED_MIME_TYPES)
    }
    val launchFolderImport: () -> Unit = {
        pickFolderLauncher.launch(null)
    }

    val subRoutes = setOf(
        MupaRoutes.SETTINGS,
        MupaRoutes.ATTRIBUTION,
        MupaRoutes.EQUALIZER,
    )

    NavHost(
        navController = navController,
        startDestination = MupaTopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = {
            val targetRoute = targetState.destination.route
            if (targetRoute in subRoutes) {
                // Buka Pengaturan / Atribusi / Equalizer: luncur halus dari kanan + fade + skala tenang
                slideInHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.28f).toInt() },
                ) + fadeIn(
                    animationSpec = MupaMotion.standardTween(220),
                ) + scaleIn(
                    initialScale = 0.97f,
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                )
            } else {
                val from = tabOrder[initialState.destination.route] ?: 0
                val to = tabOrder[targetRoute] ?: 0
                val direction = if (to >= from) 1 else -1
                slideInHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                    initialOffsetX = { fullWidth -> (fullWidth / 12) * direction },
                ) + fadeIn(
                    animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                )
            }
        },
        exitTransition = {
            val targetRoute = targetState.destination.route
            if (targetRoute in subRoutes) {
                // Layar sebelumnya mundur lembut ke kiri dengan paralaks saat sub-layar dibuka
                slideOutHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                    targetOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
                ) + fadeOut(
                    animationSpec = MupaMotion.standardTween(200),
                ) + scaleOut(
                    targetScale = 0.96f,
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationSheetMs),
                )
            } else {
                val from = tabOrder[initialState.destination.route] ?: 0
                val to = tabOrder[targetRoute] ?: 0
                val direction = if (to >= from) -1 else 1
                slideOutHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                    targetOffsetX = { fullWidth -> (fullWidth / 12) * direction },
                ) + fadeOut(
                    animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                )
            }
        },
        popEnterTransition = {
            val initialRoute = initialState.destination.route
            if (initialRoute in subRoutes) {
                // Kembali dari Pengaturan / Atribusi / Equalizer: layar asal masuk lembut dari kiri
                slideInHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(320),
                    initialOffsetX = { fullWidth -> -(fullWidth * 0.12f).toInt() },
                ) + fadeIn(
                    animationSpec = MupaMotion.standardTween(220),
                ) + scaleIn(
                    initialScale = 0.96f,
                    animationSpec = MupaMotion.emphasizedTween(320),
                )
            } else {
                fadeIn(animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs))
            }
        },
        popExitTransition = {
            val initialRoute = initialState.destination.route
            if (initialRoute in subRoutes) {
                // Tutup Pengaturan / Atribusi / Equalizer: luncur halus keluar ke kanan
                slideOutHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(300),
                    targetOffsetX = { fullWidth -> (fullWidth * 0.28f).toInt() },
                ) + fadeOut(
                    animationSpec = MupaMotion.standardTween(190),
                ) + scaleOut(
                    targetScale = 0.97f,
                    animationSpec = MupaMotion.emphasizedTween(300),
                )
            } else {
                fadeOut(animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs))
            }
        },
    ) {
        composable(MupaTopLevelDestination.HOME.route) {
            val vm: HomeViewModel = hiltViewModel()
            val homeState by vm.uiState.collectAsStateWithLifecycle()
            HomeScreen(
                uiState = homeState,
                stripArtistName = stripArtist,
                contentPadding = contentPadding,
                onPlayTrack = onPlayTracks,
                onReshuffleMix = vm::reshuffleMix,
                onRetry = vm::retry,
                onImportLocalFiles = launchFileImport,
                onImportLocalFolder = launchFolderImport,
                onToggleSource = onActiveSourceChange,
                continueTrack = continueTrack,
                continueQueue = continueQueue,
                continueIndex = continueIndex,
            )
        }
        composable(MupaTopLevelDestination.SEARCH.route) {
            val vm: SearchViewModel = hiltViewModel()
            val searchState by vm.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                uiState = searchState,
                stripArtistName = stripArtist,
                currentPlayingTrackId = currentPlayingTrackId,
                contentPadding = contentPadding,
                onQueryChange = vm::onQueryChange,
                onSelectScope = vm::onSelectScope,
                onToggleSource = vm::onToggleSource,
                onSwitchSource = vm::onSwitchSource,
                onSubmitQuery = vm::onSubmitQuery,
                onRemoveRecent = vm::onRemoveRecent,
                onPlayTrack = onPlayTracks,
                onPlayFromSearch = onPlayFromSearch,
                onPlayShuffled = onPlayShuffled,
                onOpenArtist = vm::openArtist,
                onOpenRelease = vm::openRelease,
                onCloseDetail = vm::closeDetail,
                onRetry = vm::retry,
                onImportLocalFiles = launchFileImport,
                favoriteArtists = uiState.favoriteArtists,
            )
        }
        composable(MupaTopLevelDestination.LIBRARY.route) {
            val vm: LibraryViewModel = hiltViewModel()
            val libraryState by vm.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = libraryState,
                stripArtistName = stripArtist,
                currentPlayingTrackId = currentPlayingTrackId,
                contentPadding = contentPadding,
                onSwitchSource = vm::switchSource,
                onOpenCategory = vm::openCategory,
                onCloseCategory = vm::closeCategory,
                onSelectSort = vm::selectSort,
                onSetFavoritesOnly = vm::setFavoritesOnly,
                onImportLocalFiles = launchFileImport,
                onImportLocalFolder = launchFolderImport,
                onRescanFolders = vm::rescanFolders,
                onRemoveLocalTrack = vm::removeLocalTrack,
                onToggleFavoriteTrack = vm::toggleLocalFavorite,
                onCreatePlaylist = vm::createPlaylist,
                onPlayTrack = onPlayTracks,
                onPlayShuffled = onPlayShuffled,
                onRetry = vm::retry,
            )
        }
        composable(MupaTopLevelDestination.ACCOUNT.route) {
            AccountScreen(
                preferences = uiState.preferences,
                favoriteArtists = uiState.favoriteArtists,
                totalTracksCount = uiState.totalTracksCount,
                favoriteTracksCount = uiState.favoriteTracksCount,
                contentPadding = contentPadding,
                onEditFavoriteArtists = onEditFavoriteArtists,
                onOpenSettings = { navController.navigate(MupaRoutes.SETTINGS) },
            )
        }
        composable(MupaRoutes.SETTINGS) {
            SettingsScreen(
                preferences = uiState.preferences,
                connectedFoldersCount = uiState.connectedFoldersCount,
                audioCacheBytes = uiState.audioCacheBytes,
                statusMessage = uiState.settingsStatusMessage,
                onBack = { navController.popBackStack() },
                onGlassStyleChange = onGlassStyleChange,
                onOledModeChange = onOledModeChange,
                onUltraDynamicChange = onAuroraEnabledChange,
                onPerformanceModeChange = onPerformanceModeChange,
                onStripArtistNameChange = onStripArtistNameChange,
                onOpenEqualizer = { navController.navigate(MupaRoutes.EQUALIZER) },
                onCrossfadeSecondsChange = onCrossfadeSecondsChange,
                onClearAudioCache = onClearAudioCache,
                onImportLocalFolder = launchFolderImport,
                onRescanLocalFolders = onRescanLocalFolders,
                onExportBackup = { exportBackupLauncher.launch("mupa-backup.json") },
                onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "text/plain")) },
                onClearImageAndLyricsCache = onClearImageAndLyricsCache,
                onOpenAttribution = { navController.navigate(MupaRoutes.ATTRIBUTION) },
            )
        }
        composable(MupaRoutes.ATTRIBUTION) {
            AttributionScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(MupaRoutes.EQUALIZER) {
            MupaEqualizerOverlay(
                activePreset = eqPreset,
                bandGainsDb = eqGainsDb,
                onSelectPreset = onSelectEqPreset,
                onBandGainChange = onEqBandChange,
                onReset = onResetEq,
                onClose = { navController.popBackStack() },
            )
        }
    }
}
