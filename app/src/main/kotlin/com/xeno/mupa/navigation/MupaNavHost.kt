package com.xeno.mupa.navigation

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
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.MupaAppUiState
import com.xeno.mupa.ui.screens.AccountScreen
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
    contentPadding: PaddingValues,
    onPlayTracks: (List<Track>, Int) -> Unit,
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
                initialOffsetX = { fullWidth -> (fullWidth / 12) * direction },
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
                targetOffsetX = { fullWidth -> (fullWidth / 12) * direction },
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
            )
        }
        composable(MupaTopLevelDestination.SEARCH.route) {
            val vm: SearchViewModel = hiltViewModel()
            val searchState by vm.uiState.collectAsStateWithLifecycle()
            SearchScreen(
                uiState = searchState,
                stripArtistName = stripArtist,
                contentPadding = contentPadding,
                onQueryChange = vm::onQueryChange,
                onSelectFilter = vm::onSelectFilter,
                onSubmitQuery = vm::onSubmitQuery,
                onPlayTrack = onPlayTracks,
                onRetry = vm::retry,
            )
        }
        composable(MupaTopLevelDestination.LIBRARY.route) {
            val vm: LibraryViewModel = hiltViewModel()
            val libraryState by vm.uiState.collectAsStateWithLifecycle()
            LibraryScreen(
                uiState = libraryState,
                stripArtistName = stripArtist,
                contentPadding = contentPadding,
                onSelectTab = vm::selectTab,
                onSelectSort = vm::selectSort,
                onToggleFavoritesOnly = vm::toggleFavoritesOnly,
                onToggleLocalOnly = vm::toggleLocalOnly,
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
                onOpenSettings = { navController.navigate(MupaRoutes.SETTINGS) },
                onOledModeChange = onOledModeChange,
            )
        }
        composable(MupaRoutes.SETTINGS) {
            SettingsScreen(
                preferences = uiState.preferences,
                onGlassStyleChange = onGlassStyleChange,
                onOledModeChange = onOledModeChange,
                onAuroraEnabledChange = onAuroraEnabledChange,
                onPerformanceModeChange = onPerformanceModeChange,
                onStripArtistNameChange = onStripArtistNameChange,
                onEditFavoriteArtists = onEditFavoriteArtists,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
