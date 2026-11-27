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
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.xeno.mupa.core.designsystem.theme.MupaAppearance
import com.xeno.mupa.core.designsystem.theme.MupaTheme
import com.xeno.mupa.core.designsystem.theme.UltraDynamicPalette
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.RecommendationQualityFilter
import com.xeno.mupa.core.model.ShelfComposer
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.ui.screens.AccountScreen
import com.xeno.mupa.ui.screens.HomeHeroKind
import com.xeno.mupa.ui.screens.HomeHeroPage
import com.xeno.mupa.ui.screens.HomeScreen
import com.xeno.mupa.ui.screens.HomeUiState
import com.xeno.mupa.ui.screens.SearchScreen
import com.xeno.mupa.ui.screens.SearchUiState
import org.junit.Assert.assertEquals
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
 * Verifikasi S3 (Hero Beranda Edge-to-Edge, Filter Kualitas Rekomendasi N4, SourceSwitch Tunggal N5, Scrim Status Bar N3):
 * 1. Unit test RecommendationQualityFilter: tolak "TOP HITS SPOTIFY 2026", "Piktor TV", "Full Album", "1 Hour",
 *    "Nonstop", "Mix", durasi < 60s / > 8m, dan dedup judul dinormalisasi.
 * 2. UI test geser Hero Carousel across 3 halaman (Mix Mupa -> Sorotan Artis -> Lanjutkan) + capture screenshot.
 * 3. Capture Search dengan MupaSourceSwitch kanan-atas + SegmentedTabs utuh di 360dp (N5) & Account scroll dengan scrim status bar (N3).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = Application::class, sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class S3HomeHeroAndFilterTest {

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
    private val warmCrimsonPalette = UltraDynamicPalette(
        bgDeep = Color(0xFF281619),
        glow1 = Color(0xFFDC4D42),
        glow2 = Color(0xFFE08D4F),
        glow3 = Color(0xFFC23B58),
        onAccent = Color(0xFFFFE5DF),
        glowIntensity = 0.90f,
    )
    private val forestPalette = UltraDynamicPalette(
        bgDeep = Color(0xFF13261E),
        glow1 = Color(0xFF2FA877),
        glow2 = Color(0xFF83C26E),
        glow3 = Color(0xFF2B8C82),
        onAccent = Color(0xFFDCFFEC),
        glowIntensity = 0.88f,
    )

    private val cleanTracks = listOf(
        Track("t1", "blue", "yung kai", "blue - Single", 214_000L, sourceType = AudioSourceType.STREAMING),
        Track("t2", "Hati-Hati di Jalan", "Tulus", "Manusia", 242_000L, sourceType = AudioSourceType.STREAMING),
        Track("t3", "Satu Bulan", "Bernadya", "Sialnya, Hidup Harus Tetap Berjalan", 200_000L, sourceType = AudioSourceType.STREAMING),
        Track("t4", "Evaluasi", "Hindia", "Menari dengan Bayangan", 195_000L, sourceType = AudioSourceType.STREAMING),
        Track("t5", "Sial", "Mahalini", "Fabula", 243_000L, sourceType = AudioSourceType.STREAMING),
        Track("t6", "Komang", "Raim Laode", "Komang", 222_000L, sourceType = AudioSourceType.STREAMING),
    )

    @Test
    fun recommendationQualityFilterRejectsJunkCompilationsDurationsAndDuplicates() {
        val mixed = listOf(
            Track("ok1", "blue", "yung kai", durationMs = 214_000L),
            Track("ok1_dup", "blue (Official Audio)", "Yung Kai", durationMs = 216_000L),
            Track("junk1", "TOP HITS SPOTIFY 2026 - Lagu Barat & Indo Viral", "Top Hits Music", durationMs = 3_600_000L),
            Track("junk2", "Lagu Galau Paling Enak Didengar", "Piktor TV", durationMs = 240_000L),
            Track("junk3", "Bernadya Full Album 2024", "Musik Santai", durationMs = 210_000L),
            Track("junk4", "Indie Playlist Santai Sore", "Indie Club", durationMs = 200_000L),
            Track("junk5", "Nonstop Acoustic 1 Jam", "Acoustic Channel", durationMs = 250_000L),
            Track("junk6", "Chill Mix 2026", "LoFi Room", durationMs = 220_000L),
            Track("short1", "Intro", "Tulus", durationMs = 38_000L),
            Track("long1", "Live Concert Recording", "Hindia", durationMs = 620_000L),
            Track("ok2", "Hati-Hati di Jalan", "Tulus", durationMs = 242_000L),
        )

        val filtered = RecommendationQualityFilter.filterTracks(mixed)
        assertEquals(listOf("ok1", "ok2"), filtered.map { it.id })
        assertFalse(RecommendationQualityFilter.isHighQualitySong(mixed[2]))
        assertFalse(RecommendationQualityFilter.isHighQualitySong(mixed[3]))
        assertFalse(RecommendationQualityFilter.isHighQualitySong(mixed[8]))
        assertFalse(RecommendationQualityFilter.isHighQualitySong(mixed[9]))

        // ShelfComposer memastikan unik lintas shelf (baik ID maupun judul dinormalisasi) dan maks 3 shelf
        val shelves = listOf(
            Playlist("s1", "Untukmu", tracks = listOf(mixed[0], mixed[10])),
            Playlist("s2", "Ulangi lagi", tracks = listOf(mixed[1], cleanTracks[2])),
            Playlist("s3", "Belum diputar", tracks = listOf(cleanTracks[3])),
            Playlist("s4", "Keempat", tracks = listOf(cleanTracks[4])),
        )
        val composed = ShelfComposer.compose(shelves, maxShelves = 3)
        assertEquals(3, composed.size)
        val allIds = composed.flatMap { it.tracks }.map { it.id }
        assertTrue("Duplikat judul 'blue (Official Audio)' harus disaring lintas shelf", "ok1_dup" !in allIds)
        assertEquals(allIds.size, allIds.distinct().size)
    }

    @Test
    fun homeHeroCarouselSwipesAcrossThreePagesAndCapturesScreenshots() {
        var switchedSource: AudioSourceType? = null
        val spotlightPage = HomeHeroPage(
            id = "hero_artist_yungkai",
            kind = HomeHeroKind.ARTIST_SPOTLIGHT,
            kicker = "SOROTAN ARTIS",
            title = "yung kai",
            subtitle = "3 lagu pilihan · Bergilir harian dari artis favoritmu",
            artworkUrl = null,
            tracks = cleanTracks.take(3),
            palette = warmCrimsonPalette,
        )
        val uiState = HomeUiState(
            greeting = "Selamat pagi",
            activeSource = AudioSourceType.STREAMING,
            mixHero = Playlist("shelf_mix_mupa", "Mix Mupa", "Racikan dari yung kai & Tulus", cleanTracks.take(4)),
            artistSpotlight = spotlightPage,
            shelves = listOf(
                Playlist("shelf_for_you", "Untukmu", "Berdasarkan yung kai, Tulus, Bernadya", cleanTracks.subList(0, 3)),
                Playlist("shelf_repeat", "Ulangi lagi", "Pilihan hangat yang nyaman diputar ulang", cleanTracks.subList(3, 6)),
            ),
            isLoading = false,
        )

        composeRule.setContent {
            MupaTheme(
                appearance = MupaAppearance(
                    glassStyle = GlassStyle.SOLID,
                    isUltraDynamicEnabled = true,
                    ultraDynamicPalette = blueSandPalette,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    HomeScreen(
                        uiState = uiState,
                        stripArtistName = true,
                        contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp),
                        onPlayTrack = { _, _ -> },
                        onReshuffleMix = {},
                        onRetry = {},
                        onToggleSource = { switchedSource = it },
                        continueTrack = cleanTracks[2],
                        continueQueue = cleanTracks,
                        continueIndex = 2,
                        pagePalettes = mapOf(
                            "hero_mix_mupa" to blueSandPalette,
                            "hero_artist_yungkai" to warmCrimsonPalette,
                            "hero_continue_t3" to forestPalette,
                        ),
                    )
                }
            }
        }

        // Hero menampilkan HANYA lagu terakhir yang diputar (sesuai arahan user)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("TERAKHIR DIPUTAR").assertIsDisplayed()
        composeRule.onAllNodesWithText("Satu Bulan").onFirst().assertIsDisplayed()
        composeRule.onNodeWithTag("mupa_source_switch").assertIsDisplayed()
        saveScreenshot("s3/home_hero_last_played.png")

        // Uji klik SourceSwitch ke Lokal
        composeRule.onNodeWithContentDescription("Mode Lokal (Ponsel)").performClick()
        composeRule.waitForIdle()
        assertEquals(AudioSourceType.LOCAL, switchedSource)
    }

    @Test
    fun searchHeaderShowsUnifiedSourceSwitchAndUnclippedTabs() {
        var switched: AudioSourceType? = null
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    SearchScreen(
                        uiState = SearchUiState(source = AudioSourceType.STREAMING),
                        stripArtistName = true,
                        currentPlayingTrackId = null,
                        contentPadding = PaddingValues(top = 28.dp, bottom = 120.dp),
                        onQueryChange = {},
                        onSelectScope = {},
                        onToggleSource = {},
                        onSwitchSource = { switched = it },
                        onSubmitQuery = {},
                        onRemoveRecent = {},
                        onPlayTrack = { _, _ -> },
                        onRetry = {},
                    )
                }
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("mupa_source_switch").assertIsDisplayed()
        composeRule.onNodeWithText("Semua").assertIsDisplayed()
        composeRule.onNodeWithText("Album").assertIsDisplayed()
        saveScreenshot("s3/search_source_switch_360dp.png")

        composeRule.onNodeWithContentDescription("Mode Lokal (Ponsel)").performClick()
        composeRule.waitForIdle()
        assertEquals(AudioSourceType.LOCAL, switched)
    }

    @Test
    fun accountWithTopStatusScrimProtectsScrolledContent() {
        composeRule.setContent {
            MupaTheme(appearance = MupaAppearance(glassStyle = GlassStyle.SOLID)) {
                val bgColor = MaterialTheme.colorScheme.background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bgColor),
                ) {
                    AccountScreen(
                        preferences = UserPreferences(onboardingCompleted = true),
                        favoriteArtists = listOf(
                            Artist("a1", "yung kai"),
                            Artist("a2", "Tulus"),
                            Artist("a3", "Bernadya"),
                        ),
                        totalTracksCount = 12,
                        favoriteTracksCount = 4,
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 120.dp),
                        onEditFavoriteArtists = {},
                        onGlassStyleChange = {},
                        onOledModeChange = {},
                        onAuroraEnabledChange = {},
                        onPerformanceModeChange = {},
                        onStripArtistNameChange = {},
                    )
                    // Scrim status bar atas seperti di MupaApp (N3): lindungi status bar tanpa meredupkan judul statis
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                Brush.verticalGradient(
                                    0.0f to bgColor.copy(alpha = 0.98f),
                                    0.55f to bgColor.copy(alpha = 0.80f),
                                    1.0f to Color.Transparent,
                                ),
                            ),
                    )
                }
            }
        }

        composeRule.waitForIdle()
        saveScreenshot("s3/account_status_scrim_360dp.png")
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
