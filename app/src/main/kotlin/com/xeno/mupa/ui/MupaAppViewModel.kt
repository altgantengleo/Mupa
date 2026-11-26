package com.xeno.mupa.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.theme.DynamicCoverAccent
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class MupaAppUiState(
    val isReady: Boolean = false,
    val preferences: UserPreferences = UserPreferences(),
    val favoriteArtists: List<Artist> = emptyList(),
    val totalTracksCount: Int = 0,
    val favoriteTracksCount: Int = 0,
    val isEditingFavoriteArtists: Boolean = false,
) {
    val appearance: MupaAppearance
        get() = MupaAppearance(
            glassStyle = preferences.glassStyle,
            isOledMode = preferences.isOledMode,
            isAuroraEnabled = preferences.isAuroraEnabled,
            isPerformanceMode = preferences.isPerformanceMode,
            dynamicCoverAccent = DynamicCoverAccent.Default,
        )
}

@HiltViewModel
class MupaAppViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val artistRepository: ArtistRepository,
    private val musicSource: MusicSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MupaAppUiState())
    val uiState: StateFlow<MupaAppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences.collect { prefs ->
                val artists = artistRepository.getArtistsByIds(prefs.favoriteArtistIds).getOrDefault(emptyList())
                val tracks = musicSource.getLibraryTracks().getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        isReady = true,
                        preferences = prefs,
                        favoriteArtists = artists,
                        totalTracksCount = tracks.size,
                        favoriteTracksCount = tracks.count { t -> t.isFavorite },
                    )
                }
            }
        }
    }

    fun setGlassStyle(style: GlassStyle) {
        viewModelScope.launch { preferencesRepository.setGlassStyle(style) }
    }

    fun setOledMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setOledMode(enabled) }
    }

    fun setAuroraEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAuroraEnabled(enabled) }
    }

    fun setPerformanceMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setPerformanceMode(enabled) }
    }

    fun setStripArtistName(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setStripArtistName(enabled) }
    }

    fun openEditFavoriteArtists() {
        _uiState.update { it.copy(isEditingFavoriteArtists = true) }
    }

    fun closeEditFavoriteArtists() {
        _uiState.update { it.copy(isEditingFavoriteArtists = false) }
    }

    fun applyRestoredBackup(
        restored: com.xeno.mupa.data.backup.MupaBackupCodec.RestoredBackup,
    ) {
        viewModelScope.launch {
            val p = restored.preferences
            preferencesRepository.setGlassStyle(p.glassStyle)
            preferencesRepository.setOledMode(p.isOledMode)
            preferencesRepository.setAuroraEnabled(p.isAuroraEnabled)
            preferencesRepository.setPerformanceMode(p.isPerformanceMode)
            preferencesRepository.setStripArtistName(p.stripArtistName)
            if (p.favoriteArtistIds.size >= 2) {
                preferencesRepository.setOnboardingCompleted(p.favoriteArtistIds)
            }
        }
    }
}
