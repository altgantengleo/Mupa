package com.xeno.mupa.ui.onboarding

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ArtistOnboardingUiState(
    val query: String = "",
    val artists: List<Artist> = emptyList(),
    val selectedArtistIds: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val selectedCount: Int get() = selectedArtistIds.size
    val canContinue: Boolean get() = selectedCount >= MIN_ARTISTS && !isSaving
    val reachedLimit: Boolean get() = selectedCount >= MAX_ARTISTS

    companion object {
        const val MIN_ARTISTS = 2
        const val RECOMMENDED_ARTISTS = 4
        const val MAX_ARTISTS = 8
    }
}

@HiltViewModel
class ArtistOnboardingViewModel @Inject constructor(
    private val artistRepository: ArtistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ArtistOnboardingUiState(
            query = savedStateHandle.get<String>(KEY_QUERY).orEmpty(),
            selectedArtistIds = savedStateHandle.get<ArrayList<String>>(KEY_SELECTED)?.toSet().orEmpty(),
        ),
    )
    val uiState: StateFlow<ArtistOnboardingUiState> = _uiState.asStateFlow()

    init {
        loadArtists(_uiState.value.query)
        // Jika belum ada draft dari SavedStateHandle, isi dari preferensi tersimpan (untuk mode ubah dari Akun)
        if (!savedStateHandle.contains(KEY_SELECTED)) {
            viewModelScope.launch {
                val saved = preferencesRepository.userPreferences.first().favoriteArtistIds
                if (saved.isNotEmpty()) {
                    persistSelectionDraft(saved)
                    _uiState.update { it.copy(selectedArtistIds = saved) }
                }
            }
        }
    }

    fun onQueryChange(newQuery: String) {
        savedStateHandle[KEY_QUERY] = newQuery
        _uiState.update { it.copy(query = newQuery) }
        loadArtists(newQuery)
    }

    fun retry() {
        loadArtists(_uiState.value.query)
    }

    fun toggleArtist(artistId: String) {
        _uiState.update { state ->
            val current = state.selectedArtistIds
            val updated = when {
                artistId in current -> current - artistId
                current.size < ArtistOnboardingUiState.MAX_ARTISTS -> current + artistId
                else -> current
            }
            persistSelectionDraft(updated)
            state.copy(selectedArtistIds = updated)
        }
    }

    fun submitSelection(isEditMode: Boolean, onCompleted: () -> Unit) {
        val selected = _uiState.value.selectedArtistIds
        if (selected.size < ArtistOnboardingUiState.MIN_ARTISTS) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            if (isEditMode) {
                preferencesRepository.updateFavoriteArtists(selected)
            } else {
                preferencesRepository.setOnboardingCompleted(selected)
            }
            _uiState.update { it.copy(isSaving = false) }
            onCompleted()
        }
    }

    private fun loadArtists(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            artistRepository.getSuggestedArtists(query)
                .onSuccess { list ->
                    _uiState.update { it.copy(artists = list, isLoading = false, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Gagal memuat daftar artis.",
                        )
                    }
                }
        }
    }

    private fun persistSelectionDraft(ids: Set<String>) {
        savedStateHandle[KEY_SELECTED] = ArrayList(ids)
    }

    companion object {
        const val KEY_QUERY = "onboarding_query"
        const val KEY_SELECTED = "onboarding_selected_artist_ids"
    }
}
