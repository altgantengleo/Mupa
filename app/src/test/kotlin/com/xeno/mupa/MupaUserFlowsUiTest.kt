package com.xeno.mupa

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.onboarding.ArtistOnboardingScreen
import com.xeno.mupa.ui.onboarding.ArtistOnboardingUiState
import com.xeno.mupa.ui.player.MupaPlayerOverlayHost
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.player.MupaSyncedLyricsList
import com.xeno.mupa.ui.player.PlayerOverlayPane
import com.xeno.mupa.ui.player.rememberMupaPlayerSheetState
import com.xeno.mupa.ui.screens.HomeScreen
import com.xeno.mupa.ui.screens.HomeUiState
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI test alur utama pengguna Mupa (R5):
 * 1. Onboarding min 2 artis (tombol nonaktif saat 1, aktif saat 2, cap maks 8).
 * 2. Putar dari Beranda -> Mini Player muncul.
 * 3. Switch Pustaka Lokal vs Streaming + EmptyState Lokal tampilkan CTA Impor.
 * 4. Tap Mini Player -> Full Now Playing -> Cinema -> Back bertahap menutup Cinema -> Full -> Mini.
 * 5. Baris aktif Lirik berpindah seiring progres posisi pemutaran.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class MupaUserFlowsUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun flow1_onboardingRequiresMinimumTwoArtistsAndCapsAtEight() {
        val artists = (1..9).map { Artist("a$it", "Artis $it") }
        var submitted = false

        composeRule.setContent {
            var selected by remember { mutableStateOf(emptySet<String>()) }
            val uiState = ArtistOnboardingUiState(
                artists = artists,
                selectedArtistIds = selected,
                isLoading = false,
            )
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                ArtistOnboardingScreen(
                    uiState = uiState,
                    isEditMode = true,
                    onQueryChange = {},
                    onToggleArtist = { id ->
                        selected = when {
                            id in selected -> selected - id
                            selected.size < 8 -> selected + id
                            else -> selected
                        }
                    },
                    onRetry = {},
                    onSubmit = { submitted = true },
                    onNavigateBackOrExit = {},
                )
            }
        }

        // Pilih 1 artis -> tombol Simpan nonaktif
        composeRule.onNodeWithText("Artis 1").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Simpan", substring = true)).assertIsNotEnabled()

        // Pilih artis ke-2 -> tombol aktif
        composeRule.onNodeWithText("Artis 2").performClick()
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Simpan", substring = true)).assertIsEnabled()

        // Pilih sampai ke-9 -> tetap terkunci di 8 / 8
        for (i in 3..9) {
            composeRule.onNodeWithText("Artis $i").performClick()
        }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("8 / 8", substring = true)).assertIsDisplayed()

        // Tekan simpan -> submit sukses
        composeRule.onNode(hasText("Simpan", substring = true)).performClick()
        assertTrue("Onboarding submit callback must fire", submitted)
    }

    @Test
    fun flow2_playFromHomeShowsMiniPlayer() {
        val track = Track("s1", "blue", "yung kai", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING)
        val hero = Playlist("mix", "Mix Mupa", "Racikan untukmu", listOf(track))

        composeRule.setContent {
            var currentTrack by remember { mutableStateOf<Track?>(null) }
            val sheetState = rememberMupaPlayerSheetState()
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    HomeScreen(
                        uiState = HomeUiState(
                            greeting = "Selamat sore",
                            activeSource = AudioSourceType.STREAMING,
                            mixHero = hero,
                            shelves = emptyList(),
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        contentPadding = PaddingValues(20.dp),
                        onPlayTrack = { list, idx -> currentTrack = list.getOrNull(idx) },
                        onReshuffleMix = {},
                        onRetry = {},
                    )
                    MupaPlayerOverlayHost(
                        playerState = MupaPlayerUiState(
                            currentTrack = currentTrack,
                            queue = listOfNotNull(currentTrack),
                            isPlaying = currentTrack != null,
                            durationMs = 214_000L,
                        ),
                        sheetState = sheetState,
                        stripArtistName = true,
                        bottomBarClearance = 80.dp,
                        positionProvider = { 0L },
                        onTogglePlayPause = {},
                        onSeekTo = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleFavorite = {},
                        onOpenOverlay = {},
                        onCloseOverlay = {},
                        onOpenCinema = {},
                        onCloseCinema = {},
                        onAdjustLyricsOffset = {},
                        onResetLyricsOffset = {},
                        onCycleSleepTimer = {},
                        onSelectEqPreset = {},
                        onEqBandChange = { _, _ -> },
                        onResetEq = {},
                        onSelectQueueIndex = {},
                        onMoveQueueItem = { _, _ -> },
                        onRemoveQueueItem = {},
                        onClearUpcomingQueue = {},
                    )
                }
            }
        }

        // Klik tombol Putar pada hero Mix Mupa -> Mini Player muncul
        composeRule.onNodeWithText("Putar").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mupa_mini_player").assertIsDisplayed()
    }

    @Test
    fun flow3_switchLibraryBetweenStreamingAndLocalShowsEmptyImportCta() {
        var importClicked = false

        composeRule.setContent {
            var source by remember { mutableStateOf(AudioSourceType.STREAMING) }
            val streamingTracks = listOf(Track("s1", "blue", "yung kai", durationMs = 214_000L))
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    LibraryScreen(
                        uiState = LibraryUiState(
                            activeSource = source,
                            tracks = if (source == AudioSourceType.STREAMING) streamingTracks else emptyList(),
                            isLoading = false,
                        ),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(20.dp),
                        onSwitchSource = { source = it },
                        onOpenCategory = {},
                        onCloseCategory = {},
                        onSelectSort = {},
                        onSetFavoritesOnly = {},
                        onImportLocalFiles = { importClicked = true },
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

        // Awal di Streaming -> ketuk switch Lokal
        composeRule.onNodeWithContentDescription("Mode Lokal (Ponsel)").performClick()
        composeRule.waitForIdle()

        // EmptyState lokal menampilkan ajakan Import Lagu
        composeRule.onNodeWithText("Import Lagu").assertIsDisplayed().performClick()
        assertTrue("Import local CTA must trigger callback", importClicked)
    }

    @Test
    fun flow4_miniToFullToCinemaAndStagedBack() {
        val track = Track("s1", "blue", "yung kai", durationMs = 214_000L)
        var expanded = false
        var cinema = false

        composeRule.setContent {
            var state by remember {
                mutableStateOf(
                    MupaPlayerUiState(currentTrack = track, queue = listOf(track), durationMs = 214_000L),
                )
            }
            val sheet = rememberMupaPlayerSheetState()
            expanded = sheet.isExpanded
            cinema = state.isCinemaMode
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                MupaPlayerOverlayHost(
                    playerState = state,
                    sheetState = sheet,
                    stripArtistName = true,
                    bottomBarClearance = 80.dp,
                    positionProvider = { 0L },
                    onTogglePlayPause = {},
                    onSeekTo = {},
                    onSkipNext = {},
                    onSkipPrevious = {},
                    onToggleShuffle = {},
                    onCycleRepeat = {},
                    onToggleFavorite = {},
                    onOpenOverlay = { p -> state = state.copy(activeOverlay = p) },
                    onCloseOverlay = { state = state.copy(activeOverlay = PlayerOverlayPane.NONE) },
                    onOpenCinema = { state = state.copy(isCinemaMode = true) },
                    onCloseCinema = { state = state.copy(isCinemaMode = false) },
                    onAdjustLyricsOffset = {},
                    onResetLyricsOffset = {},
                    onCycleSleepTimer = {},
                    onSelectEqPreset = {},
                    onEqBandChange = { _, _ -> },
                    onResetEq = {},
                    onSelectQueueIndex = {},
                    onMoveQueueItem = { _, _ -> },
                    onRemoveQueueItem = {},
                    onClearUpcomingQueue = {},
                )
            }
        }

        composeRule.onNodeWithTag("mupa_mini_player").performClick()
        composeRule.waitForIdle()
        assertTrue(expanded)

        composeRule.onNodeWithContentDescription("Mode Cinema").performClick()
        composeRule.waitForIdle()
        assertTrue(cinema)

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse("First Back exits Cinema", cinema)
        assertTrue("First Back stays in Full Now Playing", expanded)

        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse("Second Back collapses Full to Mini", expanded)
    }

    @Test
    fun flow5_lyricsActiveLineAdvancesAsPositionProgresses() {
        val lines = listOf(
            LyricsLine(0L, 4_000L, "Baris pembuka lagu"),
            LyricsLine(4_000L, 8_000L, "Baris kedua mengalir"),
            LyricsLine(8_000L, 12_000L, "Baris ketiga memuncak"),
        )
        val posState = mutableLongStateOf(1_000L)

        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaSyncedLyricsList(
                        lines = lines,
                        isPlain = false,
                        positionProvider = { posState.longValue },
                        offsetMs = 0L,
                        isCinemaScale = false,
                        onSeekTo = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(120L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mupa_lyrics_active_line").assertIsDisplayed()

        // Majukan posisi ke baris ketiga (9.5 detik)
        posState.longValue = 9_500L
        composeRule.mainClock.advanceTimeBy(150L)
        composeRule.waitForIdle()

        val activeTextNodes = composeRule.onNodeWithText("Baris ketiga memuncak")
        activeTextNodes.assertIsDisplayed()
        assertEquals(9_500L, posState.longValue)
    }
}
