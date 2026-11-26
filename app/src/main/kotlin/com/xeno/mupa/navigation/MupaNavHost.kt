package com.xeno.mupa.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.local.LocalImportRules
import com.xeno.mupa.ui.MupaAppUiState
import com.xeno.mupa.ui.screens.AccountScreen
import com.xeno.mupa.ui.screens.HomeScreen
import com.xeno.mupa.ui.screens.HomeViewModel
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryViewModel
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchViewModel

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
    onActiveSourceChange: (AudioSourceType) -> Unit,
    onImportLocalFiles: (List<android.net.Uri>) -> Unit,
    onImportLocalFolder: (android.net.Uri) -> Unit,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onStripArtistNameChange: (Boolean) -> Unit,
    onEditFavoriteArtists: () -> Unit,
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

    val launchFileImport: () -> Unit = {
        pickFilesLauncher.launch(LocalImportRules.SUPPORTED_MIME_TYPES)
    }
    val launchFolderImport: () -> Unit = {
        pickFolderLauncher.launch(null)
    }

    NavHost(
        navController = navController,
        startDestination = MupaTopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = {
            val from = tabOrder[initialState.destination.route] ?: 0
            val to = tabOrder[targetState.destination.route] ?: 0
            val direction = if (to >= from) 1 else -1
            slideInHorizontally(
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                initialOffsetX = { fullWidth -> (fullWidth / 14) * direction },
            ) + fadeIn(
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
            )
        },
        exitTransition = {
            val from = tabOrder[initialState.destination.route] ?: 0
            val to = tabOrder[targetState.destination.route] ?: 0
            val direction = if (to >= from) -1 else 1
            slideOutHorizontally(
                animationSpec = MupaMotion.emphasizedTween(MupaMotion.DurationMediumMs),
                targetOffsetX = { fullWidth -> (fullWidth / 14) * direction },
            ) + fadeOut(
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
            )
        },
        popEnterTransition = {
            fadeIn(animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs))
        },
        popExitTransition = {
            fadeOut(animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs))
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
                onSubmitQuery = vm::onSubmitQuery,
                onRemoveRecent = vm::onRemoveRecent,
                onPlayTrack = onPlayTracks,
                onRetry = vm::retry,
                onImportLocalFiles = launchFileImport,
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
                onGlassStyleChange = onGlassStyleChange,
                onOledModeChange = onOledModeChange,
                onAuroraEnabledChange = onAuroraEnabledChange,
                onPerformanceModeChange = onPerformanceModeChange,
                onStripArtistNameChange = onStripArtistNameChange,
            )
        }
    }
}
