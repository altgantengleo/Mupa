package com.xeno.mupa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTab(val label: String) {
    SONGS("Semua Lagu"),
    PLAYLISTS("Playlist"),
}

enum class LibrarySort(val label: String) {
    DEFAULT("Terbaru"),
    TITLE_ASC("A–Z"),
    ARTIST("Artis"),
    MOST_PLAYED("Sering"),
}

@Immutable
data class LibraryUiState(
    val activeTab: LibraryTab = LibraryTab.SONGS,
    val sort: LibrarySort = LibrarySort.DEFAULT,
    val favoritesOnly: Boolean = false,
    val localOnly: Boolean = false,
    val allTracks: List<Track> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val visibleTracks: List<Track>
        get() {
            val filtered = allTracks.filter { track ->
                (!favoritesOnly || track.isFavorite) &&
                    (!localOnly || track.sourceType == AudioSourceType.LOCAL)
            }
            return when (sort) {
                LibrarySort.DEFAULT -> filtered
                LibrarySort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
                LibrarySort.ARTIST -> filtered.sortedBy { it.artistName.lowercase() }
                LibrarySort.MOST_PLAYED -> filtered.sortedByDescending { it.playCount }
            }
        }

    val smartPlaylists: List<Playlist>
        get() = listOf(
            Playlist(
                id = "pl_mix",
                title = "Mix Mupa",
                subtitle = "Racikan otomatis dari favorit dan kebiasaan dengar",
                tracks = allTracks.sortedByDescending { it.playCount }.take(8),
                isSystemGenerated = true,
            ),
            Playlist(
                id = "pl_fav",
                title = "Favorit",
                subtitle = "${allTracks.count { it.isFavorite }} lagu disukai",
                tracks = allTracks.filter { it.isFavorite },
                isSystemGenerated = true,
            ),
            Playlist(
                id = "pl_local",
                title = "Musik Lokal",
                subtitle = "${allTracks.count { it.sourceType == AudioSourceType.LOCAL }} file di perangkat",
                tracks = allTracks.filter { it.sourceType == AudioSourceType.LOCAL },
                isSystemGenerated = true,
            ),
            Playlist(
                id = "pl_unplayed",
                title = "Belum Diputar",
                subtitle = "${allTracks.count { it.playCount == 0 }} lagu menunggu diputar",
                tracks = allTracks.filter { it.playCount == 0 },
                isSystemGenerated = true,
            ),
        )
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicSource: MusicSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibrary()
    }

    fun selectTab(tab: LibraryTab) = _uiState.update { it.copy(activeTab = tab) }
    fun selectSort(sort: LibrarySort) = _uiState.update { it.copy(sort = sort) }
    fun toggleFavoritesOnly() = _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    fun toggleLocalOnly() = _uiState.update { it.copy(localOnly = !it.localOnly) }
    fun retry() = loadLibrary()

    private fun loadLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicSource.getLibraryTracks()
                .onSuccess { list ->
                    _uiState.update { it.copy(allTracks = list, isLoading = false, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Gagal memuat pustaka.")
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    stripArtistName: Boolean,
    contentPadding: PaddingValues,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleLocalOnly: () -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "lib_header") {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Pustaka",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryTab.entries.forEach { tab ->
                        FilterChip(
                            selected = uiState.activeTab == tab,
                            onClick = { onSelectTab(tab) },
                            label = { Text(tab.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                    }
                }
                if (uiState.activeTab == LibraryTab.SONGS) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = uiState.favoritesOnly,
                            onClick = onToggleFavoritesOnly,
                            label = { Text("Favorit") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                        FilterChip(
                            selected = uiState.localOnly,
                            onClick = onToggleLocalOnly,
                            label = { Text("Lokal") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                        LibrarySort.entries.forEach { sort ->
                            FilterChip(
                                selected = uiState.sort == sort,
                                onClick = { onSelectSort(sort) },
                                label = { Text(sort.label) },
                            )
                        }
                    }
                }
            }
        }

        when {
            uiState.isLoading -> {
                item(key = "lib_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MupaPalette.CoralPrimary)
                    }
                }
            }
            uiState.errorMessage != null -> {
                item(key = "lib_error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(uiState.errorMessage, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedButton(onClick = onRetry) { Text("Coba Lagi") }
                    }
                }
            }
            uiState.activeTab == LibraryTab.SONGS -> {
                val tracks = uiState.visibleTracks
                if (tracks.isEmpty()) {
                    item(key = "lib_empty") {
                        Text(
                            text = "Belum ada lagu untuk filter yang dipilih.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = tracks,
                        key = { idx, track -> "lib_${track.id}_$idx" },
                    ) { index, track ->
                        TrackListRow(
                            track = track,
                            stripArtistName = stripArtistName,
                            onClick = { onPlayTrack(tracks, index) },
                        )
                    }
                }
            }
            else -> {
                itemsIndexed(
                    items = uiState.smartPlaylists,
                    key = { _, playlist -> playlist.id },
                ) { _, playlist ->
                    MupaGlassSurface(
                        shape = MupaRadii.Card,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = playlist.tracks.isNotEmpty()) {
                                onPlayTrack(playlist.tracks, 0)
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = playlist.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = playlist.subtitle ?: "${playlist.tracks.size} lagu",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
