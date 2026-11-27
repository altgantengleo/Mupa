package com.xeno.mupa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaChipRow
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaFilterChip
import com.xeno.mupa.core.designsystem.component.MupaPrimaryButton
import com.xeno.mupa.core.designsystem.component.MupaSecondaryButton
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSegmentedTabs
import com.xeno.mupa.core.designsystem.component.MupaSourceSwitch
import com.xeno.mupa.core.designsystem.component.MupaTrackRow
import com.xeno.mupa.core.designsystem.component.MupaTrackRowSkeleton
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.streaming.aggregator.CatalogRelease
import com.xeno.mupa.streaming.aggregator.MultiProviderCatalogAggregator
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

private enum class SearchViewLayer {
    RESULTS,
    ARTIST_DETAIL,
    RELEASE_DETAIL,
}

@Immutable
data class SearchUiState(
    val query: String = "",
    val activeScope: SearchScope = SearchScope.ALL,
    val source: AudioSourceType = AudioSourceType.STREAMING,
    val results: List<Track> = emptyList(),
    val topArtist: Artist? = null,
    val artists: List<Artist> = emptyList(),
    val releases: List<CatalogRelease> = emptyList(),
    val openArtist: Artist? = null,
    val artistTopTracks: List<Track> = emptyList(),
    val artistReleases: List<CatalogRelease> = emptyList(),
    val isArtistLoading: Boolean = false,
    val openRelease: CatalogRelease? = null,
    val releaseTracks: List<Track> = emptyList(),
    val isReleaseLoading: Boolean = false,
    val recentQueries: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val catalogAggregator: MultiProviderCatalogAggregator,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences
                .map { it.activeSource }
                .distinctUntilChanged()
                .collect { src ->
                    if (_uiState.value.source != src) {
                        _uiState.update {
                            it.copy(
                                source = src,
                                openArtist = null,
                                openRelease = null,
                            )
                        }
                        if (_uiState.value.query.isNotBlank() || src == AudioSourceType.LOCAL) {
                            triggerSearch(_uiState.value.query, _uiState.value.activeScope, src, immediate = true)
                        }
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, openArtist = null, openRelease = null) }
        val state = _uiState.value
        if (query.isBlank() && state.source == AudioSourceType.STREAMING) {
            searchJob?.cancel()
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    topArtist = null,
                    artists = emptyList(),
                    releases = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                    errorMessage = null,
                )
            }
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
        _uiState.update {
            it.copy(
                source = next,
                errorMessage = null,
                openArtist = null,
                openRelease = null,
            )
        }
        viewModelScope.launch { preferencesRepository.setActiveSource(next) }
        if (_uiState.value.query.isNotBlank() || next == AudioSourceType.LOCAL) {
            triggerSearch(_uiState.value.query, _uiState.value.activeScope, next, immediate = true)
        } else {
            _uiState.update {
                it.copy(
                    results = emptyList(),
                    topArtist = null,
                    artists = emptyList(),
                    releases = emptyList(),
                    isLoading = false,
                    hasSearched = false,
                )
            }
        }
    }

    fun onSubmitQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            _uiState.update { state ->
                val updated = (listOf(trimmed) + state.recentQueries.filterNot { it.equals(trimmed, true) }).take(8)
                state.copy(query = trimmed, recentQueries = updated, openArtist = null, openRelease = null)
            }
        }
        triggerSearch(trimmed, _uiState.value.activeScope, _uiState.value.source, immediate = true)
    }

    fun onRemoveRecent(query: String) {
        _uiState.update { state ->
            state.copy(recentQueries = state.recentQueries.filterNot { it == query })
        }
    }

    fun openArtist(artist: Artist) {
        detailJob?.cancel()
        val initialTracks = _uiState.value.results.filter {
            it.artistName.contains(artist.name, ignoreCase = true)
        }
        val initialReleases = _uiState.value.releases.filter {
            it.artistName.contains(artist.name, ignoreCase = true)
        }
        _uiState.update {
            it.copy(
                openArtist = artist,
                openRelease = null,
                artistTopTracks = initialTracks,
                artistReleases = initialReleases,
                isArtistLoading = true,
            )
        }
        detailJob = viewModelScope.launch {
            val profile = catalogAggregator.getArtistProfile(artist)
            _uiState.update { state ->
                if (state.openArtist?.id != artist.id) return@update state
                state.copy(
                    artistTopTracks = profile.topTracks.ifEmpty { initialTracks },
                    artistReleases = profile.releases.ifEmpty { initialReleases },
                    isArtistLoading = false,
                )
            }
        }
    }

    fun openRelease(release: CatalogRelease) {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                openRelease = release,
                releaseTracks = release.cachedTracks,
                isReleaseLoading = true,
            )
        }
        detailJob = viewModelScope.launch {
            val tracks = catalogAggregator.getReleaseTracks(release)
            _uiState.update { state ->
                if (state.openRelease?.id != release.id) return@update state
                state.copy(
                    releaseTracks = tracks.ifEmpty { release.cachedTracks },
                    isReleaseLoading = false,
                )
            }
        }
    }

    fun closeDetail() {
        detailJob?.cancel()
        _uiState.update { state ->
            when {
                state.openRelease != null -> state.copy(
                    openRelease = null,
                    releaseTracks = emptyList(),
                    isReleaseLoading = false,
                )
                state.openArtist != null -> state.copy(
                    openArtist = null,
                    artistTopTracks = emptyList(),
                    artistReleases = emptyList(),
                    isArtistLoading = false,
                )
                else -> state
            }
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
                delay(280L)
            }
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            if (source == AudioSourceType.LOCAL) {
                musicSource.searchTracks(query, AudioSourceType.LOCAL)
                    .onSuccess { list ->
                        val localArtists = list
                            .groupBy { it.artistName.trim().lowercase() }
                            .values
                            .mapNotNull { grp ->
                                val first = grp.firstOrNull() ?: return@mapNotNull null
                                Artist(
                                    id = "local_artist_${first.artistName.lowercase().hashCode()}",
                                    name = first.artistName,
                                    artworkUrl = grp.firstNotNullOfOrNull { it.artworkUrl },
                                    sourceType = AudioSourceType.LOCAL,
                                )
                            }
                        val localReleases = list
                            .filter { !it.albumTitle.isNullOrBlank() }
                            .groupBy { "${it.albumTitle!!.trim().lowercase()}|${it.artistName.lowercase()}" }
                            .values
                            .map { grp ->
                                val first = grp.first()
                                CatalogRelease(
                                    id = "local_album_${first.albumTitle.hashCode()}",
                                    title = first.albumTitle ?: "Album",
                                    artistName = first.artistName,
                                    artworkUrl = grp.firstNotNullOfOrNull { it.artworkUrl },
                                    releaseType = if (grp.size <= 1) "Single" else "Album",
                                    trackCount = grp.size,
                                    cachedTracks = grp,
                                )
                            }
                        _uiState.update {
                            it.copy(
                                results = list,
                                topArtist = null,
                                artists = localArtists,
                                releases = localReleases,
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
                                errorMessage = err.message ?: "Pencarian lokal gagal.",
                            )
                        }
                    }
                return@launch
            }

            // Streaming: aliran progresif lintas 4 provider (hasil tercepat langsung tampil ~250ms, lalu diperkaya hingga 60 lagu)
            var emittedAny = false
            catalogAggregator.observeSearchRichProgressive(query, trackLimit = MAX_SEARCH_TRACKS)
                .collect { bundle ->
                    emittedAny = true
                    _uiState.update {
                        it.copy(
                            results = bundle.tracks,
                            topArtist = bundle.topArtist,
                            artists = bundle.artists,
                            releases = bundle.releases,
                            isLoading = false,
                            hasSearched = true,
                            errorMessage = null,
                        )
                    }
                }
            if (!emittedAny) {
                _uiState.update {
                    it.copy(
                        results = emptyList(),
                        topArtist = null,
                        artists = emptyList(),
                        releases = emptyList(),
                        isLoading = false,
                        hasSearched = true,
                        errorMessage = "Hasil tidak ditemukan untuk \"${query.trim()}\".",
                    )
                }
            }
        }
    }

    companion object {
        const val MAX_SEARCH_TRACKS = 60
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    onPlayFromSearch: ((Track) -> Unit)? = null,
    onPlayShuffled: ((List<Track>, QueueSource) -> Unit)? = null,
    onOpenArtist: (Artist) -> Unit = {},
    onOpenRelease: (CatalogRelease) -> Unit = {},
    onCloseDetail: () -> Unit = {},
    favoriteArtists: List<Artist> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val activeLayer = when {
        uiState.openRelease != null -> SearchViewLayer.RELEASE_DETAIL
        uiState.openArtist != null -> SearchViewLayer.ARTIST_DETAIL
        else -> SearchViewLayer.RESULTS
    }

    BackHandler(enabled = activeLayer != SearchViewLayer.RESULTS) {
        onCloseDetail()
    }

    AnimatedContent(
        targetState = activeLayer,
        transitionSpec = {
            val goingDeeper = targetState.ordinal > initialState.ordinal
            if (goingDeeper) {
                (slideInHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(300),
                    initialOffsetX = { fullWidth -> (fullWidth * 0.26f).toInt() },
                ) + fadeIn(animationSpec = MupaMotion.standardTween(200))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = MupaMotion.emphasizedTween(300),
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
                    ) + fadeOut(animationSpec = MupaMotion.standardTween(160)))
            } else {
                (slideInHorizontally(
                    animationSpec = MupaMotion.emphasizedTween(280),
                    initialOffsetX = { fullWidth -> -(fullWidth * 0.10f).toInt() },
                ) + fadeIn(animationSpec = MupaMotion.standardTween(200))) togetherWith
                    (slideOutHorizontally(
                        animationSpec = MupaMotion.emphasizedTween(280),
                        targetOffsetX = { fullWidth -> (fullWidth * 0.26f).toInt() },
                    ) + fadeOut(animationSpec = MupaMotion.standardTween(160)))
            }
        },
        label = "search_detail_transition",
        modifier = modifier.fillMaxSize(),
    ) { layer ->
        when (layer) {
            SearchViewLayer.RELEASE_DETAIL -> {
                val release = uiState.openRelease
                if (release != null) {
                    SearchReleaseDetailView(
                        release = release,
                        tracks = uiState.releaseTracks,
                        isLoading = uiState.isReleaseLoading,
                        stripArtistName = stripArtistName,
                        currentPlayingTrackId = currentPlayingTrackId,
                        contentPadding = contentPadding,
                        onBack = onCloseDetail,
                        onPlayAll = { list, idx -> onPlayTrack(list, idx) },
                        onPlayShuffled = { list ->
                            if (onPlayShuffled != null) {
                                onPlayShuffled(
                                    list,
                                    QueueSource.PlaylistOrAlbum(
                                        collectionId = release.id,
                                        title = release.title,
                                        isAlbum = true,
                                    ),
                                )
                            } else {
                                onPlayTrack(list.shuffled(), 0)
                            }
                        },
                    )
                }
            }

            SearchViewLayer.ARTIST_DETAIL -> {
                val artist = uiState.openArtist
                if (artist != null) {
                    SearchArtistDetailView(
                        artist = artist,
                        topTracks = uiState.artistTopTracks,
                        releases = uiState.artistReleases,
                        isLoading = uiState.isArtistLoading,
                        stripArtistName = stripArtistName,
                        currentPlayingTrackId = currentPlayingTrackId,
                        contentPadding = contentPadding,
                        onBack = onCloseDetail,
                        onOpenRelease = onOpenRelease,
                        onPlayTrack = { list, idx -> onPlayTrack(list, idx) },
                        onPlayShuffled = { list ->
                            if (onPlayShuffled != null) {
                                onPlayShuffled(
                                    list,
                                    QueueSource.Artist(
                                        artistId = artist.id,
                                        artistName = artist.name,
                                    ),
                                )
                            } else {
                                onPlayTrack(list.shuffled(), 0)
                            }
                        },
                    )
                }
            }

            SearchViewLayer.RESULTS -> {
                SearchMainResultsView(
                    uiState = uiState,
                    stripArtistName = stripArtistName,
                    currentPlayingTrackId = currentPlayingTrackId,
                    contentPadding = contentPadding,
                    onQueryChange = onQueryChange,
                    onSelectScope = onSelectScope,
                    onToggleSource = onToggleSource,
                    onSubmitQuery = onSubmitQuery,
                    onRemoveRecent = onRemoveRecent,
                    onPlayTrack = onPlayTrack,
                    onRetry = onRetry,
                    onImportLocalFiles = onImportLocalFiles,
                    onSwitchSource = onSwitchSource,
                    onPlayFromSearch = onPlayFromSearch,
                    onOpenArtist = onOpenArtist,
                    onOpenRelease = onOpenRelease,
                    favoriteArtists = favoriteArtists,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchMainResultsView(
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
    onImportLocalFiles: () -> Unit,
    onSwitchSource: ((AudioSourceType) -> Unit)?,
    onPlayFromSearch: ((Track) -> Unit)?,
    onOpenArtist: (Artist) -> Unit,
    onOpenRelease: (CatalogRelease) -> Unit,
    favoriteArtists: List<Artist>,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        // Sticky header: judul + MupaSourceSwitch kanan-atas + input pencarian + SegmentedTabs penuh
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

        // Daftar hasil terstruktur
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                start = MupaSpacing.ScreenHorizontal,
                end = MupaSpacing.ScreenHorizontal,
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    } else if (favoriteArtists.isNotEmpty()) {
                        item(key = "search_fav_artists_shortcuts") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                MupaSectionHeader(
                                    title = "Pintasan Artis Favorit",
                                    subtitle = "Ketuk untuk melihat lagu & rilisan mereka",
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    favoriteArtists.forEach { artist ->
                                        MupaFilterChip(
                                            label = artist.name,
                                            selected = false,
                                            leadingIcon = Icons.Rounded.Search,
                                            onClick = { onSubmitQuery(artist.name) },
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item(key = "search_initial_hint") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .clip(MupaRadii.ArtworkMedium)
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkMedium)
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = MupaPalette.CoralPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "Ketik judul lagu, artis, atau album. Hasil menampilkan profil artis, rilisan, dan hingga 60 lagu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                uiState.results.isEmpty() && uiState.artists.isEmpty() && uiState.releases.isEmpty() -> {
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
                    when (uiState.activeScope) {
                        SearchScope.ALL -> {
                            // 1. Sorotan Artis Teratas (mis. cari "jvke" -> muncul kartu profil JVKE + tombol Putar)
                            val topArtist = uiState.topArtist
                            if (topArtist != null) {
                                item(key = "top_artist_${topArtist.id}") {
                                    val artistTracks = uiState.results.filter {
                                        it.artistName.contains(topArtist.name, ignoreCase = true)
                                    }.ifEmpty { uiState.results }
                                    TopArtistSpotlightCard(
                                        artist = topArtist,
                                        releasesCount = uiState.releases.count {
                                            it.artistName.contains(topArtist.name, ignoreCase = true)
                                        },
                                        onClick = { onOpenArtist(topArtist) },
                                        onPlay = {
                                            if (artistTracks.isNotEmpty()) {
                                                onPlayTrack(artistTracks, 0)
                                            }
                                        },
                                    )
                                }
                            }

                            // 2. Segmen Rilisan & Album (karusel horizontal)
                            if (uiState.releases.isNotEmpty()) {
                                item(key = "releases_header") {
                                    MupaSectionHeader(
                                        title = "Rilisan & Album",
                                        subtitle = if (topArtist != null) "Album, EP & single terkait ${topArtist.name}" else "Album, EP & single",
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                    )
                                }
                                item(key = "releases_carousel") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(vertical = 2.dp),
                                    ) {
                                        items(
                                            items = uiState.releases.take(12),
                                            key = { "rel_${it.id}" },
                                        ) { release ->
                                            ReleaseCardCompact(
                                                release = release,
                                                onClick = { onOpenRelease(release) },
                                            )
                                        }
                                    }
                                }
                            }

                            // 3. Segmen Artis Terkait (jika ada artis lain selain topArtist)
                            val otherArtists = uiState.artists.filter { it.id != topArtist?.id }.take(10)
                            if (otherArtists.isNotEmpty() && topArtist == null) {
                                item(key = "artists_strip_header") {
                                    MupaSectionHeader(
                                        title = "Artis",
                                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                                    )
                                }
                                item(key = "artists_strip") {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(vertical = 2.dp),
                                    ) {
                                        items(
                                            items = otherArtists,
                                            key = { "art_chip_${it.id}" },
                                        ) { artist ->
                                            ArtistAvatarTile(
                                                artist = artist,
                                                onClick = { onOpenArtist(artist) },
                                            )
                                        }
                                    }
                                }
                            }

                            // 4. Segmen Lagu (hingga 60 lagu!)
                            if (uiState.results.isNotEmpty()) {
                                item(key = "tracks_header") {
                                    MupaSectionHeader(
                                        title = if (topArtist != null) "Lagu Populer" else "Lagu",
                                        subtitle = "${uiState.results.size} lagu ditemukan",
                                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                                    )
                                }
                                itemsIndexed(
                                    items = uiState.results,
                                    key = { _, track -> "sr_all_${track.id}" },
                                ) { index, track ->
                                    SearchTrackItemRow(
                                        track = track,
                                        index = index,
                                        allTracks = uiState.results,
                                        stripArtistName = stripArtistName,
                                        currentPlayingTrackId = currentPlayingTrackId,
                                        onPlayTrack = onPlayTrack,
                                        onPlayFromSearch = onPlayFromSearch,
                                    )
                                }
                            }
                        }

                        SearchScope.SONGS -> {
                            item(key = "songs_only_header") {
                                MupaSectionHeader(
                                    title = "Lagu",
                                    subtitle = "${uiState.results.size} hasil",
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                                )
                            }
                            itemsIndexed(
                                items = uiState.results,
                                key = { _, track -> "sr_song_${track.id}" },
                            ) { index, track ->
                                SearchTrackItemRow(
                                    track = track,
                                    index = index,
                                    allTracks = uiState.results,
                                    stripArtistName = stripArtistName,
                                    currentPlayingTrackId = currentPlayingTrackId,
                                    onPlayTrack = onPlayTrack,
                                    onPlayFromSearch = onPlayFromSearch,
                                )
                            }
                        }

                        SearchScope.ARTISTS -> {
                            if (uiState.artists.isEmpty()) {
                                item(key = "artists_empty") {
                                    MupaEmptyState(
                                        icon = Icons.Rounded.Person,
                                        title = "Artis tidak ditemukan",
                                        subtitle = "Coba ketik nama artis lengkap untuk \"${uiState.query}\".",
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            } else {
                                items(
                                    items = uiState.artists,
                                    key = { "sr_art_${it.id}" },
                                ) { artist ->
                                    ArtistListRow(
                                        artist = artist,
                                        onClick = { onOpenArtist(artist) },
                                    )
                                }
                            }
                        }

                        SearchScope.ALBUMS -> {
                            if (uiState.releases.isEmpty()) {
                                item(key = "albums_empty") {
                                    MupaEmptyState(
                                        icon = Icons.Rounded.Album,
                                        title = "Rilisan / album tidak ditemukan",
                                        subtitle = "Belum ada album atau single yang cocok untuk \"${uiState.query}\".",
                                        modifier = Modifier.padding(top = 16.dp),
                                    )
                                }
                            } else {
                                items(
                                    items = uiState.releases,
                                    key = { "sr_rel_${it.id}" },
                                ) { release ->
                                    ReleaseListRow(
                                        release = release,
                                        onClick = { onOpenRelease(release) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTrackItemRow(
    track: Track,
    index: Int,
    allTracks: List<Track>,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onPlayFromSearch: ((Track) -> Unit)?,
) {
    val display = TrackDisplayRules.formatTrack(track, stripArtistName)
    MupaTrackRow(
        title = display.title,
        subtitle = display.subtitle,
        durationText = display.durationText,
        artworkUrl = track.artworkUrl,
        isPlaying = track.id == currentPlayingTrackId,
        onClick = {
            if (track.sourceType == AudioSourceType.STREAMING) {
                if (onPlayFromSearch != null) {
                    onPlayFromSearch(track)
                } else {
                    onPlayTrack(listOf(track), 0)
                }
            } else {
                onPlayTrack(allTracks, index)
            }
        },
    )
}

@Composable
private fun TopArtistSpotlightCard(
    artist: Artist,
    releasesCount: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MupaRadii.ArtworkLarge)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        MupaPalette.CoralPrimary.copy(alpha = 0.16f),
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
            )
            .border(
                1.dp,
                MupaPalette.CoralPrimary.copy(alpha = 0.28f),
                MupaRadii.ArtworkLarge,
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = artist.artworkUrl,
            contentDescription = artist.name,
            size = 64.dp,
            shape = CircleShape,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "ARTIS TERATAS",
                style = MaterialTheme.typography.labelSmall,
                color = MupaPalette.CoralPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (releasesCount > 0) {
                    "Artis · $releasesCount rilisan · Ketuk untuk profil"
                } else {
                    "Artis · Ketuk untuk lagu & rilisan"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MupaPalette.CoralPrimary)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Putar ${artist.name}",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ReleaseCardCompact(
    release: CatalogRelease,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = release.artworkUrl,
            contentDescription = release.title,
            size = 136.dp,
            shape = MupaRadii.ArtworkMedium,
        )
        Column(
            modifier = Modifier.padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = buildString {
                append(release.releaseType)
                if (!release.releaseYear.isNullOrBlank()) {
                    append(" · ")
                    append(release.releaseYear)
                }
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtistAvatarTile(
    artist: Artist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = artist.artworkUrl,
            contentDescription = artist.name,
            size = 72.dp,
            shape = CircleShape,
        )
        Text(
            text = artist.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtistListRow(
    artist: Artist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MupaRadii.ArtworkMedium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = artist.artworkUrl,
            contentDescription = artist.name,
            size = 54.dp,
            shape = CircleShape,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artist.genre?.takeIf { it.isNotBlank() }?.let { "Artis · $it" } ?: "Artis",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReleaseListRow(
    release: CatalogRelease,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MupaRadii.ArtworkMedium)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MupaArtworkBox(
            artworkUrl = release.artworkUrl,
            contentDescription = release.title,
            size = 56.dp,
            shape = RoundedCornerShape(10.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = release.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = buildString {
                append(release.artistName)
                append(" · ")
                append(release.releaseType)
                if (!release.releaseYear.isNullOrBlank()) {
                    append(" · ")
                    append(release.releaseYear)
                }
            }
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SearchArtistDetailView(
    artist: Artist,
    topTracks: List<Track>,
    releases: List<CatalogRelease>,
    isLoading: Boolean,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenRelease: (CatalogRelease) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onPlayShuffled: (List<Track>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MupaSpacing.ScreenHorizontal,
            end = MupaSpacing.ScreenHorizontal,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "artist_detail_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali ke pencarian",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = "Profil Artis",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "artist_detail_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MupaRadii.ArtworkLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkLarge)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MupaArtworkBox(
                        artworkUrl = artist.artworkUrl,
                        contentDescription = artist.name,
                        size = 84.dp,
                        shape = CircleShape,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${topTracks.size} lagu · ${releases.size} rilisan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (topTracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MupaPrimaryButton(
                            text = "Putar",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { onPlayTrack(topTracks, 0) },
                            modifier = Modifier.weight(1f),
                        )
                        MupaSecondaryButton(
                            text = "Acak",
                            icon = Icons.Rounded.Shuffle,
                            onClick = { onPlayShuffled(topTracks) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (releases.isNotEmpty()) {
            item(key = "artist_releases_header") {
                MupaSectionHeader(
                    title = "Rilisan & Album",
                    subtitle = "${releases.size} album, EP & single",
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            item(key = "artist_releases_row") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        items = releases,
                        key = { "art_rel_${it.id}" },
                    ) { rel ->
                        ReleaseCardCompact(
                            release = rel,
                            onClick = { onOpenRelease(rel) },
                        )
                    }
                }
            }
        }

        item(key = "artist_songs_header") {
            MupaSectionHeader(
                title = "Lagu Populer",
                subtitle = if (isLoading && topTracks.isEmpty()) "Memuat lagu..." else "${topTracks.size} lagu",
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (isLoading && topTracks.isEmpty()) {
            items(5) { MupaTrackRowSkeleton() }
        } else if (topTracks.isEmpty()) {
            item(key = "artist_songs_empty") {
                MupaEmptyState(
                    icon = Icons.Rounded.Search,
                    title = "Belum ada lagu dimuat",
                    subtitle = "Coba kembali dan ketuk ulang artis ini.",
                )
            }
        } else {
            itemsIndexed(
                items = topTracks,
                key = { _, track -> "art_trk_${track.id}" },
            ) { idx, track ->
                val display = TrackDisplayRules.formatTrack(track, stripArtistName)
                MupaTrackRow(
                    title = display.title,
                    subtitle = display.subtitle,
                    durationText = display.durationText,
                    artworkUrl = track.artworkUrl,
                    isPlaying = track.id == currentPlayingTrackId,
                    onClick = { onPlayTrack(topTracks, idx) },
                )
            }
        }
    }
}

@Composable
private fun SearchReleaseDetailView(
    release: CatalogRelease,
    tracks: List<Track>,
    isLoading: Boolean,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlayAll: (List<Track>, Int) -> Unit,
    onPlayShuffled: (List<Track>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = MupaSpacing.ScreenHorizontal,
            end = MupaSpacing.ScreenHorizontal,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "rel_detail_topbar") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Kembali",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    text = release.releaseType,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "rel_detail_hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MupaRadii.ArtworkLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkLarge)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    MupaArtworkBox(
                        artworkUrl = release.artworkUrl,
                        contentDescription = release.title,
                        size = 96.dp,
                        shape = MupaRadii.ArtworkMedium,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = release.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MupaPalette.CoralPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val meta = buildString {
                            append(release.releaseType)
                            if (!release.releaseYear.isNullOrBlank()) {
                                append(" · ")
                                append(release.releaseYear)
                            }
                            val count = tracks.size.takeIf { it > 0 } ?: release.trackCount
                            if (count > 0) {
                                append(" · ")
                                append("$count lagu")
                            }
                        }
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (tracks.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        MupaPrimaryButton(
                            text = "Putar",
                            icon = Icons.Rounded.PlayArrow,
                            onClick = { onPlayAll(tracks, 0) },
                            modifier = Modifier.weight(1f),
                        )
                        MupaSecondaryButton(
                            text = "Acak",
                            icon = Icons.Rounded.Shuffle,
                            onClick = { onPlayShuffled(tracks) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        if (isLoading && tracks.isEmpty()) {
            items(5) { MupaTrackRowSkeleton() }
        } else if (tracks.isEmpty()) {
            item(key = "rel_tracks_empty") {
                MupaEmptyState(
                    icon = Icons.Rounded.Album,
                    title = "Daftar lagu belum tersedia",
                    subtitle = "Rilisan ini belum mengembalikan daftar lagu.",
                )
            }
        } else {
            itemsIndexed(
                items = tracks,
                key = { idx, track -> "rel_trk_${idx}_${track.id}" },
            ) { idx, track ->
                val display = TrackDisplayRules.formatTrack(track, stripArtistName)
                MupaTrackRow(
                    title = "${idx + 1}. ${display.title}",
                    subtitle = display.subtitle,
                    durationText = display.durationText,
                    artworkUrl = track.artworkUrl ?: release.artworkUrl,
                    isPlaying = track.id == currentPlayingTrackId,
                    onClick = { onPlayAll(tracks, idx) },
                )
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
