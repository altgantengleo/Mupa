package com.xeno.mupa

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.component.MupaAuroraBackground
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.ShelfComposer
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.navigation.MupaFloatingBottomBar
import com.xeno.mupa.navigation.MupaTopLevelDestination
import com.xeno.mupa.ui.player.FullNowPlayingContent
import com.xeno.mupa.ui.player.MiniPlayerBar
import com.xeno.mupa.ui.player.MupaCinemaOverlay
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.screens.AccountScreen
import com.xeno.mupa.ui.screens.HomeScreen
import com.xeno.mupa.ui.screens.HomeUiState
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryUiState
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Matriks screenshot R5 lengkap di JVM (Robolectric Native Graphics):
 * - Lebar 360dp & 411dp
 * - fontScale 1.0f & 1.3f
 * - OLED OFF & OLED ON
 * - Semua status Cari (awal / memuat / error), Pustaka Streaming, Beranda panjang + Mini+Nav (bukti B1),
 *   Now Playing 411dp OLED, Cinema OLED, Akun fontScale 1.3x.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class R5ScreenshotMatrixTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val tracks = (1..12).map { i ->
        Track(
            id = "t$i",
            title = listOf("blue", "wildflower", "Die With A Smile", "Evaluasi", "Satu Bulan", "Bertaut", "To the Bone", "Dan", "Hati-Hati di Jalan", "Monokrom", "Sorai", "Secukupnya")[i - 1],
            artistName = listOf("yung kai", "yung kai", "Bruno Mars", "Hindia", "Bernadya", "Nadin Amizah", "Pamungkas", "Sheila On 7", "Tulus", "Tulus", "Nadin Amizah", "Hindia")[i - 1],
            durationMs = if (i == 2) 0L else (180_000L + i * 11_000L),
            sourceType = AudioSourceType.STREAMING,
            isFavorite = i <= 4,
        )
    }

    private val lyrics = listOf(
        LyricsLine(0L, 4_000L, "Your morning eyes, I could stare like watching stars"),
        LyricsLine(4_000L, 8_500L, "I could walk you by, and I'll tell without a thought"),
        LyricsLine(8_500L, 13_000L, "You'd be mine, would you mind if I took your hand tonight?"),
        LyricsLine(13_000L, 18_000L, "Know you're all that I want this life"),
    )

    private fun savePng(name: String) {
        composeRule.waitForIdle()
        val dir = File("../docs/screens").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val contentView = composeRule.activity.findViewById<View>(android.R.id.content)
        val bmp = contentView.drawToBitmap(Bitmap.Config.ARGB_8888)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        assertTrue("Screenshot $name must be valid", file.exists() && file.length() > 1_000L)
    }

    @Test
    fun captureB1LongListWithMiniAndNavScrim360dp() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                val bg = MaterialTheme.colorScheme.background
                Box(Modifier.fillMaxSize().background(bg)) {
                    MupaAuroraBackground()
                    SearchScreen(
                        uiState = SearchUiState(
                            query = "a",
                            source = AudioSourceType.STREAMING,
                            results = tracks,
                            isLoading = false,
                            hasSearched = true,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = "t1",
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 176.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSubmitQuery = {},
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                    // Scrim + solid MiniPlayer + solid BottomBar menumpuk di atas daftar panjang
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(172.dp)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.22f to bg.copy(alpha = 0.88f),
                                    1f to bg.copy(alpha = 0.96f),
                                ),
                            ),
                    )
                    MiniPlayerBar(
                        track = tracks.first(),
                        isPlaying = true,
                        progressFraction = 0.42f,
                        onExpand = {},
                        onTogglePlayPause = {},
                        onSkipNext = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(start = 20.dp, end = 20.dp, bottom = 84.dp)
                            .fillMaxWidth()
                            .height(64.dp),
                    )
                    MupaFloatingBottomBar(
                        currentDestination = MupaTopLevelDestination.SEARCH,
                        onSelectDestination = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
        savePng("r5_search_longlist_b1_360dp")
    }

    @Test
    fun captureSearchIdleLoadingError360dp() {
        // 1. Cari awal (recent queries)
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    SearchScreen(
                        uiState = SearchUiState(
                            query = "",
                            recentQueries = listOf("Hindia", "Nadin Amizah", "yung kai"),
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSubmitQuery = {},
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        savePng("r5_search_idle_360dp")
    }

    @Test
    fun captureSearchLoadingAndError360dp() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SearchScreen(
                        uiState = SearchUiState(query = "nadin", isLoading = true, hasSearched = true),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSubmitQuery = {},
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        savePng("r5_search_loading_360dp")
    }

    @Test
    fun captureSearchError360dp() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SearchScreen(
                        uiState = SearchUiState(
                            query = "nadin",
                            isLoading = false,
                            hasSearched = true,
                            errorMessage = "Tidak ada koneksi internet. Periksa jaringan lalu coba lagi.",
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSubmitQuery = {},
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        savePng("r5_search_error_360dp")
    }

    @Test
    fun captureLibraryStreaming360dp() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    LibraryScreen(
                        uiState = LibraryUiState(
                            activeSource = AudioSourceType.STREAMING,
                            tracks = tracks,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = "t1",
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                        onSwitchSource = {},
                        onOpenCategory = {},
                        onCloseCategory = {},
                        onSelectSort = {},
                        onSetFavoritesOnly = {},
                        onImportLocalFiles = {},
                        onImportLocalFolder = {},
                        onRescanFolders = {},
                        onRemoveLocalTrack = {},
                        onToggleFavoriteTrack = {},
                        onCreatePlaylist = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        savePng("r5_library_streaming_360dp")
    }

    @Test
    fun captureHomeAndAccountFontScale13_360dp() {
        val hero = Playlist("mix", "Mix Mupa", "Racikan dari yung kai & Bruno Mars", tracks.take(6))
        val shelves = ShelfComposer.compose(
            listOf(
                Playlist("s_for_you", "Untukmu", "Berdasarkan yung kai, Bruno Mars", tracks.take(4)),
                Playlist("s_repeat", "Ulangi lagi", "Nyaman diputar ulang", tracks.drop(4).take(4)),
            ),
        )
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        MupaAuroraBackground()
                        HomeScreen(
                            uiState = HomeUiState(
                                greeting = "Selamat sore",
                                activeSource = AudioSourceType.STREAMING,
                                mixHero = hero,
                                shelves = shelves,
                                isLoading = false,
                            ),
                            stripArtistName = true,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                            onPlayTrack = { _, _ -> },
                            onReshuffleMix = {},
                            onRetry = {},
                        )
                    }
                }
            }
        }
        savePng("r5_home_360dp_font13")
    }

    @Test
    fun captureAccountFontScale13_360dp() {
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 1.3f)) {
                MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        AccountScreen(
                            preferences = UserPreferences(),
                            favoriteArtists = listOf(Artist("1", "yung kai"), Artist("2", "Bruno Mars")),
                            totalTracksCount = 42,
                            favoriteTracksCount = 12,
                            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                            onEditFavoriteArtists = {},
                            onGlassStyleChange = {},
                            onOledModeChange = {},
                            onAuroraEnabledChange = {},
                            onPerformanceModeChange = {},
                            onStripArtistNameChange = {},
                        )
                    }
                }
            }
        }
        savePng("r5_account_360dp_font13")
    }

    @Test
    @Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
    fun captureHomeAndNowPlaying411dpOled() {
        val hero = Playlist("mix", "Mix Mupa", "Racikan dari yung kai & Bruno Mars", tracks.take(6))
        val shelves = ShelfComposer.compose(
            listOf(
                Playlist("s_for_you", "Untukmu", "Berdasarkan yung kai, Bruno Mars", tracks.take(4)),
                Playlist("s_repeat", "Ulangi lagi", "Nyaman diputar ulang", tracks.drop(4).take(4)),
            ),
        )
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID, isOledMode = true, isAuroraEnabled = false)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            greeting = "Selamat malam",
                            activeSource = AudioSourceType.STREAMING,
                            mixHero = hero,
                            shelves = shelves,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                        onPlayTrack = { _, _ -> },
                        onReshuffleMix = {},
                        onRetry = {},
                    )
                }
            }
        }
        savePng("r5_home_411dp_oled")
    }

    @Test
    @Config(application = Application::class, sdk = [34], qualifiers = "w411dp-h915dp-xhdpi")
    fun captureNowPlaying411dpOled() {
        val track = tracks.first()
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID, isOledMode = true, isAuroraEnabled = false)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    FullNowPlayingContent(
                        track = track,
                        stripArtistName = true,
                        playerState = MupaPlayerUiState(
                            currentTrack = track,
                            queue = tracks.take(4),
                            currentIndex = 0,
                            isPlaying = true,
                            positionMs = 64_000L,
                            bufferedPositionMs = 150_000L,
                            durationMs = 191_000L,
                        ),
                        progressProvider = { 1f },
                        positionProvider = { 64_000L },
                        maxWidthDp = 411.dp,
                        onCollapse = {},
                        onTogglePlayPause = {},
                        onSeekTo = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleFavorite = {},
                        onToggleLyrics = {},
                        onOpenQueue = {},
                        onOpenEqualizer = {},
                        onOpenCinema = {},
                        onAdjustLyricsOffset = {},
                        onResetLyricsOffset = {},
                        onCycleSleepTimer = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        savePng("r5_now_playing_411dp_oled")
    }

    @Test
    fun captureCinemaOled360dp() {
        val track = tracks.first()
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID, isOledMode = true, isAuroraEnabled = false)) {
                MupaCinemaOverlay(
                    track = track,
                    stripArtistName = true,
                    playerState = MupaPlayerUiState(
                        currentTrack = track,
                        queue = tracks.take(4),
                        isPlaying = true,
                        positionMs = 10_200L,
                        durationMs = 191_000L,
                        lyrics = lyrics,
                        isCinemaMode = true,
                    ),
                    positionProvider = { 10_200L },
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onExitCinema = {},
                )
            }
        }
        savePng("r5_cinema_oled_360dp")
    }
}
