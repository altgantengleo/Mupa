package com.xeno.mupa

import androidx.lifecycle.SavedStateHandle
import com.xeno.mupa.core.domain.PreferencesRepository
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.data.fake.FakeArtistRepository
import com.xeno.mupa.data.fake.FakeMusicSource
import com.xeno.mupa.data.fake.TrackNameFormatter
import com.xeno.mupa.ui.onboarding.ArtistOnboardingUiState
import com.xeno.mupa.ui.onboarding.ArtistOnboardingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MupaOnboardingAndFakeRepoTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun onboardingRequiresAtLeastTwoArtistsAndCapsAtEight() = runTest(testDispatcher) {
        val artistRepo = FakeArtistRepository()
        val prefsRepo = InMemoryPreferencesRepository()
        val handle = SavedStateHandle()
        val vm = ArtistOnboardingViewModel(artistRepo, prefsRepo, handle)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.canContinue)

        vm.toggleArtist("art-hindia")
        assertFalse(vm.uiState.value.canContinue)
        assertEquals(1, vm.uiState.value.selectedCount)

        vm.toggleArtist("art-nadin")
        assertTrue(vm.uiState.value.canContinue)
        assertEquals(2, vm.uiState.value.selectedCount)

        // Try adding 8 more -> must cap strictly at 8
        listOf("art-tulus", "art-reality", "art-feast", "art-sal", "art-bernadya", "art-pamungkas", "art-niki", "art-joji")
            .forEach { vm.toggleArtist(it) }
        assertEquals(ArtistOnboardingUiState.MAX_ARTISTS, vm.uiState.value.selectedCount)
        assertTrue(vm.uiState.value.reachedLimit)

        // Verify selection survives process death via SavedStateHandle
        val restoredVm = ArtistOnboardingViewModel(artistRepo, prefsRepo, handle)
        advanceUntilIdle()
        assertEquals(ArtistOnboardingUiState.MAX_ARTISTS, restoredVm.uiState.value.selectedCount)
    }

    @Test
    fun fakeMusicSourceSeedsForYouFromSelectedArtistsAndBuildsMixMupa() = runTest(testDispatcher) {
        val artistRepo = FakeArtistRepository()
        val source = FakeMusicSource(artistRepo)

        val shelves = source.getHomeRecommendations(setOf("art-hindia", "art-tulus")).getOrThrow()
        val forYou = shelves.first { it.id == "shelf_for_you" }
        val mixMupa = shelves.first { it.id == "shelf_mix_mupa" }
        val unplayed = shelves.first { it.id == "shelf_unplayed" }

        assertTrue(forYou.subtitle.orEmpty().contains("Hindia"))
        assertTrue(forYou.tracks.any { it.artistName == "Hindia" })
        assertEquals("Mix Mupa", mixMupa.title)
        assertTrue(unplayed.tracks.all { it.playCount == 0 })
    }

    @Test
    fun stripArtistNameIsNonDestructiveAndRecoversFromOriginalFileName() {
        val track = Track(
            id = "loc-1",
            title = "Bertaut",
            artistName = "Unknown",
            originalFileName = "Nadin Amizah - Bertaut (Official Audio).flac",
        )

        val stripped = TrackNameFormatter.format(track, stripArtistName = true)
        assertEquals("Bertaut", stripped.title)
        assertEquals("", stripped.artist)

        val restored = TrackNameFormatter.format(track, stripArtistName = false)
        assertEquals("Bertaut", restored.title)
        assertEquals("Nadin Amizah", restored.artist)
    }

    private class InMemoryPreferencesRepository : PreferencesRepository {
        private val state = MutableStateFlow(UserPreferences())
        override val userPreferences: Flow<UserPreferences> = state
        override suspend fun setOnboardingCompleted(artistIds: Set<String>) {
            state.value = state.value.copy(onboardingCompleted = true, favoriteArtistIds = artistIds)
        }
        override suspend fun updateFavoriteArtists(artistIds: Set<String>) {
            state.value = state.value.copy(favoriteArtistIds = artistIds)
        }
        override suspend fun setGlassStyle(style: GlassStyle) {
            state.value = state.value.copy(glassStyle = style)
        }
        override suspend fun setOledMode(enabled: Boolean) {
            state.value = state.value.copy(isOledMode = enabled)
        }
        override suspend fun setAuroraEnabled(enabled: Boolean) {
            state.value = state.value.copy(isAuroraEnabled = enabled)
        }
        override suspend fun setPerformanceMode(enabled: Boolean) {
            state.value = state.value.copy(isPerformanceMode = enabled)
        }
        override suspend fun setStripArtistName(enabled: Boolean) {
            state.value = state.value.copy(stripArtistName = enabled)
        }
    }
}
