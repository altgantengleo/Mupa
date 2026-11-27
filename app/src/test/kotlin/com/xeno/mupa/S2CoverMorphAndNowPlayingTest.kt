package com.xeno.mupa

import android.app.Application
import android.graphics.Bitmap
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.player.MupaPlayerMorphMath
import com.xeno.mupa.ui.player.MupaPlayerOverlayHost
import com.xeno.mupa.ui.player.MupaPlayerUiState
import com.xeno.mupa.ui.player.rememberMupaPlayerSheetState
import org.junit.Assert.assertEquals
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
 * Verifikasi S2 (Animasi Cover Mini <-> Full & Perbaikan N1, N9):
 * 1. Unit test pemetaan progres di 0, 0.25, 0.35, 0.7, 1.0 + durasi buka 520ms / tutup 440ms / lepas drag >= 250ms.
 * 2. Capture frame animasi kontinuitas posisi, ukuran, & radius di progres 0, 0.25, 0.5, 0.75, 1.0 (buka & tutup)
 *    ke docs/screens/anim/ untuk inspeksi visual langsung!
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class S2CoverMorphAndNowPlayingTest {

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

    private val queue = (1..10).map { i ->
        Track("s$i", if (i == 1) "blue" else "Lagu $i", "yung kai", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING, isFavorite = i == 1)
    }

    @Test
    fun morphProgressMathMatchesChoreographySpecAtKeyPoints() {
        // Durasi >= 400ms (buka 520ms, tutup 440ms), lepas drag min 250ms
        assertEquals(520, MupaPlayerMorphMath.OPEN_DURATION_MS)
        assertEquals(440, MupaPlayerMorphMath.CLOSE_DURATION_MS)
        assertTrue(MupaPlayerMorphMath.OPEN_DURATION_MS >= 400)
        assertTrue(MupaPlayerMorphMath.CLOSE_DURATION_MS >= 400)
        assertEquals(250, MupaPlayerMorphMath.proportionalReleaseDurationMs(0.85f, 1.0f))
        assertEquals(520, MupaPlayerMorphMath.proportionalReleaseDurationMs(0.0f, 1.0f))
        assertEquals(440, MupaPlayerMorphMath.proportionalReleaseDurationMs(1.0f, 0.0f))

        // p = 0.0 (Mini penuh)
        assertEquals(1f, MupaPlayerMorphMath.miniContentAlpha(0f), 0.001f)
        assertEquals(0f, MupaPlayerMorphMath.coverMorphFraction(0f), 0.001f)
        assertEquals(0f, MupaPlayerMorphMath.udBackdropAlpha(0f), 0.001f)
        assertEquals(1f, MupaPlayerMorphMath.bottomNavAlpha(0f), 0.001f)
        assertEquals(0f, MupaPlayerMorphMath.staggeredElementProgress(0f, 0), 0.001f)

        // p = 0.25 (isi mini selesai memudar)
        assertEquals(0f, MupaPlayerMorphMath.miniContentAlpha(0.25f), 0.001f)
        assertEquals(0.25f, MupaPlayerMorphMath.coverMorphFraction(0.25f), 0.001f)
        assertTrue(MupaPlayerMorphMath.udBackdropAlpha(0.25f) in 0.34f..0.37f)
        assertEquals(0f, MupaPlayerMorphMath.staggeredElementProgress(0.25f, 0), 0.001f)

        // p = 0.35 (kontrol full mulai masuk)
        assertEquals(0f, MupaPlayerMorphMath.staggeredElementProgress(0.35f, 0), 0.001f)
        assertEquals(0.5f, MupaPlayerMorphMath.udBackdropAlpha(0.35f), 0.01f)
        assertTrue(MupaPlayerMorphMath.bottomNavAlpha(0.35f) in 0.10f..0.15f)

        // p = 0.70 (latar UD penuh 100%, Bottom Nav sudah hilang)
        assertEquals(1f, MupaPlayerMorphMath.udBackdropAlpha(0.70f), 0.001f)
        assertEquals(0f, MupaPlayerMorphMath.bottomNavAlpha(0.70f), 0.001f)
        assertTrue(MupaPlayerMorphMath.staggeredElementProgress(0.70f, 0) > 0.5f)
        assertTrue(MupaPlayerMorphMath.staggeredElementProgress(0.70f, 3) < MupaPlayerMorphMath.staggeredElementProgress(0.70f, 0))

        // p = 1.0 (Full penuh)
        assertEquals(0f, MupaPlayerMorphMath.miniContentAlpha(1f), 0.001f)
        assertEquals(1f, MupaPlayerMorphMath.coverMorphFraction(1f), 0.001f)
        assertEquals(1f, MupaPlayerMorphMath.udBackdropAlpha(1f), 0.001f)
        assertEquals(1f, MupaPlayerMorphMath.staggeredElementProgress(1f, 0), 0.001f)
        assertEquals(1f, MupaPlayerMorphMath.staggeredElementProgress(1f, 3), 0.001f)
        assertEquals(0f, MupaPlayerMorphMath.staggeredSlideDp(1f, 3), 0.001f)
    }

    @Test
    fun captureSymmetricCoverMorphFramesOpenAndClose() {
        val progressTarget = mutableFloatStateOf(0f)

        composeRule.setContent {
            val sheetState = rememberMupaPlayerSheetState()
            val target = progressTarget.floatValue
            LaunchedEffect(target) {
                sheetState.snapToForTest(target)
            }
            MupaTheme(
                appearance = MupaAppearance(
                    glassStyle = GlassStyle.SOLID,
                    isUltraDynamicEnabled = true,
                    ultraDynamicPalette = blueSandPalette,
                ),
            ) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    MupaPlayerOverlayHost(
                        playerState = MupaPlayerUiState(
                            currentTrack = queue.first(),
                            queue = queue,
                            currentIndex = 0,
                            isPlaying = true,
                            positionMs = 45_000L,
                            bufferedPositionMs = 150_000L,
                            durationMs = 214_000L,
                            ultraDynamicPalette = blueSandPalette,
                        ),
                        sheetState = sheetState,
                        stripArtistName = true,
                        bottomBarClearance = 84.dp,
                        positionProvider = { 45_000L },
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

        val dir = File("../docs/screens/anim").apply { mkdirs() }
        // Buka: 0.0 -> 0.25 -> 0.50 -> 0.75 -> 1.0
        listOf(
            0.00f to "open_000",
            0.25f to "open_025",
            0.50f to "open_050",
            0.75f to "open_075",
            1.00f to "open_100",
            // Tutup (simetris): 0.75 -> 0.50 -> 0.25 -> 0.0
            0.75f to "close_075",
            0.50f to "close_050",
            0.25f to "close_025",
            0.00f to "close_000",
        ).forEach { (p, label) ->
            progressTarget.floatValue = p
            composeRule.waitForIdle()
            val file = File(dir, "$label.png")
            val view = composeRule.activity.findViewById<View>(android.R.id.content)
            val bmp = view.drawToBitmap(Bitmap.Config.ARGB_8888)
            FileOutputStream(file).use { out -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out) }
            assertTrue("Anim frame $label must be valid", file.exists() && file.length() > 1_000L)
        }
    }
}
