package com.xeno.mupa.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaFilterSheet
import com.xeno.mupa.core.designsystem.component.MupaPrimaryButton
import com.xeno.mupa.core.designsystem.component.MupaSecondaryButton
import com.xeno.mupa.core.designsystem.component.MupaTextButton
import com.xeno.mupa.core.designsystem.component.MupaTrackRow
import com.xeno.mupa.core.designsystem.component.MupaTrackRowSkeleton
import com.xeno.mupa.core.designsystem.theme.MupaMotion
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.designsystem.theme.TABULAR_NUMS_FEATURE
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.data.local.LocalImportProgress
import com.xeno.mupa.data.local.LocalLibraryRepository
import com.xeno.mupa.data.local.db.LocalFolderEntity
import com.xeno.mupa.data.local.db.UserPlaylistEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibraryTileCategory(val label: String) {
    SONGS("Lagu"),
    PLAYLISTS("Playlist"),
    ALBUMS("Album"),
    ARTISTS("Artis"),
    RECENTLY_ADDED("Baru ditambahkan"),
    FAVORITES("Favorit"),
}

enum class LibrarySort(val label: String) {
    RECENT("Terbaru"),
    TITLE_ASC("A–Z"),
    ARTIST("Artis"),
    MOST_PLAYED("Sering diputar"),
}

@Immutable
data class LibraryUiState(
    val activeSource: AudioSourceType = AudioSourceType.STREAMING,
    val openCategory: LibraryTileCategory? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
    val favoritesOnlyInList: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val playlists: List<UserPlaylistEntity> = emptyList(),
    val connectedFolders: List<LocalFolderEntity> = emptyList(),
    val importProgress: LocalImportProgress = LocalImportProgress(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
) {
    val albumsCount: Int
        get() = tracks.mapNotNull { it.albumTitle?.trim()?.takeIf { a -> a.isNotBlank() } }.distinct().size
    val artistsCount: Int
        get() = tracks.map { it.artistName.trim() }.filter { it.isNotBlank() }.distinct().size
    val recentlyAddedTracks: List<Track>
        get() = tracks.sortedByDescending { it.dateAddedEpochMs }.take(30)
    val favoriteTracks: List<Track>
        get() = tracks.filter { it.isFavorite }

    fun tracksForOpenCategory(): List<Track> {
        val base = when (openCategory) {
            LibraryTileCategory.SONGS, LibraryTileCategory.ALBUMS, LibraryTileCategory.ARTISTS, null -> tracks
            LibraryTileCategory.RECENTLY_ADDED -> recentlyAddedTracks
            LibraryTileCategory.FAVORITES -> favoriteTracks
            LibraryTileCategory.PLAYLISTS -> tracks
        }.let { list ->
            if (favoritesOnlyInList) list.filter { it.isFavorite } else list
        }

        return when (sort) {
            LibrarySort.RECENT -> base.sortedByDescending { it.dateAddedEpochMs }
            LibrarySort.TITLE_ASC -> base.sortedBy { it.title.lowercase() }
            LibrarySort.ARTIST -> base.sortedBy { it.artistName.lowercase() }
            LibrarySort.MOST_PLAYED -> base.sortedByDescending { it.playCount }
        }
    }
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val preferencesRepository: PreferencesRepository,
    private val localLibraryRepository: LocalLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.userPreferences.map { it.activeSource }.distinctUntilChanged(),
                localLibraryRepository.localTracksFlow,
                localLibraryRepository.localFoldersFlow,
                localLibraryRepository.importProgress,
            ) { source, localTracks, folders, progress ->
                Quad(source, localTracks, folders, progress)
            }.collect { (source, localTracks, folders, progress) ->
                _uiState.update {
                    it.copy(
                        activeSource = source,
                        connectedFolders = folders,
                        importProgress = progress,
                    )
                }
                if (source == AudioSourceType.LOCAL) {
                    _uiState.update {
                        it.copy(
                            tracks = localTracks,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                } else {
                    loadStreamingLibrary()
                }
                observePlaylistsFor(source)
            }
        }
    }

    private var playlistsJob: kotlinx.coroutines.Job? = null

    private fun observePlaylistsFor(source: AudioSourceType) {
        playlistsJob?.cancel()
        playlistsJob = viewModelScope.launch {
            localLibraryRepository.observePlaylists(source).collect { pls ->
                _uiState.update { it.copy(playlists = pls) }
            }
        }
    }

    private fun loadStreamingLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicSource.getLibraryTracks(AudioSourceType.STREAMING)
                .onSuccess { list ->
                    _uiState.update { it.copy(tracks = list, isLoading = false, errorMessage = null) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Gagal memuat koleksi streaming.",
                        )
                    }
                }
        }
    }

    fun switchSource(source: AudioSourceType) {
        _uiState.update { it.copy(openCategory = null) }
        viewModelScope.launch { preferencesRepository.setActiveSource(source) }
    }

    fun openCategory(category: LibraryTileCategory) {
        _uiState.update { it.copy(openCategory = category) }
    }

    fun closeCategory() {
        _uiState.update { it.copy(openCategory = null, favoritesOnlyInList = false) }
    }

    fun selectSort(sort: LibrarySort) {
        _uiState.update { it.copy(sort = sort) }
    }

    fun setFavoritesOnly(enabled: Boolean) {
        _uiState.update { it.copy(favoritesOnlyInList = enabled) }
    }

    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch { localLibraryRepository.importAudioFiles(uris) }
    }

    fun importFolder(treeUri: Uri) {
        viewModelScope.launch { localLibraryRepository.importAudioFolder(treeUri) }
    }

    fun rescanFolders() {
        viewModelScope.launch { localLibraryRepository.rescanFolders() }
    }

    fun removeLocalTrack(trackId: String) {
        viewModelScope.launch { localLibraryRepository.removeTrackFromLibrary(trackId) }
    }

    fun toggleLocalFavorite(trackId: String) {
        viewModelScope.launch { localLibraryRepository.toggleFavorite(trackId) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            localLibraryRepository.createPlaylist(name, _uiState.value.activeSource)
        }
    }

    fun retry() {
        if (_uiState.value.activeSource == AudioSourceType.STREAMING) {
            loadStreamingLibrary()
        } else {
            rescanFolders()
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}

/**
 * Layar Pustaka bergaya Mupa Calm dengan struktur tile kategori + switch Awan/Ponsel (perbaikan B7 & B12).
 * - Switch Awan (Streaming) / Ponsel (Lokal) di header memisahkan sumber secara nyata.
 * - Mode Lokal memiliki tombol eksplisit Import Lagu, Import Folder, dan Pindai Ulang Folder.
 * - Drill-down daftar memakai SATU tombol Urutkan/Filter (MupaFilterSheet), bukan 3 baris chip.
 */
@Composable
fun LibraryScreen(
    uiState: LibraryUiState,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onSwitchSource: (AudioSourceType) -> Unit,
    onOpenCategory: (LibraryTileCategory) -> Unit,
    onCloseCategory: () -> Unit,
    onSelectSort: (LibrarySort) -> Unit,
    onSetFavoritesOnly: (Boolean) -> Unit,
    onImportLocalFiles: () -> Unit,
    onImportLocalFolder: () -> Unit,
    onRescanFolders: () -> Unit,
    onRemoveLocalTrack: (String) -> Unit,
    onToggleFavoriteTrack: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFilterSheet by remember { mutableStateOf(false) }
    var showNewPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    BackHandler(enabled = uiState.openCategory != null) {
        onCloseCategory()
    }

    if (showNewPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showNewPlaylistDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text("Playlist Baru", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("Nama playlist...") },
                    singleLine = true,
                )
            },
            confirmButton = {
                MupaPrimaryButton(
                    text = "Buat",
                    onClick = {
                        onCreatePlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showNewPlaylistDialog = false
                    },
                )
            },
            dismissButton = {
                MupaTextButton(text = "Batal", onClick = { showNewPlaylistDialog = false })
            },
        )
    }

    MupaFilterSheet(
        visible = showFilterSheet,
        title = "Urutkan & Filter",
        options = LibrarySort.entries.map { it.label },
        selectedIndex = uiState.sort.ordinal,
        onSelect = { idx -> onSelectSort(LibrarySort.entries[idx]) },
        onDismiss = { showFilterSheet = false },
        extraToggleLabel = "Hanya Favorit",
        extraToggleChecked = uiState.favoritesOnlyInList,
        onExtraToggleChange = onSetFavoritesOnly,
    )

    val openCategory = uiState.openCategory
    if (openCategory != null) {
        LibraryCategoryDetailList(
            category = openCategory,
            uiState = uiState,
            stripArtistName = stripArtistName,
            currentPlayingTrackId = currentPlayingTrackId,
            contentPadding = contentPadding,
            onBack = onCloseCategory,
            onOpenFilter = { showFilterSheet = true },
            onRemoveLocalTrack = onRemoveLocalTrack,
            onToggleFavoriteTrack = onToggleFavoriteTrack,
            onPlayTrack = onPlayTrack,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header Pustaka + Switch Awan (Streaming) / Ponsel (Lokal)
        item(key = "lib_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Pustaka",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                SourceModeSwitch(
                    activeSource = uiState.activeSource,
                    onSwitch = onSwitchSource,
                )
            }
        }

        // Progres / ringkasan import lokal bila berjalan
        if (uiState.activeSource == AudioSourceType.LOCAL && (uiState.importProgress.isRunning || !uiState.importProgress.message.isNullOrBlank())) {
            item(key = "lib_import_status") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MupaRadii.ArtworkMedium)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkMedium)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.importProgress.isRunning) {
                        val total = uiState.importProgress.total.coerceAtLeast(1)
                        Text(
                            text = "Mengimpor lagu lokal (${uiState.importProgress.processed}/$total)...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        LinearProgressIndicator(
                            progress = { uiState.importProgress.processed.toFloat() / total.toFloat() },
                            color = MupaPalette.CoralPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (!uiState.importProgress.message.isNullOrBlank()) {
                        Text(
                            text = uiState.importProgress.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        when {
            uiState.isLoading -> {
                items(4) { MupaTrackRowSkeleton() }
            }

            uiState.errorMessage != null && uiState.tracks.isEmpty() -> {
                item(key = "lib_error") {
                    MupaErrorState(message = uiState.errorMessage, onRetry = onRetry)
                }
            }

            uiState.activeSource == AudioSourceType.LOCAL && uiState.tracks.isEmpty() -> {
                item(key = "lib_local_empty") {
                    MupaEmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Belum ada lagu lokal di-import",
                        subtitle = "Mupa tidak memindai audio sistem secara liar. Pilih file lagu atau folder musikmu sendiri agar pustaka tetap bersih.",
                        actionLabel = "Import Lagu",
                        onAction = onImportLocalFiles,
                        secondaryActionLabel = "Import Folder",
                        onSecondaryAction = onImportLocalFolder,
                    )
                }
            }

            else -> {
                val covers = uiState.tracks.mapNotNull { it.artworkUrl }.distinct()
                val favCovers = uiState.favoriteTracks.mapNotNull { it.artworkUrl }.distinct()
                val recentCovers = uiState.recentlyAddedTracks.mapNotNull { it.artworkUrl }.distinct()

                // Baris 1: Lagu & Playlist (grid 2 kolom)
                item(key = "tiles_row_1") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LibraryOverviewTile(
                            title = "Lagu",
                            count = uiState.tracks.size,
                            artworkBack = covers.getOrNull(1),
                            artworkFront = covers.getOrNull(0),
                            onClick = { onOpenCategory(LibraryTileCategory.SONGS) },
                            modifier = Modifier.weight(1f),
                        )
                        LibraryOverviewTile(
                            title = "Playlist",
                            count = uiState.playlists.size,
                            artworkBack = covers.getOrNull(3),
                            artworkFront = covers.getOrNull(2),
                            onClick = { onOpenCategory(LibraryTileCategory.PLAYLISTS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Baris 2: Album & Artis (grid 2 kolom)
                item(key = "tiles_row_2") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LibraryOverviewTile(
                            title = "Album",
                            count = uiState.albumsCount,
                            artworkBack = covers.getOrNull(4),
                            artworkFront = covers.getOrNull(1),
                            onClick = { onOpenCategory(LibraryTileCategory.ALBUMS) },
                            modifier = Modifier.weight(1f),
                        )
                        LibraryOverviewTile(
                            title = "Artis",
                            count = uiState.artistsCount,
                            artworkBack = covers.getOrNull(5),
                            artworkFront = covers.getOrNull(2),
                            circularFront = true,
                            onClick = { onOpenCategory(LibraryTileCategory.ARTISTS) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // Tile penuh: Baru ditambahkan
                item(key = "tile_recent") {
                    LibraryOverviewTile(
                        title = "Baru ditambahkan",
                        count = uiState.recentlyAddedTracks.size,
                        artworkBack = recentCovers.getOrNull(1),
                        artworkFront = recentCovers.getOrNull(0),
                        onClick = { onOpenCategory(LibraryTileCategory.RECENTLY_ADDED) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Tile penuh: Favorit
                item(key = "tile_favorites") {
                    LibraryOverviewTile(
                        title = "Favorit",
                        count = uiState.favoriteTracks.size,
                        artworkBack = favCovers.getOrNull(0) ?: covers.getOrNull(0),
                        artworkFront = null,
                        showHeartBadge = true,
                        onClick = { onOpenCategory(LibraryTileCategory.FAVORITES) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Baris aksi bawah (Playlist Baru + khusus Lokal: Import Lagu / Folder / Pindai Ulang)
                item(key = "lib_bottom_actions") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MupaSecondaryButton(
                                text = "Playlist Baru",
                                icon = Icons.Rounded.Add,
                                onClick = { showNewPlaylistDialog = true },
                                modifier = Modifier.weight(1f),
                            )
                            if (uiState.activeSource == AudioSourceType.LOCAL) {
                                MupaSecondaryButton(
                                    text = "Import Lagu",
                                    icon = Icons.Rounded.FileUpload,
                                    onClick = onImportLocalFiles,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (uiState.activeSource == AudioSourceType.LOCAL) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                MupaSecondaryButton(
                                    text = "Import Folder",
                                    icon = Icons.Rounded.CreateNewFolder,
                                    onClick = onImportLocalFolder,
                                    modifier = Modifier.weight(1f),
                                )
                                if (uiState.connectedFolders.isNotEmpty()) {
                                    MupaSecondaryButton(
                                        text = "Pindai Ulang",
                                        icon = Icons.Rounded.Refresh,
                                        onClick = onRescanFolders,
                                        modifier = Modifier.weight(1f),
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
private fun SourceModeSwitch(
    activeSource: AudioSourceType,
    onSwitch: (AudioSourceType) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val options = listOf(
            AudioSourceType.STREAMING to Pair(Icons.Rounded.Cloud, "Mode Streaming (Awan)"),
            AudioSourceType.LOCAL to Pair(Icons.Rounded.Smartphone, "Mode Lokal (Ponsel)"),
        )
        options.forEach { (source, pair) ->
            val isSelected = activeSource == source
            val bg by animateColorAsState(
                targetValue = if (isSelected) MupaPalette.CoralPrimary else Color.Transparent,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "srcSwitchBg_${source.name}",
            )
            val tint by animateColorAsState(
                targetValue = if (isSelected) MupaPalette.LogoCharcoal else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = MupaMotion.standardTween(MupaMotion.DurationShortMs),
                label = "srcSwitchTint_${source.name}",
            )
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .semantics {
                        selected = isSelected
                        contentDescription = pair.second
                    }
                    .clickable(role = Role.Tab) { onSwitch(source) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = pair.first,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

/**
 * Tile Pustaka kategori bergaya Mupa Calm:
 * - Solid Surface1 + hairline 1dp, angka besar krem tabular, tumpukan 2 cover miring di kanan bawah.
 */
@Composable
private fun LibraryOverviewTile(
    title: String,
    count: Int,
    artworkBack: String?,
    artworkFront: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    circularFront: Boolean = false,
    showHeartBadge: Boolean = false,
) {
    Box(
        modifier = modifier
            .height(106.dp)
            .clip(MupaRadii.Tile)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Tile)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, top = 14.dp, end = 12.dp, bottom = 12.dp),
    ) {
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFeatureSettings = TABULAR_NUMS_FEATURE,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }

        // Tumpukan dua cover miring di sudut kanan bawah
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            MupaArtworkBox(
                artworkUrl = artworkBack,
                contentDescription = null,
                size = 50.dp,
                shape = RoundedCornerShape(10.dp),
                iconSize = 18.dp,
                modifier = Modifier
                    .offset(x = (-22).dp, y = 2.dp)
                    .graphicsLayer { rotationZ = -9f },
            )
            if (showHeartBadge) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .graphicsLayer { rotationZ = 6f }
                        .clip(RoundedCornerShape(12.dp))
                        .background(MupaPalette.CoralContainer)
                        .border(1.dp, MupaPalette.CoralPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MupaPalette.CoralPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            } else {
                MupaArtworkBox(
                    artworkUrl = artworkFront,
                    contentDescription = null,
                    size = 50.dp,
                    shape = if (circularFront) CircleShape else RoundedCornerShape(10.dp),
                    iconSize = 18.dp,
                    modifier = Modifier.graphicsLayer { rotationZ = 6f },
                )
            }
        }
    }
}

@Composable
private fun LibraryCategoryDetailList(
    category: LibraryTileCategory,
    uiState: LibraryUiState,
    stripArtistName: Boolean,
    currentPlayingTrackId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenFilter: () -> Unit,
    onRemoveLocalTrack: (String) -> Unit,
    onToggleFavoriteTrack: (String) -> Unit,
    onPlayTrack: (List<Track>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks = uiState.tracksForOpenCategory()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "detail_topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Kembali ke Pustaka",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Column {
                        Text(
                            text = category.label,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${tracks.size} lagu · ${uiState.sort.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // SATU tombol Urutkan/Filter yang membuka MupaFilterSheet (bukan 3 baris chip)
                MupaSecondaryButton(
                    text = "Urutkan",
                    icon = Icons.Rounded.Tune,
                    onClick = onOpenFilter,
                )
            }
        }

        if (category == LibraryTileCategory.PLAYLISTS) {
            if (uiState.playlists.isEmpty()) {
                item(key = "pl_empty") {
                    MupaEmptyState(
                        icon = Icons.Rounded.LibraryMusic,
                        title = "Belum ada playlist",
                        subtitle = "Gunakan tombol Playlist Baru di halaman Pustaka untuk membuat playlist pertamamu.",
                    )
                }
            } else {
                items(uiState.playlists, key = { it.id }) { pl ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MupaRadii.ArtworkMedium)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.ArtworkMedium)
                            .clickable { if (tracks.isNotEmpty()) onPlayTrack(tracks, 0) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = pl.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (uiState.activeSource == AudioSourceType.LOCAL) "Lokal" else "Streaming",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else if (tracks.isEmpty()) {
            item(key = "detail_empty") {
                MupaEmptyState(
                    icon = Icons.Rounded.LibraryMusic,
                    title = "Belum ada lagu di ${category.label}",
                    subtitle = "Lagu yang sesuai akan tampil otomatis di sini.",
                )
            }
        } else {
            itemsIndexed(
                items = tracks,
                key = { _, track -> "det_${track.id}" },
            ) { index, track ->
                val display = TrackDisplayRules.formatTrack(track, stripArtistName)
                var menuOpen by remember(track.id) { mutableStateOf(false) }
                Box {
                    MupaTrackRow(
                        title = display.title,
                        subtitle = display.subtitle,
                        durationText = display.durationText,
                        artworkUrl = track.artworkUrl,
                        isPlaying = track.id == currentPlayingTrackId,
                        onClick = { onPlayTrack(tracks, index) },
                        onMoreClick = { menuOpen = true },
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        if (track.sourceType == AudioSourceType.LOCAL) {
                            DropdownMenuItem(
                                text = { Text(if (track.isFavorite) "Hapus dari Favorit" else "Tandai Favorit") },
                                leadingIcon = { Icon(Icons.Rounded.Favorite, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onToggleFavoriteTrack(track.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Hapus dari Pustaka") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onRemoveLocalTrack(track.id)
                                },
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text("Putar sekarang") },
                                onClick = {
                                    menuOpen = false
                                    onPlayTrack(tracks, index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
