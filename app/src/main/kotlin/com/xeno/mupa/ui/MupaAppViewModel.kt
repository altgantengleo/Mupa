package com.xeno.mupa.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class CoverAccentPreset(
    val label: String,
    val accent: DynamicCoverAccent,
) {
    EMBER_DEFAULT(
        label = "Ember Bawaan",
        accent = DynamicCoverAccent.Default,
    ),
    MIDNIGHT_TEAL(
        label = "Sampul Laut",
        accent = DynamicCoverAccent(
            primary = Color(0xFF3EC9B0),
            secondary = Color(0xFF639BFF),
        ),
    ),
    GOLDEN_HOUR(
        label = "Sampul Senja",
        accent = DynamicCoverAccent(
            primary = Color(0xFFFF8A4C),
            secondary = Color(0xFFE06D94),
        ),
    ),
}

@Immutable
data class MupaAppUiState(
    val preferences: UserPreferences = UserPreferences(),
    val coverAccentPreset: CoverAccentPreset = CoverAccentPreset.EMBER_DEFAULT,
) {
    val appearance: MupaAppearance
        get() = MupaAppearance(
            glassStyle = preferences.glassStyle,
            isOledMode = preferences.isOledMode,
            isAuroraEnabled = preferences.isAuroraEnabled,
            isPerformanceMode = preferences.isPerformanceMode,
            dynamicCoverAccent = coverAccentPreset.accent,
        )
}

@HiltViewModel
class MupaAppViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val coverAccentPreset = MutableStateFlow(CoverAccentPreset.EMBER_DEFAULT)

    val uiState: StateFlow<MupaAppUiState> = combine(
        preferencesRepository.userPreferences,
        coverAccentPreset,
    ) { prefs, preset ->
        MupaAppUiState(
            preferences = prefs,
            coverAccentPreset = preset,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MupaAppUiState(),
    )

    fun setGlassStyle(style: GlassStyle) {
        viewModelScope.launch {
            preferencesRepository.setGlassStyle(style)
        }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setOledMode(enabled)
        }
    }

    fun setAuroraEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setAuroraEnabled(enabled)
        }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPerformanceMode(enabled)
        }
    }

    fun setCoverAccentPreset(preset: CoverAccentPreset) {
        coverAccentPreset.value = preset
    }
}
