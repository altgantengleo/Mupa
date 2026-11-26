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
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.xeno.mupa.core.designsystem.component.MupaAuroraBackground
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.navigation.MupaFloatingBottomBar
import com.xeno.mupa.navigation.MupaNavHost
import com.xeno.mupa.navigation.MupaTopLevelDestination
import com.xeno.mupa.navigation.navigateToTopLevel

@Composable
fun MupaApp(
    uiState: MupaAppUiState,
    onGlassStyleChange: (GlassStyle) -> Unit,
    onOledModeChange: (Boolean) -> Unit,
    onAuroraEnabledChange: (Boolean) -> Unit,
    onPerformanceModeChange: (Boolean) -> Unit,
    onCoverAccentPresetChange: (CoverAccentPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = MupaTopLevelDestination.entries.firstOrNull {
        it.route == currentRoute
    } ?: MupaTopLevelDestination.HOME

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val screenContentPadding = PaddingValues(
        start = 20.dp,
        end = 20.dp,
        top = statusBarTop + 16.dp,
        bottom = navBarBottom + 96.dp,
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
            onGlassStyleChange = onGlassStyleChange,
            onOledModeChange = onOledModeChange,
            onAuroraEnabledChange = onAuroraEnabledChange,
            onPerformanceModeChange = onPerformanceModeChange,
            onCoverAccentPresetChange = onCoverAccentPresetChange,
            modifier = Modifier.fillMaxSize(),
        )

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
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
