package com.xeno.mupa.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaChipRow
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSegmentedTabs
import com.xeno.mupa.core.designsystem.component.MupaSourceSwitch
import com.xeno.mupa.core.designsystem.component.MupaTrackRow
import com.xeno.mupa.core.designsystem.component.MupaTrackRowSkeleton
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchScope(val label: String) {
    ALL("Semua"),
    SONGS("Lagu"),
    ARTISTS("Artis"),
    ALBUMS("Album"),
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val activeScope: SearchScope = SearchScope.ALL,
    val source: AudioSourceType = AudioSourceType.STREAMING,
    val results: List<Track> = emptyList(),
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences
                .map { it.activeSource }
                .distinctUntilChanged()
                .collect { src ->
                    if (_uiState.value.source != src) {
                        _uiState.update { it.copy(source = src) }
                        if (_uiState.value.query.isNotBlank() || src == AudioSourceType.LOCAL) {
                            triggerSearch(_uiState.value.query, _uiState.value.activeScope, src, immediate = true)
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        val state = _uiState.value
        if (query.isBlank() && state.source == AudioSourceType.STREAMING) {
            searchJob?.cancel()
            _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false, errorMessage = null) }
            return
        }
        triggerSearch(
            query = query,
            scope = state.activeScope,
            source = state.source,
            immediate = state.source == AudioSourceType.LOCAL,
        )
    }

    fun onSelectScope(scope: SearchScope) {
        _uiState.update { it.copy(activeScope = scope) }
        val state = _uiState.value
        if (state.query.isNotBlank() || state.source == AudioSourceType.LOCAL) {
            triggerSearch(state.query, scope, state.source, immediate = true)
        }
    }

    fun onToggleSource() {
        val next = if (_uiState.value.source == AudioSourceType.STREAMING) {
            AudioSourceType.LOCAL
        } else {
            AudioSourceType.STREAMING
        }
        onSwitchSource(next)
    }

    fun onSwitchSource(next: AudioSourceType) {
        if (_uiState.value.source == next) return
        _uiState.update { it.copy(source = next, errorMessage = null) }
        viewModelScope.launch { preferencesRepository.setActiveSource(next) }
        if (_uiState.value.query.isNotBlank() || next == AudioSourceType.LOCAL) {
            triggerSearch(_uiState.value.query, _uiState.value.activeScope, next, immediate = true)
        } else {
            _uiState.update { it.copy(results = emptyList(), isLoading = false, hasSearched = false) }
        }
    }

    fun onSubmitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            _uiState.update { state ->
                val updated = (listOf(trimmed) + state.recentQueries.filterNot { it.equals(trimmed, true) }).take(8)
                state.copy(query = trimmed, recentQueries = updated)
            }
        }
        triggerSearch(trimmed, _uiState.value.activeScope, _uiState.value.source, immediate = true)
    }

    fun onRemoveRecent(query: String) {
        _uiState.update { state ->
            state.copy(recentQueries = state.recentQueries.filterNot { it == query })
        }
    }

    fun retry() {
        triggerSearch(_uiState.value.query, _uiState.value.activeScope, _uiState.value.source, immediate = true)
    }

    private fun triggerSearch(
        query: String,
        scope: SearchScope,
        source: AudioSourceType,
        immediate: Boolean,
    ) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate && source == AudioSourceType.STREAMING) {
                delay(300L)
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicSource.searchTracks(query, source)
                .onSuccess { list ->
                    val trimmed = query.trim()
                    val scoped = when (scope) {
                        SearchScope.ALL, SearchScope.SONGS -> list
                        SearchScope.ARTISTS -> if (trimmed.isEmpty()) list else list.filter {
                            it.artistName.contains(trimmed, ignoreCase = true)
                        }.ifEmpty { list }
                        SearchScope.ALBUMS -> if (trimmed.isEmpty()) list else list.filter {
                            it.albumTitle?.contains(trimmed, ignoreCase = true) == true
                        }.ifEmpty { list }
                    }
                    _uiState.update {
                        it.copy(
                            results = scoped,
                            isLoading = false,
                            hasSearched = true,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            hasSearched = true,
                            errorMessage = err.message ?: "Pencarian gagal. Periksa koneksi internetmu.",
                        )
                    }
                }
        }
    }
}

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSelectScope: (SearchScope) -> Unit,
    onToggleSource: () -> Unit,
    onSubmitQuery: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRetry: () -> Unit,
    onImportLocalFiles: () -> Unit = {},
    onSwitchSource: ((AudioSourceType) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Sticky header: judul + MupaSourceSwitch kanan-atas + input pencarian + SegmentedTabs penuh tanpa terpotong (N5)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = MupaSpacing.ScreenHorizontal,
                    end = MupaSpacing.ScreenHorizontal,
                    top = contentPadding.calculateTopPadding(),
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Cari",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                MupaSourceSwitch(
                    activeSource = uiState.source,
                    onSwitch = { target ->
                        if (onSwitchSource != null) {
                            onSwitchSource(target)
                        } else if (target != uiState.source) {
                            onToggleSource()
                        }
                    },
                )
            }

            SearchInputPill(
                value = uiState.query,
                placeholder = if (uiState.source == AudioSourceType.LOCAL) {
                    "Cari di pustaka lokal..."
                } else {
                    "Judul lagu, artis, atau album..."
                },
                onValueChange = onQueryChange,
                onSubmit = { onSubmitQuery(uiState.query) },
            )

            MupaChipRow {
                MupaSegmentedTabs(
                    items = SearchScope.entries.map { it.label },
                    selectedIndex = uiState.activeScope.ordinal,
                    onSelect = { idx -> onSelectScope(SearchScope.entries[idx]) },
                )
            }
        }

        // Daftar hasil / recent / state
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MupaSpacing.ScreenHorizontal,
                end = MupaSpacing.ScreenHorizontal,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            when {
                uiState.isLoading -> {
                    items(6) { MupaTrackRowSkeleton() }
                }

                uiState.errorMessage != null -> {
                    item(key = "search_error") {
                        MupaErrorState(
                            message = uiState.errorMessage,
                            onRetry = onRetry,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                uiState.query.isBlank() && uiState.source == AudioSourceType.STREAMING -> {
                    if (uiState.recentQueries.isNotEmpty()) {
                        item(key = "recent_header") {
                            MupaSectionHeader(
                                title = "Terakhir dicari",
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                        items(
                            items = uiState.recentQueries,
                            key = { "recent_$it" },
                        ) { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable { onSubmitQuery(recent) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = recent,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(
                                    onClick = { onRemoveRecent(recent) },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Hapus $recent dari riwayat",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    } else {
                        item(key = "search_initial_hint") {
                            MupaEmptyState(
                                icon = Icons.Rounded.Search,
                                title = "Cari musik favoritmu",
                                subtitle = "Ketik judul lagu, nama artis, atau album. Ketuk chip sumber untuk beralih antara Streaming dan Lokal.",
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }

                uiState.results.isEmpty() -> {
                    item(key = "search_empty") {
                        if (uiState.source == AudioSourceType.LOCAL) {
                            MupaEmptyState(
                                icon = Icons.Rounded.LibraryMusic,
                                title = if (uiState.query.isBlank()) "Pustaka lokal kosong" else "Tidak ditemukan di Lokal",
                                subtitle = if (uiState.query.isBlank()) {
                                    "Belum ada lagu lokal yang di-import ke Mupa."
                                } else {
                                    "Tidak ada lagu lokal yang cocok dengan \"${uiState.query}\"."
                                },
                                actionLabel = if (uiState.query.isBlank()) "Import Lagu" else null,
                                onAction = if (uiState.query.isBlank()) onImportLocalFiles else null,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        } else {
                            MupaEmptyState(
                                icon = Icons.Rounded.Search,
                                title = "Tidak ada hasil",
                                subtitle = "Coba kata kunci lain untuk \"${uiState.query}\".",
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }

                else -> {
                    itemsIndexed(
                        items = uiState.results,
                        key = { _, track -> "sr_${track.id}" },
                    ) { index, track ->
                        val display = TrackDisplayRules.formatTrack(track, stripArtistName)
                        MupaTrackRow(
                            title = display.title,
                            subtitle = display.subtitle,
                            durationText = display.durationText,
                            artworkUrl = track.artworkUrl,
                            isPlaying = track.id == currentPlayingTrackId,
                            onClick = { onPlayTrack(uiState.results, index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchInputPill(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MupaPalette.CoralPrimary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(MupaRadii.Pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Pill)
                    .padding(start = 16.dp, end = if (value.isNotEmpty()) 4.dp else 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Hapus kata kunci",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        },
    )
}
