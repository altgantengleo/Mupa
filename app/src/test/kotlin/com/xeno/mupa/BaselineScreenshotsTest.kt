package com.xeno.mupa

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.component.MupaAuroraBackground
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
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
 * Screenshot baseline R0 (sebelum redesign Fase 2R) pada JVM memakai Robolectric Native Graphics.
 * Mengonfirmasi secara visual kondisi awal Beranda, Cari, Pustaka, dan Akun di docs/screens/baseline/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class BaselineScreenshotsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baselineTracks = listOf(
        Track("t1", "blue", "yung kai", durationMs = 0L, sourceType = AudioSourceType.STREAMING),
        Track("t2", "Blue Oiia", "yung kai", durationMs = 0L, sourceType = AudioSourceType.STREAMING),
        Track("t3", "Bertaut", "Nadin Amizah", durationMs = 256_000L, sourceType = AudioSourceType.LOCAL, isFavorite = true),
        Track("t4", "To the Bone", "Pamungkas", durationMs = 344_000L, sourceType = AudioSourceType.LOCAL, isFavorite = true),
    )

    private fun saveBaselinePng(name: String) {
        composeRule.waitForIdle()
        val dir = File("../docs/screens/baseline").apply { mkdirs() }
        val file = File(dir, "$name.png")
        val contentView = composeRule.activity.findViewById<View>(android.R.id.content)
        val bmp = contentView.drawToBitmap(Bitmap.Config.ARGB_8888)
        // Jangan timpa screenshot baseline R0 jika sudah ada (bukti sebelum Fase 2R)
        if (!file.exists()) {
            FileOutputStream(file).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        assertTrue("Rendered bitmap must be valid", bmp.width > 0 && bmp.height > 0)
    }

    @Test
    fun captureBaselineHome() {
        val shelf = Playlist("shelf_mix_mupa", "Mix Mupa", "Racikan dari selera artis & pustaka lokalmu", baselineTracks)
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    HomeScreen(
                        uiState = HomeUiState(
                            greeting = "Selamat sore",
                            shelves = listOf(
                                Playlist("shelf_for_you", "Untukmu", "Berdasarkan yung kai, Bruno Mars", baselineTracks),
                                shelf,
                            ),
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        contentPadding = PaddingValues(20.dp),
                        onPlayTrack = { _, _ -> },
                        onReshuffleMix = {},
                        onRetry = {},
                    )
                }
            }
        }
        saveBaselinePng("r0_home_360dp")
    }

    @Test
    fun captureBaselineSearch() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    SearchScreen(
                        uiState = SearchUiState(results = baselineTracks, isLoading = false),
                        stripArtistName = true,
                        contentPadding = PaddingValues(20.dp),
                        onQueryChange = {},
                        onSelectFilter = {},
                        onSubmitQuery = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        saveBaselinePng("r0_search_360dp")
    }

    @Test
    fun captureBaselineLibrary() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    LibraryScreen(
                        uiState = LibraryUiState(
                            favoritesOnly = true,
                            localOnly = true,
                            allTracks = baselineTracks,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        contentPadding = PaddingValues(20.dp),
                        onSelectTab = {},
                        onSelectSort = {},
                        onToggleFavoritesOnly = {},
                        onToggleLocalOnly = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }
        saveBaselinePng("r0_library_360dp")
    }

    @Test
    fun captureBaselineAccount() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance()) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaAuroraBackground()
                    AccountScreen(
                        preferences = UserPreferences(),
                        favoriteArtists = listOf(Artist("1", "yung kai"), Artist("2", "Bruno Mars")),
                        totalTracksCount = 14,
                        favoriteTracksCount = 8,
                        contentPadding = PaddingValues(20.dp),
                        onEditFavoriteArtists = {},
                        onOpenSettings = {},
                        onOledModeChange = {},
                    )
                }
            }
        }
        saveBaselinePng("r0_account_360dp")
    }
}
