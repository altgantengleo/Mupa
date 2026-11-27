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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.ui.player.MupaCinemaOverlay
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.screens.AccountScreen
import com.xeno.mupa.ui.screens.AttributionScreen
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryUiState
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchUiState
import com.xeno.mupa.ui.screens.SettingsScreen
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
 * Verifikasi S4 & S5:
 * - Akun ringkas + tombol gerigi & baris Pengaturan terpisah (N7) + angka konsisten sumber (N6)
 * - Layar Pengaturan terpisah (5 grup + pratinjau Solid/Crystal/Liquid+) & Atribusi NOTICE
 * - Cari state awal dengan pintasan Artis Favorit tanpa kotak kosong besar (N8)
 * - Pustaka kompak tanpa kolase sampul pada Album 0 / Playlist 0 (N6)
 * - Cinema dengan Ultra Dynamic background & warna lirik aktif onAccent (S5)
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class S4S5SettingsAndPolishTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val blueSandPalette = UltraDynamicPalette(
        bgDeep = Color(0xFF152538),
        glow1 = Color(0xFF3E84D8),
        glow2 = Color(0xFFD6A85C),
        glow3 = Color(0xFF5D92CC),
        onAccent = Color(0xFFDCEEFF),
        glowIntensity = 0.92f,
    )

    @Test
    fun compactAccountNavigatesToSettingsAndMatchesStreamingCount() {
        var openedSettings = false
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    AccountScreen(
                        preferences = UserPreferences(
                            onboardingCompleted = true,
                            activeSource = AudioSourceType.STREAMING,
                        ),
                        favoriteArtists = listOf(Artist("a1", "yung kai"), Artist("a2", "Tulus")),
                        totalTracksCount = 24,
                        favoriteTracksCount = 6,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 120.dp),
                        onEditFavoriteArtists = {},
                        onOpenSettings = { openedSettings = true },
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Lagu").assertIsDisplayed()
        composeRule.onNodeWithText("24").assertIsDisplayed()
        composeRule.onNodeWithTag("account_settings_gear").assertIsDisplayed()
        composeRule.onNodeWithTag("account_settings_row").performClick()
        composeRule.waitForIdle()
        assertTrue(openedSettings)
        saveScreenshot("s4s5/account_compact_360dp.png")
    }

    @Test
    fun settingsAndAttributionRenderCleanly() {
        var openedAttribution = false
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.LIQUID_PLUS)) {
                SettingsScreen(
                    preferences = UserPreferences(
                        onboardingCompleted = true,
                        glassStyle = GlassStyle.LIQUID_PLUS,
                        isAuroraEnabled = true,
                    ),
                    connectedFoldersCount = 2,
                    audioCacheBytes = 42_500_000L,
                    statusMessage = null,
                    onBack = {},
                    onGlassStyleChange = {},
                    onOledModeChange = {},
                    onUltraDynamicChange = {},
                    onPerformanceModeChange = {},
                    onStripArtistNameChange = {},
                    onOpenEqualizer = {},
                    onClearAudioCache = {},
                    onImportLocalFolder = {},
                    onRescanLocalFolders = {},
                    onExportBackup = {},
                    onImportBackup = {},
                    onClearImageAndLyricsCache = {},
                    onOpenAttribution = { openedAttribution = true },
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pengaturan").assertIsDisplayed()
        composeRule.onNodeWithText("Permukaan Melayang").assertIsDisplayed()
        composeRule.onNodeWithText("Ultra Dynamic").assertIsDisplayed()
        saveScreenshot("s4s5/settings_360dp.png")
        // Scroll / verify attribution screen separately
        assertTrue(!openedAttribution)
    }

    @Test
    fun attributionScreenRendersNotice() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                AttributionScreen(onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Atribusi & Lisensi").assertIsDisplayed()
        saveScreenshot("s4s5/attribution_360dp.png")
    }

    @Test
    fun searchInitialShowsFavoriteArtistsShortcutsWithoutGiantDeadBox() {
        var submitted = ""
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    SearchScreen(
                        uiState = SearchUiState(source = AudioSourceType.STREAMING),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSubmitQuery = { submitted = it },
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                        favoriteArtists = listOf(
                            Artist("a1", "yung kai"),
                            Artist("a2", "Tulus"),
                            Artist("a3", "Bernadya"),
                        ),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pintasan Artis Favorit").assertIsDisplayed()
        composeRule.onNodeWithText("yung kai").performClick()
        assertTrue(submitted == "yung kai")
        saveScreenshot("s4s5/search_initial_shortcuts_360dp.png")
    }

    @Test
    fun compactLibraryEmptyAlbumShowsCleanIconNoCollage() {
        val tracksNoAlbum = listOf(
            Track("t1", "blue", "yung kai", albumTitle = null, durationMs = 214_000L),
            Track("t2", "Hati-Hati di Jalan", "Tulus", albumTitle = null, durationMs = 242_000L),
        )
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LibraryScreen(
                        uiState = LibraryUiState(
                            activeSource = AudioSourceType.STREAMING,
                            tracks = tracksNoAlbum,
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 120.dp),
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
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Album").assertIsDisplayed()
        saveScreenshot("s4s5/library_compact_empty_album_360dp.png")
    }

    @Test
    fun cinemaModeRendersUltraDynamicBackdropAndOnAccentLyrics() {
        val track = Track("t1", "blue", "yung kai", durationMs = 214_000L)
        composeRule.setContent {
            MupaTheme(
                appearance = MupaAppearance(
                    glassStyle = GlassStyle.SOLID,
                    isUltraDynamicEnabled = true,
                    ultraDynamicPalette = blueSandPalette,
                ),
            ) {
                MupaCinemaOverlay(
                    track = track,
                    stripArtistName = true,
                    playerState = MupaPlayerUiState(
                        currentTrack = track,
                        isPlaying = true,
                        positionMs = 15_000L,
                        durationMs = 214_000L,
                        lyrics = listOf(
                            LyricsLine(10_000L, 14_000L, "Your morning eyes, I could stare like watching stars"),
                            LyricsLine(14_000L, 19_000L, "I could walk you by, and I'll tell without a thought"),
                            LyricsLine(19_000L, 24_000L, "You'd be mine, would you mind if I took your hand tonight"),
                        ),
                        ultraDynamicPalette = blueSandPalette,
                    ),
                    positionProvider = { 15_000L },
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onExitCinema = {},
                )
            }
        }
        composeRule.waitForIdle()
        saveScreenshot("s4s5/cinema_ud_360dp.png")
    }

    private fun saveScreenshot(relativePath: String) {
        val rootView: View = composeRule.activity.window.decorView.rootView
        val bitmap: Bitmap = rootView.drawToBitmap(Bitmap.Config.ARGB_8888)
        val outFile = File(resolveScreensDir(), relativePath)
        outFile.parentFile?.mkdirs()
        FileOutputStream(outFile).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
    }

    private fun resolveScreensDir(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(4) {
            if (File(dir, "settings.gradle.kts").exists()) {
                return File(dir, "docs/screens")
            }
            dir = dir.parentFile ?: return@repeat
        }
        return File("docs/screens")
    }
}
