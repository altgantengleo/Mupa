package com.xeno.mupa

import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.Playlist
import com.xeno.mupa.core.model.ShelfComposer
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.data.local.LocalImportRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class R1FoundationAndLocalRulesTest {

    @Test
    fun durationFormatterHidesZeroAndSupportsOneHourPlus() {
        assertNull(TrackDisplayRules.formatDuration(0L))
        assertNull(TrackDisplayRules.formatDuration(-500L))
        assertEquals("0:09", TrackDisplayRules.formatDuration(9_200L))
        assertEquals("3:34", TrackDisplayRules.formatDuration(214_000L))
        assertEquals("1:05:09", TrackDisplayRules.formatDuration(3_909_000L))
    }

    @Test
    fun trackDisplayNeverLeavesSecondLineBlankAndStripsTitlePrefixNonDestructively() {
        val localWithClutter = Track(
            id = "content://audio/1",
            title = "Nadin Amizah - Bertaut (Official Audio)",
            artistName = "",
            originalFileName = "Nadin Amizah - Bertaut (Official Audio).flac",
            durationMs = 256_000L,
            sourceType = AudioSourceType.LOCAL,
        )
        val formattedLocal = TrackDisplayRules.formatTrack(localWithClutter, stripArtistName = true)
        assertEquals("Bertaut", formattedLocal.title)
        assertEquals("Nadin Amizah", formattedLocal.subtitle)
        assertEquals("4:16", formattedLocal.durationText)

        val streamingUnknownMeta = Track(
            id = "vid12345678",
            title = "Mystery Track",
            artistName = "Unknown",
            albumTitle = null,
            durationMs = 0L,
            sourceType = AudioSourceType.STREAMING,
        )
        val formattedStreaming = TrackDisplayRules.formatTrack(streamingUnknownMeta, stripArtistName = true)
        assertEquals("Streaming", formattedStreaming.subtitle)
        assertNull(formattedStreaming.durationText)

        val albumFallback = streamingUnknownMeta.copy(albumTitle = "Midnight EP", sourceType = AudioSourceType.LOCAL)
        assertEquals("Midnight EP", TrackDisplayRules.formatTrack(albumFallback, stripArtistName = true).subtitle)
    }

    @Test
    fun shelfComposerDeduplicatesAcrossShelvesAndCapsAtTenPerShelf() {
        val shared = (1..15).map { i ->
            Track(id = "t$i", title = "Song $i", artistName = "Artist $i")
        }
        val shelves = listOf(
            Playlist("s1", "Untukmu", tracks = shared.take(12)),
            Playlist("s2", "Ulangi lagi", tracks = shared.take(15)),
            Playlist("s3", "Belum diputar", tracks = shared.take(15)),
        )
        val composed = ShelfComposer.compose(shelves, maxPerShelf = 10, maxShelves = 3)
        val allIds = composed.flatMap { it.tracks }.map { it.id }
        assertEquals("No duplicate track IDs allowed across screen", allIds.size, allIds.toSet().size)
        assertTrue(composed.all { it.tracks.size <= 10 })
        assertEquals(10, composed[0].tracks.size)
        assertEquals(5, composed[1].tracks.size)
    }

    @Test
    fun localImportRulesDetectSupportedExtensionsFallbackTitleAndDuplicates() {
        assertTrue(LocalImportRules.isSupportedAudioFileName("track.FLAC"))
        assertTrue(LocalImportRules.isSupportedAudioFileName("song.opus"))
        assertFalse(LocalImportRules.isSupportedAudioFileName("cover.jpg"))
        assertEquals("Hindia - Evaluasi", LocalImportRules.fallbackTitleFromFileName("Hindia - Evaluasi.mp3"))

        val fp = LocalImportRules.fingerprintKey("Evaluasi", 214_500L, 8_000_000L)
        assertTrue(
            LocalImportRules.isDuplicate(
                candidateUri = "content://new/2",
                candidateTitle = "Evaluasi!",
                candidateDurationMs = 214_100L,
                candidateFileSize = 8_000_000L,
                existingUris = setOf("content://old/1"),
                existingFingerprints = setOf(fp),
            ),
        )
        assertFalse(
            LocalImportRules.isDuplicate(
                candidateUri = "content://new/3",
                candidateTitle = "Evaluasi",
                candidateDurationMs = 214_100L,
                candidateFileSize = 9_500_000L,
                existingUris = setOf("content://old/1"),
                existingFingerprints = setOf(fp),
            ),
        )
    }

    @Test
    fun defaultGlassStyleIsSolidForCalmNonBleedingChrome() {
        assertEquals(GlassStyle.SOLID, UserPreferences().glassStyle)
        assertEquals(AudioSourceType.STREAMING, UserPreferences().activeSource)
    }
}
