package com.xeno.mupa

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.core.model.Artist
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.streaming.aggregator.CrossProviderTrackMatcher
import com.xeno.mupa.streaming.aggregator.MultiProviderCatalogAggregator
import com.xeno.mupa.streaming.aggregator.ProviderRegistry
import com.xeno.mupa.streaming.aggregator.SwrCache
import com.xeno.mupa.streaming.aggregator.providers.DeezerCatalogProvider
import com.xeno.mupa.streaming.aggregator.providers.ItunesCatalogProvider
import com.xeno.mupa.streaming.api.CatalogProvider
import com.xeno.mupa.streaming.api.MupaStreamingDiagnostics
import com.xeno.mupa.streaming.api.StreamLookupRequest
import com.xeno.mupa.streaming.api.StreamingAudioProvider
import com.xeno.mupa.streaming.api.StreamingError
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pengujian T3 (Multi-Provider: Deezer + iTunes + InnerTube, Cross-Provider Matcher,
 * Parallel Progressive Merge, SWR Cache, Stream Hedging 800ms, dan Diagnostik Debug).
 */
class T3MultiProviderTest {

    @Test
    fun deezerAndItunesParsersExtractCleanHdMetadata() {
        val dzTracksJson = """
            {
              "data": [
                {
                  "id": 3135556,
                  "title": "blue",
                  "duration": 214,
                  "artist": { "id": 99, "name": "yung kai" },
                  "album": { "title": "blue - Single", "cover_xl": "https://cdn-images.dzcdn.net/cover_xl.jpg" }
                }
              ]
            }
        """.trimIndent()
        val dzTracks = DeezerCatalogProvider.parseTracksResponse(dzTracksJson).getOrThrow()
        assertEquals(1, dzTracks.size)
        assertEquals("dz_3135556", dzTracks[0].id)
        assertEquals(214_000L, dzTracks[0].durationMs)
        assertEquals("https://cdn-images.dzcdn.net/cover_xl.jpg", dzTracks[0].artworkUrl)

        val dzArtistsJson = """
            {
              "data": [
                { "id": 123, "name": "Tommy J. Pisa", "picture_big": "https://cdn-images.dzcdn.net/tommy_big.jpg" }
              ]
            }
        """.trimIndent()
        val dzArtists = DeezerCatalogProvider.parseArtistsResponse(dzArtistsJson).getOrThrow()
        assertEquals("Tommy J. Pisa", dzArtists[0].name)
        assertEquals("https://cdn-images.dzcdn.net/tommy_big.jpg", dzArtists[0].artworkUrl)

        val itJson = """
            {
              "resultCount": 1,
              "results": [
                {
                  "trackId": 177001,
                  "trackName": "blue",
                  "artistName": "yung kai",
                  "collectionName": "blue - Single",
                  "trackTimeMillis": 214357,
                  "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music/100x100bb.jpg"
                }
              ]
            }
        """.trimIndent()
        val itTracks = ItunesCatalogProvider.parseTracksResponse(itJson).getOrThrow()
        assertEquals(1, itTracks.size)
        assertEquals("it_177001", itTracks[0].id)
        assertEquals(214_357L, itTracks[0].durationMs)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/600x600bb.jpg", itTracks[0].artworkUrl)
    }

    @Test
    fun crossProviderMatcherScoresExactMatchAndRejectsWrongOrAlternateTracks() {
        CrossProviderTrackMatcher.clearCache()
        val candidates = listOf(
            Track("wrong_ver", "blue (Slowed + Reverb)", "yung kai", durationMs = 248_000L),
            Track("wrong_song", "Blue Sky", "Random Band", durationMs = 214_000L),
            Track("IpFX2vq8HKw", "blue", "yung kai", durationMs = 215_000L),
        )
        val best = CrossProviderTrackMatcher.findBestMatch(
            targetTrackId = "dz_3135556",
            targetTitle = "blue",
            targetArtist = "yung kai",
            targetDurationMs = 214_000L,
            candidates = candidates,
        )
        assertNotNull(best)
        assertEquals("IpFX2vq8HKw", best!!.candidate.id)
        assertTrue(best.score >= CrossProviderTrackMatcher.MIN_MATCH_SCORE)
        assertEquals("IpFX2vq8HKw", CrossProviderTrackMatcher.getCachedStreamId("dz_3135556"))

        // Bila hanya ada kandidat salah -> HARUS null (jangan memutar lagu yang salah!)
        val noMatch = CrossProviderTrackMatcher.findBestMatch(
            targetTrackId = "dz_missing",
            targetTitle = "Hati-Hati di Jalan",
            targetArtist = "Tulus",
            targetDurationMs = 242_000L,
            candidates = listOf(Track("bad1", "Completely Different Song", "Other", durationMs = 180_000L)),
        )
        assertNull(noMatch)
    }

    @Test
    fun parallelCatalogAggregatorEmitsFastestFirstAndMergesDedupedWithPhotos() = runTest {
        val fastDeezer = object : CatalogProvider {
            override val providerId = "deezer"
            override val catalogPriority = 10
            override suspend fun searchTracks(query: String, limit: Int) = Result.success(
                listOf(Track("dz_1", "blue", "yung kai", durationMs = 214_000L, artworkUrl = "https://dz/art.jpg")),
            )
            override suspend fun searchArtists(query: String, limit: Int) = Result.success(
                listOf(Artist("dz_a1", "Tulus", artworkUrl = "https://dz/tulus.jpg")),
            )
            override suspend fun getArtistTopTracks(artistId: String, artistName: String) = Result.success(emptyList<Track>())
            override suspend fun getTrendingArtists(limit: Int) = Result.success(
                listOf(Artist("dz_a1", "Tulus", artworkUrl = "https://dz/tulus.jpg")),
            )
        }
        val slowerYt = object : CatalogProvider {
            override val providerId = "innertube"
            override val catalogPriority = 30
            override suspend fun searchTracks(query: String, limit: Int): Result<List<Track>> {
                delay(120)
                return Result.success(
                    listOf(
                        Track("IpFX2vq8HKw", "blue", "yung kai", durationMs = 214_000L, artworkUrl = "https://yt/art.jpg"),
                        Track("yt_extra123", "wildflower", "yung kai", durationMs = 198_000L, artworkUrl = "https://yt/wf.jpg"),
                    ),
                )
            }
            override suspend fun searchArtists(query: String, limit: Int): Result<List<Artist>> {
                delay(120)
                return Result.success(
                    listOf(
                        Artist("yt_a1", "Tulus", artworkUrl = null), // duplikat tanpa foto -> harus kalah dari Deezer
                        Artist("yt_a2", "Bernadya", artworkUrl = "https://yt/bernadya.jpg"),
                    ),
                )
            }
            override suspend fun getArtistTopTracks(artistId: String, artistName: String) = Result.success(emptyList<Track>())
            override suspend fun getTrendingArtists(limit: Int) = searchArtists("", limit)
        }

        val aggregator = MultiProviderCatalogAggregator(listOf(fastDeezer, slowerYt))
        val trackEmissions = aggregator.searchTracksProgressive("blue", limit = 10).toList()
        assertTrue("Harus memancarkan hasil progresif", trackEmissions.size >= 2)
        // Hasil pertama dari Deezer langsung tampil
        assertEquals(1, trackEmissions.first().size)
        // Hasil akhir digabung + dedupe (blue 1x dengan ID langsung YT, ditambah wildflower)
        val finalTracks = trackEmissions.last()
        assertEquals(2, finalTracks.size)
        assertEquals("IpFX2vq8HKw", finalTracks.first { it.title == "blue" }.id)

        // Onboarding artis paralel: Tulus tetap punya foto HD dari Deezer, tidak duplikat
        val artists = aggregator.getArtistsMerged("", limit = 10)
        assertEquals(2, artists.size)
        assertEquals("https://dz/tulus.jpg", artists.first { it.name == "Tulus" }.artworkUrl)
    }

    @Test
    fun swrCacheAndHedgedStreamRegistryWorkAndRecordDiagnostics() = runTest {
        var clock = 1_000L
        val swr = SwrCache<String, String>(ttlMs = 5_000L, clockMs = { clock })
        swr.put("home", "v1")
        assertFalse(swr.get("home")!!.isStale)
        clock += 6_000L
        assertTrue(swr.get("home")!!.isStale)

        MupaStreamingDiagnostics.clear()
        val slowPrimary = object : StreamingAudioProvider {
            override val providerId = "primary_slow"
            override val priority = 10
            override val supportsDirectTrackId = true
            override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> {
                delay(2_500L)
                return Result.success(ResolvedStream("https://primary/audio.m4a", "audio/mp4"))
            }
        }
        val fastHedge = object : StreamingAudioProvider {
            override val providerId = "secondary_fast"
            override val priority = 20
            override val supportsDirectTrackId = false
            override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> {
                delay(50L)
                return Result.success(ResolvedStream("https://secondary/audio.m4a", "audio/mp4"))
            }
        }

        val registry = ProviderRegistry(
            providers = listOf(slowPrimary, fastHedge),
            hedgeDelayMs = 200L,
        )
        val outcome = registry.resolve(
            StreamLookupRequest("dz_1", "blue", "yung kai", 214_000L),
        ).getOrThrow()

        assertEquals("secondary_fast", outcome.providerId)
        assertTrue(MupaStreamingDiagnostics.snapshot().any { it.kind == "time_to_audio" && it.providerId == "secondary_fast" })
    }
}
