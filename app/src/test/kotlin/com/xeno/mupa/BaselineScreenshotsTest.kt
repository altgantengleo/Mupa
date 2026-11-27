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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.component.UltraDynamicBackground
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.ShelfComposer
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.navigation.MupaFloatingBottomBar
import com.xeno.mupa.navigation.MupaTopLevelDestination
import com.xeno.mupa.ui.player.MiniPlayerBar
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
 * Verifikasi visual R2 (Mupa Calm) pada JVM memakai Robolectric Native Graphics.
 * Menyimpan hasil ke docs/screens/ untuk diperiksa langsung (bukti B1-B10 & B12).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class BaselineScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val streamingTracks = listOf(
        Track("s1", "blue", "yung kai", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING),
        Track("s2", "wildflower", "yung kai", durationMs = 0L, sourceType = AudioSourceType.STREAMING),
        Track("s3", "Die With A Smile", "Bruno Mars", durationMs = 251_000L, sourceType = AudioSourceType.STREAMING),
        Track("s4", "Evaluasi", "Hindia", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING),
        Track("s5", "Satu Bulan", "Bernadya", durationMs = 204_000L, sourceType = AudioSourceType.STREAMING),
    )

    private val localTracks = listOf(
        Track("content://loc/1", "Nadin Amizah - Bertaut (Official Audio)", "Nadin Amizah", albumTitle = "Selamat Ulang Tahun", durationMs = 256_000L, sourceType = AudioSourceType.LOCAL, originalFileName = "Nadin Amizah - Bertaut (Official Audio).flac", isFavorite = true, playCount = 12),
        Track("content://loc/2", "To the Bone", "Pamungkas", albumTitle = "Flying Solo", durationMs = 344_000L, sourceType = AudioSourceType.LOCAL, originalFileName = "Pamungkas - To the Bone.mp3", isFavorite = true, playCount = 9),
        Track("content://loc/3", "Dan", "Sheila On 7", albumTitle = "Sheila On 7", durationMs = 286_000L, sourceType = AudioSourceType.LOCAL, originalFileName = "Sheila On 7 - Dan.flac", isFavorite = false, playCount = 0),
    )

    private fun saveScreenPng(name: String) {
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
    fun captureR2HomeWithChromeScrim() {
        val hero = Playlist("shelf_mix_mupa", "Mix Mupa", "Racikan dari yung kai & Bruno Mars", streamingTracks)
        val shelves = ShelfComposer.compose(
            listOf(
                Playlist("shelf_for_you", "Untukmu", "Berdasarkan yung kai, Bruno Mars", streamingTracks.take(3)),
                Playlist("shelf_repeat", "Ulangi lagi", "Nyaman diputar ulang", streamingTracks.drop(3)),
            ),
        )
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                val bg = MaterialTheme.colorScheme.background
                Box(Modifier.fillMaxSize().background(bg)) {
                    UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
                    HomeScreen(
                        uiState = HomeUiState(
                            greeting = "Selamat sore",
                            activeSource = AudioSourceType.STREAMING,
                            mixHero = hero,
                            shelves = shelves,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 176.dp),
                        onPlayTrack = { _, _ -> },
                        onReshuffleMix = {},
                        onRetry = {},
                    )
                    // Scrim + solid MiniPlayer + solid BottomBar (bukti B1 & B6)
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
                        track = streamingTracks.first(),
                        isPlaying = true,
                        progressFraction = 0.35f,
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
                        currentDestination = MupaTopLevelDestination.HOME,
                        onSelectDestination = {},
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
        saveScreenPng("r2_home_360dp")
    }

    @Test
    fun captureR2SearchResults() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
                    SearchScreen(
                        uiState = SearchUiState(
                            query = "yung",
                            source = AudioSourceType.STREAMING,
                            results = streamingTracks,
                            isLoading = false,
                            hasSearched = true,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = "s1",
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
        saveScreenPng("r2_search_results_360dp")
    }

    @Test
    fun captureR2LibraryStreamingAndLocalEmpty() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
                    LibraryScreen(
                        uiState = LibraryUiState(
                            activeSource = AudioSourceType.LOCAL,
                            tracks = localTracks,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
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
        saveScreenPng("r2_library_local_filled_360dp")
    }

    @Test
    fun captureR2LibraryLocalEmpty() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
                    LibraryScreen(
                        uiState = LibraryUiState(
                            activeSource = AudioSourceType.LOCAL,
                            tracks = emptyList(),
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
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
        saveScreenPng("r2_library_local_empty_360dp")
    }

    @Test
    fun captureR2Account() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    UltraDynamicBackground(palette = UltraDynamicPalette.BrandNoCover, headerOnly = true)
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
        saveScreenPng("r2_account_360dp")
    }
}
