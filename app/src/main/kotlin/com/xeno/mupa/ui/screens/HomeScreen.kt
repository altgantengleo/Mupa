package com.xeno.mupa.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.crossfade
import coil3.toBitmap
import com.xeno.mupa.core.designsystem.component.MupaCoverCard
import com.xeno.mupa.core.designsystem.component.MupaCoverCardSkeleton
import com.xeno.mupa.core.designsystem.component.MupaEmptyState
import com.xeno.mupa.core.designsystem.component.MupaErrorState
import com.xeno.mupa.core.designsystem.component.MupaLogoGlyph
import com.xeno.mupa.core.designsystem.component.MupaPrimaryButton
import com.xeno.mupa.core.designsystem.component.MupaSectionHeader
import com.xeno.mupa.core.designsystem.component.MupaSkeletonBox
import com.xeno.mupa.core.designsystem.component.MupaSourceSwitch
import com.xeno.mupa.core.designsystem.component.UltraDynamicBackground
import com.xeno.mupa.core.designsystem.theme.LocalMupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaPalette
import com.xeno.mupa.core.designsystem.theme.MupaRadii
import com.xeno.mupa.core.designsystem.theme.MupaSpacing
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPaletteCache
import com.xeno.mupa.core.domain.ArtistRepository
import com.xeno.mupa.core.domain.MusicSource
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.data.local.LocalLibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject

enum class HomeHeroKind {
    MIX_MUPA,
    ARTIST_SPOTLIGHT,
    CONTINUE_LISTENING,
}

@Immutable
data class HomeHeroPage(
    val id: String,
    val kind: HomeHeroKind,
    val kicker: String,
    val title: String,
    val subtitle: String,
    val artworkUrl: String?,
    val tracks: List<Track>,
    val initialTrackIndex: Int = 0,
    val canReshuffle: Boolean = false,
    val palette: UltraDynamicPalette? = null,
)

@Immutable
data class HomeUiState(
    val greeting: String = "Selamat datang",
    val activeSource: AudioSourceType = AudioSourceType.STREAMING,
    val mixHero: Playlist? = null,
    val artistSpotlight: HomeHeroPage? = null,
    val shelves: List<Playlist> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val mixSeed: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val musicSource: MusicSource,
    private val artistRepository: ArtistRepository,
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
            val favoriteArtists = artistRepository.getArtistsByIds(seedIds).getOrDefault(emptyList())
            musicSource.getHomeRecommendations(seedIds, source)
                .onSuccess { raw ->
                    val rawHero = raw.firstOrNull { it.id == "shelf_mix_mupa" }
                    val adjustedHero = if (rawHero != null && seed > 0 && rawHero.tracks.size > 1) {
                        val shift = seed % rawHero.tracks.size
                        rawHero.copy(tracks = rawHero.tracks.drop(shift) + rawHero.tracks.take(shift))
                    } else {
                        rawHero
                    }
                    // Maksimal 3 shelf di bawah Hero, tanpa duplikasi Mix Mupa
                    val nonHeroShelves = raw
                        .filterNot { it.id == "shelf_mix_mupa" }
                        .filter { it.tracks.isNotEmpty() }
                        .take(3)

                    val allPool = buildList {
                        adjustedHero?.tracks?.let { addAll(it) }
                        nonHeroShelves.forEach { addAll(it.tracks) }
                    }.distinctBy { it.id }

                    val spotlight = buildDailyArtistSpotlight(favoriteArtists, allPool)

                    _uiState.update {
                        it.copy(
                            greeting = computeGreeting(),
                            activeSource = source,
                            mixHero = adjustedHero,
                            artistSpotlight = spotlight,
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

    private fun buildDailyArtistSpotlight(
        favoriteArtists: List<Artist>,
        pool: List<Track>,
    ): HomeHeroPage? {
        if (favoriteArtists.isEmpty() || pool.isEmpty()) return null
        val dayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val spotlightArtist = favoriteArtists[dayOfYear % favoriteArtists.size]
        val matchingTracks = pool.filter {
            it.artistName.contains(spotlightArtist.name, ignoreCase = true)
        }
        val tracks = matchingTracks.ifEmpty { pool.take(8) }
        if (tracks.isEmpty()) return null
        return HomeHeroPage(
            id = "hero_artist_${spotlightArtist.id}",
            kind = HomeHeroKind.ARTIST_SPOTLIGHT,
            kicker = "SOROTAN ARTIS",
            title = spotlightArtist.name,
            subtitle = "${tracks.size} lagu pilihan · Bergilir harian dari artis favoritmu",
            artworkUrl = spotlightArtist.artworkUrl ?: tracks.firstOrNull()?.artworkUrl,
            tracks = tracks,
            canReshuffle = false,
        )
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

/**
 * Layar Beranda Mupa (Fase 2S — C):
 * - Hero edge-to-edge di bagian atas (~54% tinggi layar, maks 440dp) menjangkau tepi atas di bawah status bar.
 * - Carousel manual (tanpa auto-advance) hingga 3 halaman: Mix Mupa, Sorotan Artis (bergilir harian), Lanjutkan.
 * - Warna Ultra Dynamic berpindah mulus mengikuti halaman aktif; parallax 0,5x di graphicsLayer.
 * - Maksimal 3 shelf di bawah Hero dengan lagu unik per layar.
 */
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
    continueTrack: Track? = null,
    continueQueue: List<Track> = emptyList(),
    continueIndex: Int = 0,
    pagePalettes: Map<String, UltraDynamicPalette> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val topInset = contentPadding.calculateTopPadding()
    val bottomInset = contentPadding.calculateBottomPadding()
    val listState = rememberLazyListState()

    val heroPages = remember(uiState.mixHero, uiState.shelves, continueTrack, continueQueue, continueIndex, stripArtistName) {
        // Hero Beranda menampilkan HANYA lagu terakhir yang diputar (bukan Mix Mupa) sesuai arahan user.
        // Bila belum ada lagu yang diputar pada sesi ini, fallback ke lagu pertama yang tersedia agar Hero tetap rapi.
        val fallbackPool = uiState.mixHero?.tracks?.takeIf { it.isNotEmpty() }
            ?: uiState.shelves.firstOrNull { it.tracks.isNotEmpty() }?.tracks
            ?: emptyList()
        val targetTrack = continueTrack ?: fallbackPool.firstOrNull()

        if (targetTrack == null) {
            emptyList()
        } else {
            val display = TrackDisplayRules.formatTrack(targetTrack, stripArtistName)
            val queueToPlay = if (continueTrack != null) {
                continueQueue.ifEmpty { listOf(continueTrack) }
            } else {
                fallbackPool.ifEmpty { listOf(targetTrack) }
            }
            val safeIdx = if (continueTrack != null) {
                continueIndex.coerceIn(0, (queueToPlay.size - 1).coerceAtLeast(0))
            } else {
                0
            }
            listOf(
                HomeHeroPage(
                    id = "hero_last_played_${targetTrack.id}",
                    kind = HomeHeroKind.CONTINUE_LISTENING,
                    kicker = if (continueTrack != null) "TERAKHIR DIPUTAR" else "SIAP DIPUTAR",
                    title = display.title,
                    subtitle = if (continueTrack != null) {
                        "Lanjutkan mendengarkan · ${display.subtitle}"
                    } else {
                        "${display.subtitle} · Putar sekarang"
                    },
                    artworkUrl = targetTrack.artworkUrl,
                    tracks = queueToPlay,
                    initialTrackIndex = safeIdx,
                    canReshuffle = false,
                ),
            )
        }
    }

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val heroHeight = (screenHeightDp * 0.54f).coerceIn(340.dp, 440.dp)

    val hasContent = heroPages.isNotEmpty() || uiState.shelves.isNotEmpty()

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = bottomInset),
        verticalArrangement = Arrangement.spacedBy(MupaSpacing.SectionGap),
    ) {
        when {
            uiState.isLoading && !hasContent -> {
                item(key = "home_loading_hero") {
                    Column {
                        HomeFallbackTopBar(
                            greeting = uiState.greeting,
                            activeSource = uiState.activeSource,
                            topInset = topInset,
                            onSwitchSource = onToggleSource,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        MupaSkeletonBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = MupaSpacing.ScreenHorizontal)
                                .height(260.dp),
                            shape = MupaRadii.Card,
                        )
                    }
                }
                item(key = "home_loading_shelf") {
                    Column(
                        modifier = Modifier.padding(horizontal = MupaSpacing.ScreenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        MupaSkeletonBox(modifier = Modifier.width(120.dp).height(20.dp), shape = CircleShape)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(3) { MupaCoverCardSkeleton() }
                        }
                    }
                }
            }

            uiState.errorMessage != null && !hasContent -> {
                item(key = "home_error_top") {
                    HomeFallbackTopBar(
                        greeting = uiState.greeting,
                        activeSource = uiState.activeSource,
                        topInset = topInset,
                        onSwitchSource = onToggleSource,
                    )
                }
                item(key = "home_error") {
                    MupaErrorState(
                        message = uiState.errorMessage,
                        onRetry = onRetry,
                        modifier = Modifier.padding(horizontal = MupaSpacing.ScreenHorizontal),
                    )
                }
            }

            !hasContent -> {
                item(key = "home_empty_top") {
                    HomeFallbackTopBar(
                        greeting = uiState.greeting,
                        activeSource = uiState.activeSource,
                        topInset = topInset,
                        onSwitchSource = onToggleSource,
                    )
                }
                item(key = "home_empty") {
                    Box(modifier = Modifier.padding(horizontal = MupaSpacing.ScreenHorizontal)) {
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
            }

            else -> {
                if (heroPages.isNotEmpty()) {
                    item(key = "home_hero_carousel") {
                        HomeEdgeToEdgeHeroCarousel(
                            pages = heroPages,
                            greeting = uiState.greeting,
                            activeSource = uiState.activeSource,
                            heroHeight = heroHeight,
                            topInset = topInset,
                            scrollOffsetProvider = {
                                if (listState.firstVisibleItemIndex == 0) {
                                    listState.firstVisibleItemScrollOffset.toFloat()
                                } else {
                                    0f
                                }
                            },
                            pagePalettes = pagePalettes,
                            onSwitchSource = onToggleSource,
                            onPlayPage = { page -> onPlayTrack(page.tracks, page.initialTrackIndex) },
                            onReshuffleMix = onReshuffleMix,
                        )
                    }
                } else {
                    item(key = "home_header_fallback") {
                        HomeFallbackTopBar(
                            greeting = uiState.greeting,
                            activeSource = uiState.activeSource,
                            topInset = topInset,
                            onSwitchSource = onToggleSource,
                        )
                    }
                }

                // Maksimal 3 shelf tanpa duplikasi di bawah Hero
                uiState.shelves.take(3).forEach { shelf ->
                    item(key = shelf.id) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            MupaSectionHeader(
                                title = shelf.title,
                                subtitle = shelf.subtitle,
                                modifier = Modifier.padding(horizontal = MupaSpacing.ScreenHorizontal),
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = MupaSpacing.ScreenHorizontal),
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
private fun HomeFallbackTopBar(
    greeting: String,
    activeSource: AudioSourceType,
    topInset: Dp,
    onSwitchSource: ((AudioSourceType) -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MupaSpacing.ScreenHorizontal,
                end = MupaSpacing.ScreenHorizontal,
                top = topInset + 12.dp,
            ),
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
                text = greeting,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MupaSourceSwitch(
            activeSource = activeSource,
            onSwitch = { onSwitchSource?.invoke(it) },
        )
    }
}

@Composable
private fun HomeEdgeToEdgeHeroCarousel(
    pages: List<HomeHeroPage>,
    greeting: String,
    activeSource: AudioSourceType,
    heroHeight: Dp,
    topInset: Dp,
    scrollOffsetProvider: () -> Float,
    pagePalettes: Map<String, UltraDynamicPalette>,
    onSwitchSource: ((AudioSourceType) -> Unit)?,
    onPlayPage: (HomeHeroPage) -> Unit,
    onReshuffleMix: () -> Unit,
) {
    val context = LocalContext.current
    val appearance = LocalMupaAppearance.current
    val bgColor = MaterialTheme.colorScheme.background
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val extractedPalettes = remember { mutableStateMapOf<String, UltraDynamicPalette>() }

    // Ekstraksi palet Ultra Dynamic asinkron per halaman Hero di background thread (di-cache via LRU 64)
    LaunchedEffect(pages) {
        val loader = ImageLoader(context)
        pages.forEach { page ->
            if (page.palette != null || pagePalettes.containsKey(page.id) || extractedPalettes.containsKey(page.id)) {
                return@forEach
            }
            val art = page.artworkUrl
            if (art.isNullOrBlank()) {
                extractedPalettes[page.id] = UltraDynamicPalette.BrandNoCover
            } else {
                val palette = withContext(Dispatchers.IO) {
                    val dataModel: Any = if (art.startsWith("/")) File(art) else art
                    val req = ImageRequest.Builder(context)
                        .data(dataModel)
                        .size(128)
                        .allowHardware(false)
                        .build()
                    val result = runCatching { loader.execute(req) }.getOrNull()
                    val bmp: Bitmap? = (result as? SuccessResult)?.image?.toBitmap()
                    UltraDynamicPaletteCache.extractOrGet(
                        key = "hero_${page.id}_$art",
                        bitmap = bmp,
                    )
                }
                extractedPalettes[page.id] = palette
            }
        }
    }

    val currentPageIndex = pagerState.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val activePage = pages[currentPageIndex]
    val activeHeroPalette = activePage.palette
        ?: pagePalettes[activePage.id]
        ?: extractedPalettes[activePage.id]
        ?: appearance.effectiveUltraDynamicPalette

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clipToBounds()
            .testTag("mupa_home_hero"),
    ) {
        // HorizontalPager manual (tanpa auto-advance)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("mupa_home_hero_pager"),
        ) { pageIdx ->
            val page = pages[pageIdx]
            val pagePalette = page.palette
                ?: pagePalettes[page.id]
                ?: extractedPalettes[page.id]
                ?: activeHeroPalette

            Box(modifier = Modifier.fillMaxSize()) {
                // Latar cover (>= 512px) dengan parallax halus 0,5x di fase graphicsLayer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scrollOffsetProvider() * 0.5f
                        }
                        .background(
                            if (appearance.isOledMode) Color.Black else pagePalette.bgDeep,
                        ),
                ) {
                    if (!page.artworkUrl.isNullOrBlank()) {
                        val dataModel: Any = if (page.artworkUrl.startsWith("/")) File(page.artworkUrl) else page.artworkUrl
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(dataModel)
                                .size(640)
                                .crossfade(220)
                                .build(),
                            contentDescription = page.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = MupaPalette.TextPrimary.copy(alpha = 0.14f),
                                modifier = Modifier.size(96.dp),
                            )
                        }
                    }
                }

                // Glow Ultra Dynamic aktif menyatu dengan sampul (berpindah mulus 600ms antar halaman)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (appearance.isOledMode) 0f else 0.52f },
                ) {
                    UltraDynamicBackground(
                        palette = activeHeroPalette,
                        headerOnly = true,
                    )
                }

                // Scrim status bar di atas + gradasi lembut ke warna latar di bawah
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.62f),
                                0.22f to Color.Black.copy(alpha = 0.24f),
                                0.52f to bgColor.copy(alpha = 0.44f),
                                0.82f to bgColor.copy(alpha = 0.90f),
                                1.0f to bgColor,
                            ),
                        ),
                )

                // Konten kiri-bawah Hero: sapaan, kicker berjarak, judul 32/36 Bold, subjudul 14, Putar + Racik ulang
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = MupaSpacing.ScreenHorizontal,
                            end = MupaSpacing.ScreenHorizontal,
                            bottom = 20.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.labelLarge,
                        color = MupaPalette.TextPrimary.copy(alpha = 0.78f),
                        maxLines = 1,
                    )
                    Text(
                        text = page.kicker,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.6.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = if (appearance.isOledMode) MupaPalette.CoralPrimary else activeHeroPalette.onAccent,
                        maxLines = 1,
                    )
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = 32.sp,
                            lineHeight = 36.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MupaPalette.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = page.subtitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MupaPalette.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            MupaPrimaryButton(
                                text = "Putar",
                                icon = Icons.Rounded.PlayArrow,
                                onClick = { onPlayPage(page) },
                            )
                            if (page.canReshuffle) {
                                IconButton(
                                    onClick = onReshuffleMix,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(MupaPalette.Background.copy(alpha = 0.65f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Racik ulang Mix Mupa",
                                        tint = MupaPalette.TextPrimary,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                        }

                        // Spacer kanan untuk titik paginasi yang melayang di sudut kanan-bawah
                        Spacer(modifier = Modifier.width(56.dp))
                    }
                }
            }
        }

        // Bar atas tetap di dalam Hero: logo glyph kiri-atas + MupaSourceSwitch kanan-atas
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(
                    start = MupaSpacing.ScreenHorizontal,
                    end = MupaSpacing.ScreenHorizontal,
                    top = topInset + 12.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MupaLogoGlyph(size = 28.dp)
            MupaSourceSwitch(
                activeSource = activeSource,
                onSwitch = { onSwitchSource?.invoke(it) },
                onDarkOverlay = true,
            )
        }

        // Titik paginasi kanan-bawah (hanya saat > 1 halaman)
        if (pages.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MupaSpacing.ScreenHorizontal, bottom = 32.dp)
                    .testTag("mupa_hero_dots"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { idx, _ ->
                    val active = idx == currentPageIndex
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (active) 20.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (active) MupaPalette.CoralPrimary else MupaPalette.TextPrimary.copy(alpha = 0.35f),
                            ),
                    )
                }
            }
        }
    }
}
