package com.xeno.mupa.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.fake.TrackNameFormatter
import com.xeno.mupa.ui.player.TrackArtworkTile
import com.xeno.mupa.ui.player.formatDurationMs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchFilter(val label: String) {
    ALL("Semua"),
    FAVORITE("Favorit"),
    LOCAL("Lokal"),
    STREAMING("Streaming"),
    UNPLAYED("Belum Diputar"),
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val activeFilter: SearchFilter = SearchFilter.ALL,
    val results: List<Track> = emptyList(),
    val recentQueries: List<String> = listOf("Hindia", "Nadin Amizah", "Tulus"),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicSource: MusicSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState(isLoading = true))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    init {
        executeSearch("", SearchFilter.ALL)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        executeSearch(query, _uiState.value.activeFilter)
    }

    fun onSelectFilter(filter: SearchFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
        executeSearch(_uiState.value.query, filter)
    }

    fun onSubmitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            _uiState.update { state ->
                val updated = (listOf(trimmed) + state.recentQueries.filterNot { it.equals(trimmed, true) }).take(6)
                state.copy(query = trimmed, recentQueries = updated)
            }
        }
        executeSearch(trimmed, _uiState.value.activeFilter)
    }

    fun retry() {
        executeSearch(_uiState.value.query, _uiState.value.activeFilter)
    }

    private fun executeSearch(query: String, filter: SearchFilter) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicSource.searchTracks(query)
                .onSuccess { list ->
                    val filtered = when (filter) {
                        SearchFilter.ALL -> list
                        SearchFilter.FAVORITE -> list.filter { it.isFavorite }
                        SearchFilter.LOCAL -> list.filter { it.sourceType == AudioSourceType.LOCAL }
                        SearchFilter.STREAMING -> list.filter { it.sourceType == AudioSourceType.STREAMING }
                        SearchFilter.UNPLAYED -> list.filter { it.playCount == 0 }
                    }
                    _uiState.update { it.copy(results = filtered, isLoading = false, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Pencarian gagal.")
                    }
                }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: SearchUiState,
    stripArtistName: Boolean,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "search_header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Cari",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Judul lagu, artis, atau album...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        if (uiState.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Hapus kata kunci",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    shape = MupaRadii.Chip,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MupaPalette.CoralPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SearchFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.activeFilter == filter,
                            onClick = { onSelectFilter(filter) },
                            label = { Text(filter.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MupaPalette.CoralPrimary,
                                selectedLabelColor = MupaPalette.LogoCharcoal,
                            ),
                        )
                    }
                }
            }
        }

        if (uiState.query.isBlank() && uiState.recentQueries.isNotEmpty()) {
            item(key = "recent_queries") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Terakhir dicari",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        uiState.recentQueries.forEach { recent ->
                            FilterChip(
                                selected = false,
                                onClick = { onSubmitQuery(recent) },
                                label = { Text(recent) },
                            )
                        }
                    }
                }
            }
        }

        when {
            uiState.isLoading -> {
                item(key = "loading") {
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
                item(key = "error") {
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
            uiState.results.isEmpty() -> {
                item(key = "empty") {
                    Text(
                        text = "Tidak ada lagu yang cocok dengan filter saat ini.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            else -> {
                itemsIndexed(
                    items = uiState.results,
                    key = { idx, track -> "sr_${track.id}_$idx" },
                ) { index, track ->
                    TrackListRow(
                        track = track,
                        stripArtistName = stripArtistName,
                        onClick = { onPlayTrack(uiState.results, index) },
                    )
                }
            }
        }
    }
}

@Composable
fun TrackListRow(
    track: Track,
    stripArtistName: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = TrackNameFormatter.format(track, stripArtistName)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MupaRadii.Chip)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrackArtworkTile(
            track = track,
            modifier = Modifier
                .size(48.dp)
                .clip(MupaRadii.ArtworkSmall),
            iconSize = 22.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subtitle = buildString {
                if (display.artist.isNotEmpty()) append(display.artist)
                if (track.sourceType == AudioSourceType.LOCAL) {
                    if (isNotEmpty()) append(" · ")
                    append("Lokal")
                }
            }
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!display.durationText.isNullOrBlank()) {
            Text(
                text = display.durationText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
