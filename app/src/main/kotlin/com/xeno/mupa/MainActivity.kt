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
            val viewModel: MupaAppViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            MupaTheme(appearance = uiState.appearance) {
                MupaApp(
                    uiState = uiState,
                    onGlassStyleChange = viewModel::setGlassStyle,
                    onOledModeChange = viewModel::setOledMode,
                    onAuroraEnabledChange = viewModel::setAuroraEnabled,
                    onPerformanceModeChange = viewModel::setPerformanceMode,
                    onCoverAccentPresetChange = viewModel::setCoverAccentPreset,
                )
            }
        }
    }
}
