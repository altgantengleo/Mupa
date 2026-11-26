package com.xeno.mupa.engine.local

import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRecommenderTest {

    private fun item(
        id: String,
        artist: String,
        plays: Int,
        fav: Boolean,
        daysAgoPlayed: Long,
        now: Long,
    ) = LocalTrackItem(
        track = Track(
            id = id,
            title = "Lagu $id",
            artistName = artist,
            durationMs = 210_000L,
            sourceType = AudioSourceType.LOCAL,
            originalFileName = "$artist - Lagu $id.flac",
            isFavorite = fav,
            playCount = plays,
        ),
        contentUri = "content://media/external/audio/media/$id",
        importedAtMs = now - 5L * 86_400_000L,
        lastPlayedAtMs = if (daysAgoPlayed > 0L) now - daysAgoPlayed * 86_400_000L else 0L,
    )

    @Test
    fun mixMupaRespectsMmrArtistCapAndExplorationSlot() {
        val now = 1_750_000_000_000L
        val items = listOf(
            item("1", "Hindia", 20, true, 16, now),
            item("2", "Hindia", 19, true, 15, now),
            item("3", "Hindia", 18, true, 14, now),
            item("4", "Nadin Amizah", 12, true, 20, now),
            item("5", "Tulus", 0, false, 0, now),
            item("6", "Reality Club", 10, false, 18, now),
        )

        val mix = MixMupaLocalRecommender.buildMixMupa(items, nowMs = now, limit = 6)
        assertEquals(6, mix.size)
        val hindiaTop5 = mix.take(5).count { it.track.artistName == "Hindia" }
        assertTrue("Expected <=2 tracks by same artist in top 5, got $hindiaTop5", hindiaTop5 <= 2)
        assertEquals("5", mix[2].track.id)
    }

    @Test
    fun repeatAgainUnplayedAndLocalArtistsWorkTogether() {
        val now = 1_750_000_000_000L
        val items = listOf(
            item("old", "Hindia", 6, true, 14, now),
            item("fresh", "Nadin Amizah", 10, true, 2, now),
            item("zero", "Tulus", 0, false, 0, now),
        )

        val repeat = MixMupaLocalRecommender.buildRepeatAgain(items, nowMs = now)
        assertEquals(listOf("old"), repeat.map { it.id })

        val unplayed = MixMupaLocalRecommender.buildUnplayed(items)
        assertEquals(listOf("zero"), unplayed.map { it.id })

        val artists = MixMupaLocalRecommender.aggregateLocalArtists(items)
        assertEquals(3, artists.size)
        assertEquals("Nadin Amizah", artists.first().name)
    }
}
