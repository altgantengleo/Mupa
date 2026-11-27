package com.xeno.mupa

import androidx.compose.ui.graphics.Color
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.data.local.db.LyricsCacheDao
import com.xeno.mupa.data.local.db.LyricsCacheEntity
import com.xeno.mupa.data.lyrics.EmbeddedAudioLyricsReader
import com.xeno.mupa.data.lyrics.LrcLibLyricsProvider
import com.xeno.mupa.data.lyrics.LrcParser
import com.xeno.mupa.data.lyrics.LyricsDocument
import com.xeno.mupa.data.lyrics.LyricsProvider
import com.xeno.mupa.data.lyrics.LyricsRepository
import com.xeno.mupa.data.lyrics.LyricsRequest
import com.xeno.mupa.streaming.api.StreamingError
import com.xeno.mupa.ui.player.LyricsSyncCalculator
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pengujian R4 (Lirik):
 * - >= 12 kasus LrcParser (standar, 3-digit ms, colon, 1-digit, whole-sec, multi-timestamp,
 *   offset positif/negatif clamp, enhanced word-level, metadata tag, spacer instrumental,
 *   deduplikasi, rusak/invalid detik >=60, fallback polos).
 * - Pencarian biner baris aktif + OKLCH L >= 0.87 untuk cover gelap.
 * - Pembersihan judul/artis + toleransi durasi +-2 detik di LRCLIB.
 * - Pembacaan lirik tertanam ID3v2 USLT & FLAC Vorbis Comment LYRICS.
 * - Cache Room (TTL 30 hari, negative cache 24 jam, evict maks 500, prioritas lokal -> eksternal).
 */
class R4LyricsParserAndRepositoryTest {

    // ---- 12+ Kasus LrcParser ----

    @Test
    fun case01_standardTwoDigitCentiseconds() {
        val parsed = LrcParser.parseDetailed("[00:12.34]Baris pertama\n[00:18.50]Baris kedua")
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(12_340L, parsed.lines[0].timestampMs)
        assertEquals(18_500L, parsed.lines[0].endTimestampMs)
        assertEquals("Baris pertama", parsed.lines[0].text)
    }

    @Test
    fun case02_threeDigitMillisAndColonSeparator() {
        val parsed = LrcParser.parseDetailed("[01:02.345]Tiga digit\n[01:05:67]Pemisah titik dua")
        assertTrue(parsed.isSynced)
        assertEquals(62_345L, parsed.lines[0].timestampMs)
        assertEquals(65_670L, parsed.lines[1].timestampMs)
    }

    @Test
    fun case03_singleDigitTenthsAndWholeSeconds() {
        val parsed = LrcParser.parseDetailed("[00:04.5]Sepersepuluh\n[00:09]Detik bulat")
        assertTrue(parsed.isSynced)
        assertEquals(4_500L, parsed.lines[0].timestampMs)
        assertEquals(9_000L, parsed.lines[1].timestampMs)
    }

    @Test
    fun case04_multiTimestampsPerLineSortedChronologically() {
        val raw = "[00:30.00][00:05.00][01:00.25]Reff berulang\n[00:15.00]Bait"
        val parsed = LrcParser.parseDetailed(raw)
        assertEquals(4, parsed.lines.size)
        assertEquals(listOf(5_000L, 15_000L, 30_000L, 60_250L), parsed.lines.map { it.timestampMs })
        assertEquals("Reff berulang", parsed.lines[0].text)
        assertEquals("Bait", parsed.lines[1].text)
        assertEquals("Reff berulang", parsed.lines[3].text)
    }

    @Test
    fun case05_globalOffsetPositiveAndNegativeClampedAtZero() {
        val neg = LrcParser.parseDetailed("[offset:-2000]\n[00:01.00]Awal terjepit\n[00:05.00]Lanjut")
        assertEquals(0L, neg.lines[0].timestampMs)
        assertEquals(3_000L, neg.lines[1].timestampMs)

        val pos = LrcParser.parseDetailed("[offset:+750]\n[00:01.00]Geser maju")
        assertEquals(1_750L, pos.lines[0].timestampMs)
    }

    @Test
    fun case06_enhancedWordLevelTimestamps() {
        val raw = "[00:10.00]<00:10.00>Your <00:10.40>morning <00:11.00>eyes\n[00:13.00]Next line"
        val parsed = LrcParser.parseDetailed(raw)
        assertTrue(parsed.isSynced)
        assertEquals("Your morning eyes", parsed.lines[0].text)
        assertEquals(3, parsed.lines[0].wordTimings.size)
        assertEquals(10_000L, parsed.lines[0].wordTimings[0].startMs)
        assertEquals(10_400L, parsed.lines[0].wordTimings[0].endMs)
        assertEquals("morning", parsed.lines[0].wordTimings[1].text)
    }

    @Test
    fun case07_metadataHeadersIgnored() {
        val raw = """
            [ar:yung kai]
            [ti:blue]
            [al:stay with the ocean]
            [by:Mupa]
            [length:3:34]
            [00:02.00]Actual lyric
        """.trimIndent()
        val parsed = LrcParser.parseDetailed(raw)
        assertEquals(1, parsed.lines.size)
        assertEquals("Actual lyric", parsed.lines[0].text)
    }

    @Test
    fun case08_blankInstrumentalSpacerClosesPreviousLineEndTimestamp() {
        val raw = """
            [00:05.00]Baris sebelum jeda
            [00:10.00]
            [00:25.00]Baris setelah jeda panjang
        """.trimIndent()
        val parsed = LrcParser.parseDetailed(raw)
        assertEquals(2, parsed.lines.size)
        assertEquals(10_000L, parsed.lines[0].endTimestampMs)
        assertEquals(25_000L, parsed.lines[1].timestampMs)
    }

    @Test
    fun case09_duplicateExactLinesDeduplicated() {
        val raw = "[00:05.00]Sama persis\n[00:05.00]Sama persis\n[00:10.00]Beda"
        val parsed = LrcParser.parseDetailed(raw)
        assertEquals(2, parsed.lines.size)
    }

    @Test
    fun case10_plainTextFallbackWithoutTimestamps() {
        val raw = "[ar:Artist]\nBaris polos satu\n\nBaris polos dua"
        val parsed = LrcParser.parseDetailed(raw)
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(0L, parsed.lines[0].timestampMs)
        assertEquals("Baris polos satu", parsed.lines[0].text)
        assertEquals("Baris polos dua", parsed.lines[1].text)
    }

    @Test
    fun case11_malformedTimestampsAndInvalidSecondsIgnoredSafely() {
        val raw = "[00:75.00]Detik tidak sah\n[broken]Tidak valid\n[00:12.00]Baris sah"
        val parsed = LrcParser.parseDetailed(raw)
        assertTrue(parsed.isSynced)
        assertEquals(1, parsed.lines.size)
        assertEquals("Baris sah", parsed.lines[0].text)
        assertEquals(12_000L, parsed.lines[0].timestampMs)
    }

    @Test
    fun case12_emptyOrBlankInputReturnsEmpty() {
        val parsed = LrcParser.parseDetailed("   \n\t\r\n  ")
        assertFalse(parsed.isSynced)
        assertTrue(parsed.lines.isEmpty())
    }

    // ---- Binary Search, OKLCH Lift, Title Cleaning, Duration +-2s ----

    @Test
    fun lyricsSyncBinarySearchAndOklchReadabilityLift() {
        val lines = (0 until 50).map { i ->
            LyricsLine(timestampMs = i * 3_000L, endTimestampMs = (i + 1) * 3_000L, text = "Line $i")
        }
        val state = LyricsSyncCalculator.compute(lines, positionMs = 91_500L)
        assertEquals(30, state.index)
        assertEquals(0.5f, state.lineProgress, 0.01f)

        val pitchDark = Color(0xFF0A0D18)
        val lifted = LyricsSyncCalculator.ensureReadableWipeColor(pitchDark)
        val relLum = 0.2126f * lifted.red + 0.7152f * lifted.green + 0.0722f * lifted.blue
        assertTrue("Expected OKLCH-lifted luminance >= 0.65, got $relLum", relLum >= 0.65f)
    }

    @Test
    fun titleAndArtistCleaningAndLrcLibTwoSecondTolerance() {
        assertEquals(
            "blue",
            TrackDisplayRules.cleanTitleForLyrics("blue (Official Music Video) [HD]"),
        )
        assertEquals(
            "Die With A Smile",
            TrackDisplayRules.cleanTitleForLyrics("Die With A Smile (feat. Lady Gaga)"),
        )
        assertEquals(
            "yung kai",
            LrcLibLyricsProvider.cleanArtistForLyrics("yung kai - Topic"),
        )
        assertEquals("", LrcLibLyricsProvider.cleanArtistForLyrics("Lokal"))

        val jsonStr = """
            [
              {"duration": 230.0, "syncedLyrics": "[00:01.00]Wrong version"},
              {"duration": 215.5, "syncedLyrics": "[00:02.00]Within 2s synced"},
              {"duration": 214.0, "plainLyrics": "Within 2s plain"}
            ]
        """.trimIndent()
        val candidates = Json.parseToJsonElement(jsonStr).jsonArray.map { it.jsonObject }

        // Target 214s -> 215.5s (delta 1.5s <= 2s) menang sebagai syncedLyrics
        val matched = LrcLibLyricsProvider.selectBestCandidate(candidates, targetDurationMs = 214_000L)
        assertNotNull(matched)
        assertTrue(matched!!.isSynced)
        assertEquals("Within 2s synced", matched.lines.first().text)

        // Target 100s -> semua kandidat di luar toleransi +-2s -> null
        val noMatch = LrcLibLyricsProvider.selectBestCandidate(candidates, targetDurationMs = 100_000L)
        assertNull(noMatch)
    }

    // ---- Embedded ID3v2 USLT & FLAC Vorbis Comment ----

    @Test
    fun embeddedReadersExtractId3v2UsltAndFlacVorbisLyrics() {
        // Bangun tag ID3v2.3 dengan frame USLT (UTF-8 encoding=3)
        val lyricText = "[00:01.00]Lirik dari tag MP3"
        val payload = ByteArrayOutputStream().apply {
            write(3) // UTF-8
            write("ind".toByteArray(StandardCharsets.ISO_8859_1))
            write(0) // empty description terminator
            write(lyricText.toByteArray(StandardCharsets.UTF_8))
        }.toByteArray()
        val frameSize = payload.size
        val tagSize = 10 + frameSize
        val id3 = ByteArrayOutputStream().apply {
            write("ID3".toByteArray(StandardCharsets.ISO_8859_1))
            write(byteArrayOf(3, 0, 0)) // v2.3.0, flags=0
            write(byteArrayOf(0, 0, (tagSize shr 7).toByte(), (tagSize and 0x7F).toByte()))
            write("USLT".toByteArray(StandardCharsets.ISO_8859_1))
            write(
                byteArrayOf(
                    (frameSize ushr 24).toByte(),
                    (frameSize ushr 16).toByte(),
                    (frameSize ushr 8).toByte(),
                    frameSize.toByte(),
                ),
            )
            write(byteArrayOf(0, 0)) // frame flags
            write(payload)
        }.toByteArray()

        assertEquals(lyricText, EmbeddedAudioLyricsReader.parseId3v2Uslt(id3))

        // Bangun blok FLAC METADATA_BLOCK_VORBIS_COMMENT dengan LYRICS=
        val flacLyric = "[00:02.50]Lirik dari FLAC Vorbis"
        val commentBytes = "LYRICS=$flacLyric".toByteArray(StandardCharsets.UTF_8)
        val vorbisBody = ByteArrayOutputStream().apply {
            write(byteArrayOf(0, 0, 0, 0)) // vendor len 0
            write(byteArrayOf(1, 0, 0, 0)) // 1 comment
            write(
                byteArrayOf(
                    (commentBytes.size and 0xFF).toByte(),
                    ((commentBytes.size ushr 8) and 0xFF).toByte(),
                    ((commentBytes.size ushr 16) and 0xFF).toByte(),
                    ((commentBytes.size ushr 24) and 0xFF).toByte(),
                ),
            )
            write(commentBytes)
        }.toByteArray()
        val flac = ByteArrayOutputStream().apply {
            write("fLaC".toByteArray(StandardCharsets.ISO_8859_1))
            write(0x84) // last block + type 4 (VORBIS_COMMENT)
            write(
                byteArrayOf(
                    ((vorbisBody.size ushr 16) and 0xFF).toByte(),
                    ((vorbisBody.size ushr 8) and 0xFF).toByte(),
                    (vorbisBody.size and 0xFF).toByte(),
                ),
            )
            write(vorbisBody)
        }.toByteArray()

        assertEquals(flacLyric, EmbeddedAudioLyricsReader.parseFlacVorbisLyrics(flac))
    }

    // ---- LyricsRepository Cache (TTL 30d, Negative 24h, Cap 500, Priority) ----

    @Test
    fun repositoryCachesPositiveHitsRespects30DayTtlAnd24HourNegativeCache() = runTest {
        val dao = InMemoryLyricsCacheDao()
        var externalCalls = 0
        var localCalls = 0
        var now = 1_700_000_000_000L

        val fakeLocal = object : LyricsProvider {
            override val providerId = "local"
            override val providerLabel = "Lokal"
            override val priority = 10
            override suspend fun fetch(request: LyricsRequest): Result<LyricsDocument> {
                localCalls++
                return if (request.trackId == "content://local/has_lrc") {
                    val raw = "[00:01.00]Lirik lokal"
                    Result.success(
                        LyricsDocument(
                            lines = LrcParser.parse(raw),
                            rawText = raw,
                            isSynced = true,
                            providerId = "local_lrc",
                            providerLabel = "File .lrc",
                        ),
                    )
                } else {
                    Result.failure(StreamingError.Unavailable("tidak ada"))
                }
            }
        }

        val fakeExternal = object : LyricsProvider {
            override val providerId = "lrclib"
            override val providerLabel = "LRCLIB"
            override val priority = 20
            override suspend fun fetch(request: LyricsRequest): Result<LyricsDocument> {
                externalCalls++
                return if (request.title == "blue") {
                    val raw = "[00:01.00]Your morning eyes"
                    Result.success(
                        LyricsDocument(
                            lines = LrcParser.parse(raw),
                            rawText = raw,
                            isSynced = true,
                            providerId = "lrclib",
                            providerLabel = "LRCLIB",
                        ),
                    )
                } else {
                    Result.failure(StreamingError.Unavailable("404"))
                }
            }
        }

        val repo = LyricsRepository(dao, fakeLocal, fakeExternal) { now }

        // 1. Lokal prioritas sebelum eksternal
        val localRes = repo.getLyrics(
            LyricsRequest("content://local/has_lrc", "Bertaut", "Nadin", durationMs = 250_000L, sourceType = AudioSourceType.LOCAL),
        ).getOrThrow()
        assertEquals("File .lrc", localRes.providerLabel)
        assertEquals(1, localCalls)
        assertEquals(0, externalCalls)

        // 2. Streaming positive cache hit (panggilan kedua tidak menyentuh jaringan)
        val reqBlue = LyricsRequest("s1", "blue", "yung kai", durationMs = 214_000L)
        assertTrue(repo.getLyrics(reqBlue).isSuccess)
        assertTrue(repo.getLyrics(reqBlue).isSuccess)
        assertEquals(1, externalCalls)

        // 3. Setelah > 30 hari, TTL habis -> fetch ulang
        now += LyricsRepository.POSITIVE_CACHE_TTL_MS + 1_000L
        assertTrue(repo.getLyrics(reqBlue).isSuccess)
        assertEquals(2, externalCalls)

        // 4. Negative cache 24 jam menahan request gagal berulang
        val reqMissing = LyricsRequest("s_none", "Lagu Tanpa Lirik", "Unknown", durationMs = 180_000L)
        assertTrue(repo.getLyrics(reqMissing).isFailure)
        assertTrue(repo.getLyrics(reqMissing).isFailure)
        assertEquals(3, externalCalls) // hanya 1 kali network call

        // 5. forceRefresh menembus negative cache
        assertTrue(repo.getLyrics(reqMissing, forceRefresh = true).isFailure)
        assertEquals(4, externalCalls)

        // 6. Setelah > 24 jam, negative cache kadaluarsa
        now += LyricsRepository.NEGATIVE_CACHE_TTL_MS + 1_000L
        assertTrue(repo.getLyrics(reqMissing).isFailure)
        assertEquals(5, externalCalls)
    }

    @Test
    fun repositoryPrunesCacheAbove500Entries() = runTest {
        val dao = InMemoryLyricsCacheDao()
        var now = 1_700_000_000_000L
        repeat(505) { idx ->
            dao.upsert(
                LyricsCacheEntity(
                    cacheKey = "k_$idx",
                    rawLrc = "[00:01.00]Baris $idx",
                    isSynced = true,
                    providerId = "lrclib",
                    isNegative = false,
                    cachedAtEpochMs = now + idx,
                ),
            )
        }
        val fakeEmpty = object : LyricsProvider {
            override val providerId = "local"
            override val providerLabel = "Lokal"
            override val priority = 10
            override suspend fun fetch(request: LyricsRequest) =
                Result.failure<LyricsDocument>(StreamingError.Unavailable("none"))
        }
        val fakeHit = object : LyricsProvider {
            override val providerId = "lrclib"
            override val providerLabel = "LRCLIB"
            override val priority = 20
            override suspend fun fetch(request: LyricsRequest) = Result.success(
                LyricsDocument(LrcParser.parse("[00:01.00]Baru"), "[00:01.00]Baru", true, "lrclib", "LRCLIB"),
            )
        }
        val repo = LyricsRepository(dao, fakeEmpty, fakeHit) { now + 10_000L }
        assertTrue(repo.getLyrics(LyricsRequest("new1", "New Song", "Artist", durationMs = 200_000L)).isSuccess)
        assertTrue("Cache must be pruned to <= 500, got ${dao.count()}", dao.count() <= LyricsRepository.MAX_CACHE_ENTRIES)
    }

    private class InMemoryLyricsCacheDao : LyricsCacheDao {
        private val map = LinkedHashMap<String, LyricsCacheEntity>()

        override suspend fun getByKey(cacheKey: String): LyricsCacheEntity? = map[cacheKey]

        override suspend fun upsert(entry: LyricsCacheEntity) {
            map[entry.cacheKey] = entry
        }

        override suspend fun deleteOlderThan(cutoffMs: Long) {
            val toRemove = map.values.filter { it.cachedAtEpochMs < cutoffMs }.map { it.cacheKey }
            toRemove.forEach { map.remove(it) }
        }

        override suspend fun count(): Int = map.size

        override suspend fun deleteOldest(deleteCount: Int) {
            val oldest = map.values.sortedBy { it.cachedAtEpochMs }.take(deleteCount).map { it.cacheKey }
            oldest.forEach { map.remove(it) }
        }

        override suspend fun deleteAll() {
            map.clear()
        }
    }
}
