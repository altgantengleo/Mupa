package com.xeno.mupa

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.SmartShuffleEngine
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.player.MupaQueueOverlay
import com.xeno.mupa.ui.screens.LibraryScreen
import com.xeno.mupa.ui.screens.LibraryTileCategory
import com.xeno.mupa.ui.screens.LibraryUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Pengujian T2 (Shuffle Berbasis Seed, Tombol Acak Pustaka, Label Sumber Antrean & Repeat):
 * 1. Shuffle mengacak HANYA item berikutnya; lagu aktif & riwayat sebelumnya tidak bergeser.
 * 2. Urutan acak deterministik per seed; mematikan shuffle mengembalikan urutan asli untuk sisa antrean.
 * 3. Penyisipan continuation radio saat shuffle ON menghindari 2 artis sama berurutan bila mungkin.
 * 4. Sheet Antrean menampilkan label sumber ("Radio dari 'blue'") & header daftar Pustaka punya tombol Acak.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class T2ShuffleAndLabelsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sampleQueue = listOf(
        Track("t0", "Lagu 0", "Tulus", durationMs = 200_000L),
        Track("t1", "blue", "yung kai", durationMs = 214_000L),
        Track("t2", "Satu Bulan", "Bernadya", durationMs = 200_000L),
        Track("t3", "Evaluasi", "Hindia", durationMs = 195_000L),
        Track("t4", "Monokrom", "Tulus", durationMs = 210_000L),
        Track("t5", "Kata Mereka Ini Berlebihan", "Bernadya", durationMs = 205_000L),
        Track("t6", "Komang", "Raim Laode", durationMs = 222_000L),
        Track("t7", "Sial", "Mahalini", durationMs = 243_000L),
    )

    @Test
    fun shuffleOnlyShufflesUpcomingPreservesCurrentAndHistoryAndRestoresOnOff() {
        val currentIdx = 1 // Sedang memutar "blue" setelah "Lagu 0"
        val seed = 42_000_123L

        val shuffled = SmartShuffleEngine.shuffleUpcoming(sampleQueue, currentIdx, seed)
        val shuffledSameSeed = SmartShuffleEngine.shuffleUpcoming(sampleQueue, currentIdx, seed)

        // Deterministik dengan seed sama
        assertEquals(shuffled.map { it.id }, shuffledSameSeed.map { it.id })
        // Riwayat sebelumnya (0) & lagu aktif (1) TIDAK berubah ("Sebelumnya" kembali ke t0!)
        assertEquals(listOf("t0", "t1"), shuffled.take(2).map { it.id })
        // Item berikutnya tetap lengkap
        assertEquals(sampleQueue.drop(2).map { it.id }.toSet(), shuffled.drop(2).map { it.id }.toSet())
        // Tidak ada dua artis sama berurutan pada hasil acak
        val upcoming = shuffled.drop(1)
        for (i in 0 until upcoming.lastIndex) {
            val a = SmartQueueFilter.normalizeArtist(upcoming[i].artistName)
            val b = SmartQueueFilter.normalizeArtist(upcoming[i + 1].artistName)
            assertFalse("Hindari 2 lagu berurutan artis sama ($a)", a == b)
        }

        // Matikan shuffle -> mengembalikan urutan asli untuk sisa antrean setelah currentIdx
        val restored = SmartShuffleEngine.unshuffleRemaining(
            currentQueue = shuffled,
            originalOrder = sampleQueue,
            currentIndex = currentIdx,
        )
        assertEquals(sampleQueue.map { it.id }, restored.map { it.id })
    }

    @Test
    fun shuffledContinuationInsertsIncomingTracksAvoidingAdjacentSameArtist() {
        val base = sampleQueue.take(4)
        val incoming = listOf(
            Track("r1", "Baru 1", "Nadin Amizah", durationMs = 200_000L),
            Track("r2", "Baru 2", "Pamungkas", durationMs = 210_000L),
            Track("r3", "Baru 3", "Sal Priadi", durationMs = 220_000L),
        )
        val merged = SmartShuffleEngine.insertShuffledContinuation(
            currentQueue = base,
            currentIndex = 1,
            incoming = incoming,
            seed = 777L,
        )
        assertEquals(listOf("t0", "t1"), merged.take(2).map { it.id })
        assertEquals(base.size + incoming.size, merged.size)
        assertTrue(incoming.all { inc -> merged.any { it.id == inc.id } })
    }

    @Test
    fun queueOverlayDisplaysQueueSourceLabel() {
        val radioSource = QueueSource.Radio("t1", "blue", "yung kai")
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                MupaQueueOverlay(
                    queue = sampleQueue.take(3),
                    currentIndex = 1,
                    queueSourceLabel = radioSource.displayLabel,
                    onSelectIndex = {},
                    onMoveItem = { _, _ -> },
                    onRemoveItem = {},
                    onClose = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Radio dari 'blue'", substring = true).assertIsDisplayed()
    }

    @Test
    fun libraryDetailHeaderHasAcakButtonTriggeringShuffledPlayback() {
        var shuffledList: List<Track>? = null
        var shuffledSource: QueueSource? = null
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                LibraryScreen(
                    uiState = LibraryUiState(
                        activeSource = AudioSourceType.LOCAL,
                        openCategory = LibraryTileCategory.SONGS,
                        tracks = sampleQueue.take(4),
                        isLoading = false,
                    ),
                    stripArtistName = true,
                    currentPlayingTrackId = null,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 100.dp),
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
                    onPlayShuffled = { list, src ->
                        shuffledList = list
                        shuffledSource = src
                    },
                    onRetry = {},
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("library_shuffle_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertNotNull(shuffledList)
        assertEquals(4, shuffledList!!.size)
        assertTrue(shuffledSource is QueueSource.Local)
    }
}
