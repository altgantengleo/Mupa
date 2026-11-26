package com.xeno.mupa.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.ui.CoverAccentPreset
import com.xeno.mupa.ui.MupaAppUiState
import com.xeno.mupa.ui.screens.AccountFoundationScreen
import com.xeno.mupa.ui.screens.HomeFoundationScreen
import com.xeno.mupa.ui.screens.LibraryFoundationScreen
import com.xeno.mupa.ui.screens.SearchFoundationScreen

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
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onCoverAccentPresetChange: (CoverAccentPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabOrder = MupaTopLevelDestination.entries.associate { it.route to it.ordinal }

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
            HomeFoundationScreen(
                contentPadding = contentPadding,
                activePreset = uiState.coverAccentPreset,
                onCoverAccentPresetChange = onCoverAccentPresetChange,
            )
        }
        composable(MupaTopLevelDestination.SEARCH.route) {
            SearchFoundationScreen(contentPadding = contentPadding)
        }
        composable(MupaTopLevelDestination.LIBRARY.route) {
            LibraryFoundationScreen(contentPadding = contentPadding)
        }
        composable(MupaTopLevelDestination.ACCOUNT.route) {
            AccountFoundationScreen(
                contentPadding = contentPadding,
                preferences = uiState.preferences,
                onGlassStyleChange = onGlassStyleChange,
                onOledModeChange = onOledModeChange,
                onAuroraEnabledChange = onAuroraEnabledChange,
                onPerformanceModeChange = onPerformanceModeChange,
            )
        }
    }
}
