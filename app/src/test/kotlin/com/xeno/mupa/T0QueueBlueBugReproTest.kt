package com.xeno.mupa

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Reproduksi T0 untuk bug antrean pencarian "blue":
 * Saat pengguna mencari "blue" dan mengetuk lagu asli ("yung kai - blue"),
 * implementasi saat ini (SearchScreen.kt -> onPlayTrack(uiState.results, index))
 * memasukkan seluruh versi lain ("Slowed + Reverb", "Acoustic", "Live", "Instrumental",
 * "Sped Up", "Official Lyric Video") ke dalam antrean putar, BUKAN [lagu itu] + Radio.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class T0QueueBlueBugReproTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Serializable
    private data class SearchFixture(
        val query: String,
        val results: List<FixtureTrack>,
    )

    @Serializable
    private data class FixtureTrack(
        val id: String,
        val title: String,
        val artistName: String,
        val albumTitle: String? = null,
        val durationMs: Long,
    )

    private val fixtureJson = Json { ignoreUnknownKeys = true }

    private fun loadBlueFixtureTracks(): List<Track> {
        val stream = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("fixtures/search_blue_versions.json"),
        ) { "Fixture fixtures/search_blue_versions.json tidak ditemukan" }
        val raw = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val parsed = fixtureJson.decodeFromString<SearchFixture>(raw)
        return parsed.results.map {
            Track(
                id = it.id,
                title = it.title,
                artistName = it.artistName,
                albumTitle = it.albumTitle,
                durationMs = it.durationMs,
                sourceType = AudioSourceType.STREAMING,
            )
        }
    }

    @Test
    fun t0FixtureLoadsSevenVersionsOfBlue() {
        val tracks = loadBlueFixtureTracks()
        assertEquals(7, tracks.size)
        assertEquals("IpFX2vq8HKw", tracks.first().id)
        assertTrue(tracks.any { it.title.contains("Slowed", ignoreCase = true) })
    }

    @Ignore("T0: reproduksi bug antrean 'blue' terbukti gagal di T0 (AssertionError); akan diaktifkan & diluluskan di T1")
    @Test
    fun t0Repro_tappingBlueInSearchShouldNotQueueOtherVersionsOfBlue() {
        val searchResults = loadBlueFixtureTracks()
        assertEquals(7, searchResults.size)

        var queuedTracks: List<Track>? = null
        var queuedStartIndex: Int = -1

        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                SearchScreen(
                    uiState = SearchUiState(
                        query = "blue",
                        source = AudioSourceType.STREAMING,
                        results = searchResults,
                        hasSearched = true,
                    ),
                    stripArtistName = false,
                    currentPlayingTrackId = null,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    onQueryChange = {},
                    onSelectScope = {},
                    onToggleSource = {},
                    onSubmitQuery = {},
                    onRemoveRecent = {},
                    onPlayTrack = { tracks, idx ->
                        queuedTracks = tracks
                        queuedStartIndex = idx
                    },
                    onRetry = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("blue").onFirst().performClick()
        composeRule.waitForIdle()

        val actualQueue = queuedTracks
        assertNotNull("Mengetuk hasil cari 'blue' harus memulai pemutaran", actualQueue)
        assertEquals("IpFX2vq8HKw", actualQueue!![queuedStartIndex].id)

        // EKSPEKTASI FASE 2T (GAGAL DI T0 SEBAGAI REPRODUKSI BUG):
        // Antrean dari hasil pencarian TIDAK BOLEH berisi sisa hasil pencarian / versi lain dari "blue"
        val upcoming = actualQueue.filterIndexed { i, _ -> i != queuedStartIndex }
        val containsOtherBlueVersions = upcoming.any { t ->
            t.title.contains("slowed", ignoreCase = true) ||
                t.title.contains("acoustic", ignoreCase = true) ||
                t.title.contains("live", ignoreCase = true) ||
                t.title.contains("karaoke", ignoreCase = true) ||
                t.title.contains("sped up", ignoreCase = true)
        }
        assertFalse(
            "BUG T0 TEREPRODUKSI: mengetuk 'blue' mengantrekan ${upcoming.size} versi lain dari 'blue': ${upcoming.map { it.title }}",
            containsOtherBlueVersions,
        )
    }
}
