package com.xeno.mupa

import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.QueueSource
import com.xeno.mupa.core.model.SmartQueueFilter
import com.xeno.mupa.core.model.Track
import com.xeno.mupa.playback.QueuePersistenceStore
import com.xeno.mupa.streaming.innertube.InnerTubeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pengujian T1 (Mesin Antrean Cerdas):
 * 1. SmartQueueFilter: normalisasi judul, tolak semua versi alternatif (live/acoustic/cover/remix/
 *    lyric/instrumental/karaoke/slowed/sped up/reverb/8d/nightcore/ver./official video/mix/nonstop/1 jam),
 *    dedupe (judul+artis), durasi 60s-8m, maks 2 berurutan per artis secara lunak, hindari 20 lagu terakhir.
 * 2. InnerTubeParser.parseNextResponse: parse playlistPanelRenderer, continuation token, automixPlaylistId.
 * 3. QueuePersistenceStore: round-trip QueueSource + continuationToken + radioPlaylistId + recentTrackIds.
 */
class T1SmartQueueAndRadioTest {

    @Test
    fun smartQueueFilterRejectsAlternateVersionsDuplicatesDurationsAndCapsArtistStreaks() {
        val seed = Track("IpFX2vq8HKw", "blue", "yung kai", durationMs = 214_000L)
        val candidates = listOf(
            // Versi lain dari "blue" -> HARUS ditolak
            Track("v1", "blue (Slowed + Reverb)", "yung kai", durationMs = 240_000L),
            Track("v2", "blue - Acoustic Version", "yung kai", durationMs = 200_000L),
            Track("v3", "blue (Live)", "yung kai", durationMs = 220_000L),
            Track("v4", "blue (Instrumental Karaoke)", "Studio", durationMs = 214_000L),
            Track("v5", "blue (Official Lyric Video)", "yung kai", durationMs = 215_000L),
            Track("v6", "Lagu Galau 1 Jam Nonstop", "Compilations", durationMs = 300_000L),
            // Durasi < 60s / > 8m -> HARUS ditolak
            Track("short1", "Interlude", "Tulus", durationMs = 42_000L),
            Track("long1", "Concert Encore", "Hindia", durationMs = 560_000L),
            // Lagu valid dengan 4 lagu artis sama berurutan -> maks 2 berurutan (lunak: digeser, bukan dibuang!)
            Track("g1", "wildflower", "yung kai", durationMs = 198_000L),
            Track("g2", "do you think you could love me?", "yung kai", durationMs = 204_000L),
            Track("g3", "stay with the ocean", "yung kai", durationMs = 210_000L),
            Track("g3_dup", "Stay With The Ocean (Remaster)", "Yung Kai", durationMs = 211_000L),
            Track("g4", "Hati-Hati di Jalan", "Tulus", durationMs = 242_000L),
            Track("g5", "Satu Bulan", "Bernadya", durationMs = 200_000L),
        )

        val result = SmartQueueFilter.filterAndOrderRadioCandidates(
            seed = seed,
            candidates = candidates,
            existingQueue = listOf(seed),
            recentTrackIds = setOf("recent_only"),
        )

        val ids = result.map { it.id }
        // Semua versi alternatif, durasi salah, dan duplikat ternormalisasi tersingkir
        assertFalse(ids.any { it.startsWith("v") || it == "short1" || it == "long1" || it == "g3_dup" })
        // Semua 5 lagu valid tetap ada (artis sama digeser lunak, bukan dibuang!)
        assertEquals(setOf("g1", "g2", "g3", "g4", "g5"), ids.toSet())

        // Cek tidak ada > 2 lagu berurutan dari artis sama (termasuk seed "yung kai")
        val fullOrder = listOf(seed) + result
        for (i in 0..fullOrder.size - 3) {
            val a0 = SmartQueueFilter.normalizeArtist(fullOrder[i].artistName)
            val a1 = SmartQueueFilter.normalizeArtist(fullOrder[i + 1].artistName)
            val a2 = SmartQueueFilter.normalizeArtist(fullOrder[i + 2].artistName)
            assertFalse("Tidak boleh 3 lagu berurutan dari artis sama ($a0) di indeks $i", a0 == a1 && a1 == a2)
        }
    }

    @Test
    fun smartQueueFilterAvoidsLast20PlayedWhenFreshCandidatesAreEnough() {
        val seed = Track("s0", "blue", "yung kai", durationMs = 214_000L)
        val candidates = listOf(
            Track("r1", "Played Recently", "Tulus", durationMs = 200_000L),
            Track("f1", "Fresh One", "Bernadya", durationMs = 200_000L),
            Track("f2", "Fresh Two", "Hindia", durationMs = 200_000L),
            Track("f3", "Fresh Three", "Nadin Amizah", durationMs = 200_000L),
        )
        val filtered = SmartQueueFilter.filterAndOrderRadioCandidates(
            seed = seed,
            candidates = candidates,
            recentTrackIds = setOf("r1"),
        )
        assertEquals(listOf("f1", "f2", "f3"), filtered.map { it.id })
    }

    @Test
    fun innerTubeNextParserExtractsRadioTracksContinuationAndAutomix() {
        val rawNextJson = """
            {
              "contents": {
                "singleColumnMusicWatchNextResultsRenderer": {
                  "tabbedRenderer": {
                    "watchNextTabbedResultsRenderer": {
                      "tabs": [
                        {
                          "tabRenderer": {
                            "content": {
                              "musicQueueRenderer": {
                                "content": {
                                  "playlistPanelRenderer": {
                                    "contents": [
                                      {
                                        "playlistPanelVideoRenderer": {
                                          "videoId": "IpFX2vq8HKw",
                                          "title": { "runs": [{ "text": "blue" }] },
                                          "longBylineText": { "runs": [{ "text": "yung kai" }, { "text": " • " }, { "text": "blue - Single" }] },
                                          "lengthText": { "runs": [{ "text": "3:34" }] },
                                          "thumbnail": { "thumbnails": [{ "url": "https://lh3.googleusercontent.com/abc=w120-h120" }] }
                                        }
                                      },
                                      {
                                        "playlistPanelVideoRenderer": {
                                          "videoId": "dQw4w9WgXcQ",
                                          "title": { "runs": [{ "text": "Here With Me" }] },
                                          "shortBylineText": { "runs": [{ "text": "d4vd" }] },
                                          "lengthText": { "runs": [{ "text": "4:02" }] }
                                        }
                                      },
                                      {
                                        "automixPreviewVideoRenderer": {
                                          "content": {
                                            "automixPlaylistVideoRenderer": {
                                              "navigationEndpoint": {
                                                "watchPlaylistEndpoint": { "playlistId": "RDAMVMIpFX2vq8HKw" }
                                              }
                                            }
                                          }
                                        }
                                      }
                                    ],
                                    "continuations": [
                                      {
                                        "nextRadioContinuationData": { "continuation": "CONT_TOKEN_42" }
                                      }
                                    ]
                                  }
                                }
                              }
                            }
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()

        val parsed = InnerTubeParser.parseNextResponse(rawNextJson).getOrThrow()
        assertEquals(2, parsed.tracks.size)
        assertEquals("IpFX2vq8HKw", parsed.tracks[0].id)
        assertEquals("yung kai", parsed.tracks[0].artistName)
        assertEquals(214_000L, parsed.tracks[0].durationMs)
        assertEquals("CONT_TOKEN_42", parsed.continuationToken)
        assertEquals("RDAMVMIpFX2vq8HKw", parsed.automixPlaylistId)
    }

    @Test
    fun queuePersistenceStoreRoundTripsRadioSourceAndContinuation() {
        val tempFile = File.createTempFile("mupa_queue_t1", ".json").apply { deleteOnExit() }
        val store = QueuePersistenceStore(tempFile)
        val tracks = listOf(
            Track("IpFX2vq8HKw", "blue", "yung kai", durationMs = 214_000L, sourceType = AudioSourceType.STREAMING),
            Track("abc12345678", "Here With Me", "d4vd", durationMs = 242_000L, sourceType = AudioSourceType.STREAMING),
        )
        val source = QueueSource.Radio("IpFX2vq8HKw", "blue", "yung kai")

        store.save(
            tracks = tracks,
            currentIndex = 1,
            positionMs = 45_000L,
            source = source,
            continuationToken = "NEXT_PAGE_TOKEN",
            radioPlaylistId = "RDAMVMIpFX2vq8HKw",
            isShuffleEnabled = true,
            shuffleSeed = 987654321L,
            originalOrderIds = listOf("IpFX2vq8HKw", "abc12345678"),
            recentTrackIds = listOf("IpFX2vq8HKw"),
        )

        val loaded = store.load()
        assertNotNull(loaded)
        assertEquals(source, loaded!!.source.toDomain())
        assertEquals("Radio dari 'blue'", loaded.source.toDomain().displayLabel)
        assertTrue(loaded.isInfinite)
        assertEquals("NEXT_PAGE_TOKEN", loaded.continuationToken)
        assertEquals("RDAMVMIpFX2vq8HKw", loaded.radioPlaylistId)
        assertTrue(loaded.isShuffleEnabled)
        assertEquals(987654321L, loaded.shuffleSeed)
        assertEquals(listOf("IpFX2vq8HKw"), loaded.recentTrackIds)
    }
}
