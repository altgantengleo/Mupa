package com.xeno.mupa

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.player.MupaCinemaOverlay
import com.xeno.mupa.ui.player.MupaPlayerOverlayHost
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.player.MupaQueueOverlay
import com.xeno.mupa.ui.player.PlayerOverlayPane
import com.xeno.mupa.ui.player.rememberMupaPlayerSheetState
import org.junit.Assert.assertFalse
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
 * Verifikasi R3:
 * 1. Tap Mini Player membuka Full Now Playing penuh (perbaikan B11).
 * 2. Membuka Mode Cinema & keluar bertahap (Back: Cinema -> Full -> Mini).
 * 3. Render screenshot JVM R3 untuk inspeksi visual langsung: Now Playing, Now Playing + Lirik, Cinema, Antrean.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class R3PlayerAndCinemaUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleQueue = listOf(
        Track("s1", "blue", "yung kai", albumTitle = "stay with the ocean", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING, isFavorite = true),
        Track("s2", "wildflower", "yung kai", albumTitle = "stay with the ocean", durationMs = 198_000L, sourceType = AudioSourceType.STREAMING),
        Track("s3", "Die With A Smile", "Bruno Mars", albumTitle = "Single", durationMs = 251_000L, sourceType = AudioSourceType.STREAMING),
        Track("s4", "Evaluasi", "Hindia", albumTitle = "Menari dengan Bayangan", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING),
    )

    private val sampleLyrics = listOf(
        LyricsLine(0L, 4_000L, "Your morning eyes, I could stare like watching stars"),
        LyricsLine(4_000L, 8_500L, "I could walk you by, and I'll tell without a thought"),
        LyricsLine(8_500L, 13_000L, "You'd be mine, would you mind if I took your hand tonight?"),
        LyricsLine(13_000L, 18_000L, "Know you're all that I want this life"),
        LyricsLine(18_000L, 24_000L, "I'll imagine we fell in love"),
        LyricsLine(24_000L, 29_000L, "I'll nap under moonlight skies with you"),
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
        assertTrue("Screenshot $name must exist and be non-empty", file.exists() && file.length() > 1_000L)
    }

    @Test
    fun tapMiniPlayerOpensFullAndCinemaThenBackStepsDown() {
        var expandedFlag = false
        var cinemaFlag = false

        composeRule.setContent {
            var playerState by remember {
                mutableStateOf(
                    MupaPlayerUiState(
                        currentTrack = sampleQueue.first(),
                        queue = sampleQueue,
                        currentIndex = 0,
                        isPlaying = true,
                        positionMs = 10_200L,
                        bufferedPositionMs = 128_000L,
                        durationMs = 214_000L,
                        lyrics = sampleLyrics,
                    ),
                )
            }
            val sheetState = rememberMupaPlayerSheetState()
            expandedFlag = sheetState.isExpanded
            cinemaFlag = playerState.isCinemaMode

            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaPlayerOverlayHost(
                        playerState = playerState,
                        sheetState = sheetState,
                        stripArtistName = true,
                        bottomBarClearance = 84.dp,
                        positionProvider = { playerState.positionMs },
                        onTogglePlayPause = {},
                        onSeekTo = {},
                        onSkipNext = {},
                        onSkipPrevious = {},
                        onToggleShuffle = {},
                        onCycleRepeat = {},
                        onToggleFavorite = {},
                        onOpenOverlay = { pane ->
                            playerState = playerState.copy(
                                activeOverlay = if (playerState.activeOverlay == pane) PlayerOverlayPane.NONE else pane,
                            )
                        },
                        onCloseOverlay = { playerState = playerState.copy(activeOverlay = PlayerOverlayPane.NONE) },
                        onOpenCinema = { playerState = playerState.copy(isCinemaMode = true) },
                        onCloseCinema = { playerState = playerState.copy(isCinemaMode = false) },
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

        // 1. Awalnya Mini Player terlihat, sheet belum terbuka
        composeRule.onNodeWithTag("mupa_mini_player").assertIsDisplayed()
        assertFalse("Sheet should start collapsed", expandedFlag)

        // 2. Ketuk Mini Player -> Full Now Playing terbuka penuh!
        composeRule.onNodeWithTag("mupa_mini_player").performClick()
        composeRule.waitForIdle()
        assertTrue("Tap Mini Player must expand Full Now Playing", expandedFlag)
        composeRule.onNodeWithTag("mupa_full_now_playing").assertIsDisplayed()
        savePng("r3_now_playing_360dp")

        // 3. Buka panel Lirik inline di Full Now Playing
        composeRule.onNodeWithContentDescription("Lirik").performClick()
        composeRule.waitForIdle()
        savePng("r3_now_playing_lyrics_360dp")

        // 4. Buka Mode Cinema dari Full Now Playing
        composeRule.onNodeWithContentDescription("Mode Cinema").performClick()
        composeRule.waitForIdle()
        assertTrue("Cinema must open", cinemaFlag)
        composeRule.onNodeWithTag("mupa_cinema_overlay").assertIsDisplayed()
        savePng("r3_cinema_360dp")

        // 5. Back #1: Cinema -> Full Now Playing (masih expanded, overlay lirik masih ada)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse("Back #1 must close Cinema", cinemaFlag)
        assertTrue("Back #1 must keep Full Now Playing open", expandedFlag)

        // 6. Back #2: Tutup overlay Lirik -> Full Now Playing
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertTrue("Back #2 closes inline lyrics while staying in Full", expandedFlag)

        // 7. Back #3: Full Now Playing -> Mini Player
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        assertFalse("Back #3 must collapse Full back to Mini", expandedFlag)
    }

    @Test
    fun captureR3QueueAndCinemaNoLyrics() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                MupaQueueOverlay(
                    queue = sampleQueue,
                    currentIndex = 0,
                    stripArtistName = true,
                    onSelectIndex = {},
                    onMoveItem = { _, _ -> },
                    onRemoveItem = {},
                    onClearUpcoming = {},
                    onClose = {},
                )
            }
        }
        savePng("r3_queue_360dp")
    }
}
