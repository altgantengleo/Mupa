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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaArtworkBox
import com.xeno.mupa.core.designsystem.component.MupaCoverCard
import com.xeno.mupa.core.designsystem.component.MupaCoverCardSkeleton
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaLogoGlyph
import com.xeno.mupa.core.designsystem.component.MupaPrimaryButton
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSkeletonBox
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.data.local.LocalLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val greeting: String = "Selamat datang",
    val activeSource: AudioSourceType = AudioSourceType.STREAMING,
    val mixHero: Playlist? = null,
    val shelves: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mixSeed: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val preferencesRepository: PreferencesRepository,
    private val localLibraryRepository: LocalLibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(greeting = computeGreeting()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var latestSeeds: Set<String> = emptySet()
    private var latestSource: AudioSourceType = AudioSourceType.STREAMING

    init {
        viewModelScope.launch {
            combine(
                preferencesRepository.userPreferences
                    .map { it.favoriteArtistIds to it.activeSource }
                    .distinctUntilChanged(),
                localLibraryRepository.localTracksFlow.map { it.size }.distinctUntilChanged(),
            ) { (seeds, source), _ ->
                seeds to source
            }.collect { (seeds, source) ->
                latestSeeds = seeds
                latestSource = source
                loadShelves(seeds, source, _uiState.value.mixSeed)
            }
        }
    }

    fun reshuffleMix() {
        val nextSeed = _uiState.value.mixSeed + 1
        _uiState.update { state ->
            val currentHero = state.mixHero
            val rotatedHero = if (currentHero != null && currentHero.tracks.size > 1) {
                val shift = nextSeed % currentHero.tracks.size
                currentHero.copy(tracks = currentHero.tracks.drop(shift) + currentHero.tracks.take(shift))
            } else {
                currentHero
            }
            state.copy(mixSeed = nextSeed, mixHero = rotatedHero)
        }
    }

    fun retry() {
        loadShelves(latestSeeds, latestSource, _uiState.value.mixSeed)
    }

    private fun loadShelves(seedIds: Set<String>, source: AudioSourceType, seed: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, activeSource = source, errorMessage = null) }
            musicSource.getHomeRecommendations(seedIds, source)
                .onSuccess { raw ->
                    val rawHero = raw.firstOrNull { it.id == "shelf_mix_mupa" }
                    val adjustedHero = if (rawHero != null && seed > 0 && rawHero.tracks.size > 1) {
                        val shift = seed % rawHero.tracks.size
                        rawHero.copy(tracks = rawHero.tracks.drop(shift) + rawHero.tracks.take(shift))
                    } else {
                        rawHero
                    }
                    // Hapus shelf Mix Mupa ganda dari daftar shelf (perbaikan B3)
                    val nonHeroShelves = raw
                        .filterNot { it.id == "shelf_mix_mupa" }
                        .filter { it.tracks.isNotEmpty() }
                        .take(3)

                    _uiState.update {
                        it.copy(
                            greeting = computeGreeting(),
                            activeSource = source,
                            mixHero = adjustedHero,
                            shelves = nonHeroShelves,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            activeSource = source,
                            isLoading = false,
                            errorMessage = err.message ?: "Gagal memuat beranda.",
                        )
                    }
                }
        }
    }

    private fun computeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 4..10 -> "Selamat pagi"
            in 11..14 -> "Selamat siang"
            in 15..17 -> "Selamat sore"
            else -> "Selamat malam"
        }
    }
}

@Composable
fun HomeScreen(
    uiState: HomeUiState,
    stripArtistName: Boolean,
    contentPadding: PaddingValues,
    onPlayTrack: (List<Track>, Int) -> Unit,
    onReshuffleMix: () -> Unit,
    onRetry: () -> Unit,
    onImportLocalFiles: () -> Unit = {},
    onImportLocalFolder: () -> Unit = {},
    onToggleSource: ((AudioSourceType) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(MupaSpacing.SectionGap),
    ) {
        // Header: glyph + sapaan (wordmark tidak dobel) + indikator sumber tenang
        item(key = "home_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    MupaLogoGlyph(size = 26.dp)
                    Text(
                        text = uiState.greeting,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                }
                val nextSource = if (uiState.activeSource == AudioSourceType.STREAMING) {
                    AudioSourceType.LOCAL
                } else {
                    AudioSourceType.STREAMING
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        .clickable(enabled = onToggleSource != null) { onToggleSource?.invoke(nextSource) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = if (uiState.activeSource == AudioSourceType.STREAMING) {
                            Icons.Rounded.Cloud
                        } else {
                            Icons.Rounded.Smartphone
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (uiState.activeSource == AudioSourceType.STREAMING) "Awan" else "Lokal",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        when {
            uiState.isLoading && uiState.mixHero == null && uiState.shelves.isEmpty() -> {
                item(key = "home_loading_hero") {
                    MupaSkeletonBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(148.dp),
                        shape = MupaRadii.Card,
                    )
                }
                item(key = "home_loading_shelf") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MupaSkeletonBox(modifier = Modifier.width(120.dp).height(20.dp), shape = CircleShape)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) { MupaCoverCardSkeleton() }
                        }
                    }
                }
            }

            uiState.errorMessage != null && uiState.mixHero == null && uiState.shelves.isEmpty() -> {
                item(key = "home_error") {
                    MupaErrorState(message = uiState.errorMessage, onRetry = onRetry)
                }
            }

            uiState.mixHero == null && uiState.shelves.isEmpty() -> {
                item(key = "home_empty") {
                    if (uiState.activeSource == AudioSourceType.LOCAL) {
                        MupaEmptyState(
                            icon = Icons.Rounded.LibraryMusic,
                            title = "Pustaka lokal masih kosong",
                            subtitle = "Import file audio atau folder musik dari perangkatmu untuk mulai meracik Mix Mupa offline.",
                            actionLabel = "Import Lagu",
                            onAction = onImportLocalFiles,
                            secondaryActionLabel = "Import Folder",
                            onSecondaryAction = onImportLocalFolder,
                        )
                    } else {
                        MupaEmptyState(
                            icon = Icons.Rounded.Cloud,
                            title = "Belum ada rekomendasi",
                            subtitle = "Periksa koneksi internetmu atau muat ulang Beranda.",
                            actionLabel = "Coba lagi",
                            onAction = onRetry,
                        )
                    }
                }
            }

            else -> {
                // SATU kartu hero Mix Mupa (kolase cover, judul, N lagu, tombol Putar koral, ikon Racik ulang)
                uiState.mixHero?.takeIf { it.tracks.isNotEmpty() }?.let { hero ->
                    item(key = "hero_mix_mupa") {
                        MixMupaHeroCard(
                            playlist = hero,
                            onPlay = { onPlayTrack(hero.tracks, 0) },
                            onReshuffle = onReshuffleMix,
                        )
                    }
                }

                // Maksimal 3 shelf tanpa duplikasi (Untukmu, Ulangi lagi, Belum diputar)
                uiState.shelves.forEach { shelf ->
                    item(key = shelf.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            MupaSectionHeader(
                                title = shelf.title,
                                subtitle = shelf.subtitle,
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                itemsIndexed(
                                    items = shelf.tracks,
                                    key = { _, track -> "${shelf.id}_${track.id}" },
                                ) { index, track ->
                                    val display = TrackDisplayRules.formatTrack(track, stripArtistName)
                                    MupaCoverCard(
                                        title = display.title,
                                        subtitle = display.subtitle,
                                        artworkUrl = track.artworkUrl,
                                        onClick = { onPlayTrack(shelf.tracks, index) },
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
private fun MixMupaHeroCard(
    playlist: Playlist,
    onPlay: () -> Unit,
    onReshuffle: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MupaRadii.Card)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MupaRadii.Card)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Kolase 2x2 sampul
            MixCoverCollage(
                artworkUrls = playlist.tracks.map { it.artworkUrl }.take(4),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "Mix Mupa",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${playlist.tracks.size} lagu · ${playlist.subtitle.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            IconButton(
                onClick = onReshuffle,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Racik ulang Mix Mupa",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MupaPrimaryButton(
                text = "Putar",
                icon = Icons.Rounded.PlayArrow,
                onClick = onPlay,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MixCoverCollage(
    artworkUrls: List<String?>,
    modifier: Modifier = Modifier,
) {
    val padded = List(4) { idx -> artworkUrls.getOrNull(idx) }
    Column(
        modifier = modifier
            .size(68.dp)
            .clip(RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MupaArtworkBox(artworkUrl = padded[0], contentDescription = null, size = 33.dp, shape = RoundedCornerShape(4.dp), iconSize = 14.dp)
            MupaArtworkBox(artworkUrl = padded[1], contentDescription = null, size = 33.dp, shape = RoundedCornerShape(4.dp), iconSize = 14.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            MupaArtworkBox(artworkUrl = padded[2], contentDescription = null, size = 33.dp, shape = RoundedCornerShape(4.dp), iconSize = 14.dp)
            MupaArtworkBox(artworkUrl = padded[3], contentDescription = null, size = 33.dp, shape = RoundedCornerShape(4.dp), iconSize = 14.dp)
        }
    }
}
