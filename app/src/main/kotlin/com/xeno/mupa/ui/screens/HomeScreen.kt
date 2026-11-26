package com.xeno.mupa.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xeno.mupa.core.designsystem.component.MupaBrandMark
import com.xeno.mupa.core.designsystem.component.MupaGlassSurface
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.data.fake.TrackNameFormatter
import com.xeno.mupa.ui.player.TrackArtworkTile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@Immutable
data class HomeUiState(
    val greeting: String = "Selamat datang",
    val shelves: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mixSeed: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(greeting = computeGreeting()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var latestSeeds: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            preferencesRepository.userPreferences
                .map { it.favoriteArtistIds }
                .distinctUntilChanged()
                .collect { seeds ->
                    latestSeeds = seeds
                    loadShelves(seeds, _uiState.value.mixSeed)
                }
        }
    }

    fun reshuffleMix() {
        val nextSeed = _uiState.value.mixSeed + 1
        _uiState.update { it.copy(mixSeed = nextSeed) }
        loadShelves(latestSeeds, nextSeed)
    }

    fun retry() {
        loadShelves(latestSeeds, _uiState.value.mixSeed)
    }

    private fun loadShelves(seedIds: Set<String>, seed: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            musicSource.getHomeRecommendations(seedIds)
                .onSuccess { raw ->
                    val adjusted = raw.map { shelf ->
                        if (shelf.id == "shelf_mix_mupa" && seed > 0 && shelf.tracks.size > 1) {
                            val shift = seed % shelf.tracks.size
                            shelf.copy(tracks = shelf.tracks.drop(shift) + shelf.tracks.take(shift))
                        } else {
                            shelf
                        }
                    }
                    _uiState.update {
                        it.copy(
                            greeting = computeGreeting(),
                            shelves = adjusted,
                            isLoading = false,
                            errorMessage = null,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
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
    modifier: Modifier = Modifier,
) {
    when {
        uiState.isLoading && uiState.shelves.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MupaPalette.CoralPrimary)
            }
        }
        uiState.errorMessage != null && uiState.shelves.isEmpty() -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedButton(onClick = onRetry) { Text("Coba Lagi") }
                }
            }
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                item(key = "brand_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MupaBrandMark(glyphSize = 28.dp)
                        Text(
                            text = uiState.greeting,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                // Hero Mix Mupa Card
                val mixShelf = uiState.shelves.firstOrNull { it.id == "shelf_mix_mupa" }
                if (mixShelf != null && mixShelf.tracks.isNotEmpty()) {
                    item(key = "hero_mix_mupa") {
                        MupaGlassSurface(
                            shape = MupaRadii.Card,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Mix Mupa",
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            text = mixShelf.subtitle.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    TextButton(onClick = onReshuffleMix) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = "Racik ulang Mix Mupa",
                                            tint = MupaPalette.CoralBright,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = " Racik ulang",
                                            color = MupaPalette.CoralBright,
                                        )
                                    }
                                }
                                Button(
                                    onClick = { onPlayTrack(mixShelf.tracks, 0) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MupaPalette.CoralPrimary,
                                        contentColor = MupaPalette.LogoCharcoal,
                                    ),
                                ) {
                                    Text("Putar Mix Mupa (${mixShelf.tracks.size} lagu)")
                                }
                            }
                        }
                    }
                }

                // Rak Horizontal (Untukmu, Mix Mupa, Ulangi Lagi, Belum Diputar)
                uiState.shelves.forEach { shelf ->
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = shelf.id) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column {
                                    Text(
                                        text = shelf.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    shelf.subtitle?.let { sub ->
                                        Text(
                                            text = sub,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    itemsIndexed(
                                        items = shelf.tracks,
                                        key = { idx, track -> "${shelf.id}_${track.id}_$idx" },
                                    ) { index, track ->
                                        val display = TrackNameFormatter.format(track, stripArtistName)
                                        Column(
                                            modifier = Modifier
                                                .width(136.dp)
                                                .clip(MupaRadii.Card)
                                                .clickable { onPlayTrack(shelf.tracks, index) },
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            TrackArtworkTile(
                                                track = track,
                                                modifier = Modifier
                                                    .size(136.dp)
                                                    .clip(MupaRadii.ArtworkMedium),
                                                iconSize = 36.dp,
                                            )
                                            Text(
                                                text = display.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            if (display.artist.isNotEmpty()) {
                                                Text(
                                                    text = display.artist,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
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
        }
    }
}
