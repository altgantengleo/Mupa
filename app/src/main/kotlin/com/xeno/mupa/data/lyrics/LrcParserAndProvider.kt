package com.xeno.mupa.data.lyrics

import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.core.model.LyricsWord
import com.xeno.mupa.core.model.TrackDisplayRules
import com.xeno.mupa.streaming.api.StreamingError
import com.xeno.mupa.streaming.api.StreamingLyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToLong

data class LyricsRequest(
    val trackId: String,
    val title: String,
    val artistName: String,
    val albumTitle: String? = null,
    val durationMs: Long = 0L,
    val sourceType: AudioSourceType = AudioSourceType.STREAMING,
)

data class LyricsDocument(
    val lines: List<LyricsLine>,
    val rawText: String,
    val isSynced: Boolean,
    val providerId: String,
    val providerLabel: String,
)

data class ParsedLyrics(
    val lines: List<LyricsLine>,
    val isSynced: Boolean,
)

/**
 * Antarmuka penyedia lirik terisolasi (lokal maupun eksternal).
 * Setiap penyedia dapat ditambah/diganti tanpa menyentuh UI maupun ViewModel.
 */
interface LyricsProvider {
    val providerId: String
    val providerLabel: String
    val priority: Int

    suspend fun fetch(request: LyricsRequest): Result<LyricsDocument>
}

/**
 * Parser LRC lengkap (12+ kasus uji):
 * - [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [mm:ss.x], [mm:ss]
 * - Multi-timestamp per baris ([00:01.00][00:10.50]Teks)
 * - Global [offset:±ms] (terjepit >= 0ms)
 * - Enhanced LRC word-level (<mm:ss.xx>kata)
 * - Tag metadata ([ar:], [ti:], [al:], [by:], [length:], [re:], [ve:]) diabaikan aman
 * - Baris kosong / instrumental spacer menutup endTimestampMs baris sebelumnya tanpa baris kosong
 * - Deduplikasi baris identik (timestampMs + text)
 * - Validasi detik < 60 & pengabaian timestamp rusak tanpa crash
 * - Fallback lirik teks polos (isSynced = false, timestampMs = 0L)
 */
object LrcParser {
    private val lineTimestampRegex = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val wordTimestampRegex = Regex("""<(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?>""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val metadataTagRegex = Regex("""^\s*\[[a-zA-Z]{2,12}\s*:.*]\s*$""")

    fun parse(rawText: String): List<LyricsLine> = parseDetailed(rawText).lines

    fun parseDetailed(rawText: String): ParsedLyrics {
        if (rawText.isBlank()) return ParsedLyrics(emptyList(), isSynced = false)

        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val offsetMs = offsetRegex.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L

        data class RawEntry(
            val timestampMs: Long,
            val text: String,
            val words: List<LyricsWord>,
        )

        val entries = mutableListOf<RawEntry>()
        val spacerTimestamps = mutableListOf<Long>()

        normalized.lineSequence().forEach { rawLine ->
            val trimmedLine = rawLine.trim()
            if (trimmedLine.isEmpty()) return@forEach

            val matches = lineTimestampRegex.findAll(trimmedLine).toList()
            if (matches.isEmpty()) return@forEach

            val validTimestamps = matches.mapNotNull { m ->
                parseTimestampMatch(m, offsetMs)
            }
            if (validTimestamps.isEmpty()) return@forEach

            val bodyRaw = lineTimestampRegex.replace(trimmedLine, "").trim()
            if (bodyRaw.isEmpty()) {
                spacerTimestamps.addAll(validTimestamps)
                return@forEach
            }

            val (cleanText, words) = parseEnhancedWords(bodyRaw, offsetMs)
            if (cleanText.isEmpty()) {
                spacerTimestamps.addAll(validTimestamps)
                return@forEach
            }

            for (ts in validTimestamps) {
                entries.add(RawEntry(timestampMs = ts, text = cleanText, words = words))
            }
        }

        if (entries.isNotEmpty()) {
            // Urutkan kronologis dan buang duplikasi persis (timestampMs + text)
            val deduped = entries
                .sortedBy { it.timestampMs }
                .distinctBy { it.timestampMs to it.text }
            val sortedSpacers = spacerTimestamps.sorted()

            val result = deduped.mapIndexed { idx, entry ->
                val nextLineTs = deduped.getOrNull(idx + 1)?.timestampMs
                val spacerBetween = sortedSpacers.firstOrNull { sp ->
                    sp > entry.timestampMs && (nextLineTs == null || sp < nextLineTs)
                }
                val endTs = spacerBetween ?: nextLineTs
                LyricsLine(
                    timestampMs = entry.timestampMs,
                    endTimestampMs = endTs,
                    text = entry.text,
                    wordTimings = entry.words,
                )
            }
            return ParsedLyrics(lines = result, isSynced = true)
        }

        // Fallback lirik polos (tanpa timestamp valid)
        val plainLines = normalized.lineSequence()
            .map { it.trim() }
            .filter { line ->
                line.isNotEmpty() &&
                    !metadataTagRegex.matches(line) &&
                    !line.startsWith("[")
            }
            .map { text ->
                LyricsLine(
                    timestampMs = 0L,
                    endTimestampMs = null,
                    text = text,
                )
            }
            .toList()

        return ParsedLyrics(lines = plainLines, isSynced = false)
    }

    private fun parseTimestampMatch(match: MatchResult, offsetMs: Long): Long? {
        val min = match.groupValues[1].toLongOrNull() ?: return null
        val sec = match.groupValues[2].toLongOrNull() ?: return null
        if (sec >= 60L) return null
        val fracRaw = match.groupValues[3]
        val fracMs = when (fracRaw.length) {
            0 -> 0L
            1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
            2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
            else -> fracRaw.take(3).toLongOrNull() ?: 0L
        }
        return (min * 60_000L + sec * 1_000L + fracMs + offsetMs).coerceAtLeast(0L)
    }

    private fun parseEnhancedWords(bodyRaw: String, offsetMs: Long): Pair<String, List<LyricsWord>> {
        val wordMatches = wordTimestampRegex.findAll(bodyRaw).toList()
        if (wordMatches.isEmpty()) {
            return bodyRaw.replace(Regex("""\s+"""), " ").trim() to emptyList()
        }

        val rawWords = mutableListOf<Pair<Long, String>>()
        for (i in wordMatches.indices) {
            val match = wordMatches[i]
            val startMs = parseTimestampMatch(match, offsetMs) ?: continue
            val textStart = match.range.last + 1
            val textEnd = wordMatches.getOrNull(i + 1)?.range?.first ?: bodyRaw.length
            val seg = bodyRaw.substring(textStart, textEnd).trim()
            if (seg.isNotEmpty()) {
                rawWords.add(startMs to seg)
            }
        }
        val cleanText = wordTimestampRegex.replace(bodyRaw, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        val words = rawWords.mapIndexed { idx, (start, text) ->
            val end = rawWords.getOrNull(idx + 1)?.first ?: (start + 450L)
            LyricsWord(startMs = start, endMs = end.coerceAtLeast(start + 80L), text = text)
        }
        return cleanText to words
    }
}

/**
 * Penyedia lirik online LRCLIB (open-source MIT, API publik resmi tanpa login/scraping).
 * - Judul & artis dibersihkan sebelum pencarian.
 * - Pencocokan durasi dalam toleransi +-2 detik.
 * - Timeout ketat 6 detik per panggilan pada Dispatchers.IO.
 */
class LrcLibLyricsProvider(
    httpClient: OkHttpClient,
) : LyricsProvider, StreamingLyricsProvider {

    override val providerId: String = PROVIDER_ID
    override val providerLabel: String = "LRCLIB"
    override val priority: Int = 20

    private val boundedClient: OkHttpClient = httpClient.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetch(request: LyricsRequest): Result<LyricsDocument> = withContext(Dispatchers.IO) {
        val cleanTitle = TrackDisplayRules.cleanTitleForLyrics(request.title)
        val cleanArtist = cleanArtistForLyrics(request.artistName)
        if (cleanTitle.isBlank()) {
            return@withContext Result.failure(StreamingError.Unavailable("Judul lagu kosong"))
        }

        // 1. Coba endpoint exact match /api/get bila durasi diketahui
        if (request.durationMs > 0L && cleanArtist.isNotBlank()) {
            val exact = fetchExact(cleanTitle, cleanArtist, request.albumTitle, request.durationMs)
            if (exact != null) return@withContext Result.success(exact)
        }

        // 2. Fallback /api/search dengan track_name + artist_name
        val searchUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", cleanTitle)
            .apply {
                if (cleanArtist.isNotBlank()) addQueryParameter("artist_name", cleanArtist)
            }
            .build()

        val firstBody = executeGet(searchUrl.toString()).getOrNull()
        val firstCandidates = firstBody?.let { b ->
            runCatching { json.parseToJsonElement(b).jsonArray }.getOrNull()
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        }.orEmpty()

        selectBestCandidate(firstCandidates, request.durationMs, cleanTitle, cleanArtist)?.let {
            return@withContext Result.success(it)
        }

        // 3. Fallback /api/search?q= (judul + artis)
        val qText = listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ")
        val qUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", qText)
            .build()
        val qBody = executeGet(qUrl.toString()).getOrNull()
        val qCandidates = qBody?.let { b ->
            runCatching { json.parseToJsonElement(b).jsonArray }.getOrNull()
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
        }.orEmpty()

        selectBestCandidate(qCandidates, request.durationMs, cleanTitle, cleanArtist)?.let {
            return@withContext Result.success(it)
        }

        // 4. Fallback cerdas hanya judul bersih (mengatasi re-upload YouTube seperti "JVKE Golden Hour" oleh channel random)
        if (cleanArtist.isNotBlank()) {
            val titleOnlyUrl = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("q", cleanTitle)
                .build()
            val titleOnlyBody = executeGet(titleOnlyUrl.toString())
                .getOrElse { return@withContext Result.failure(it) }
            val titleCandidates = runCatching { json.parseToJsonElement(titleOnlyBody).jsonArray }.getOrNull()
                ?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                .orEmpty()
            selectBestCandidate(titleCandidates, request.durationMs, cleanTitle, "")?.let {
                return@withContext Result.success(it)
            }
        }

        Result.failure(StreamingError.Unavailable("Lirik tidak ditemukan di LRCLIB"))
    }

    override suspend fun fetchLyrics(
        title: String,
        artistName: String,
        durationMs: Long,
    ): Result<List<LyricsLine>> = fetch(
        LyricsRequest(
            trackId = "$title|$artistName",
            title = title,
            artistName = artistName,
            durationMs = durationMs,
        ),
    ).map { it.lines }

    private fun fetchExact(
        cleanTitle: String,
        cleanArtist: String,
        albumTitle: String?,
        durationMs: Long,
    ): LyricsDocument? {
        val durationSec = (durationMs / 1000.0).roundToLong()
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", cleanTitle)
            .addQueryParameter("artist_name", cleanArtist)
            .addQueryParameter("duration", durationSec.toString())
            .apply {
                if (!albumTitle.isNullOrBlank()) addQueryParameter("album_name", albumTitle)
            }
            .build()

        val body = executeGet(url.toString()).getOrNull() ?: return null
        val obj = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        return candidateToDocument(obj)
    }

    private fun executeGet(url: String): Result<String> {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MupaAndroid/1.0.0 (GPL-3.0; https://github.com/altgantengleo/Mupa)")
            .get()
            .build()
        return try {
            boundedClient.newCall(req).execute().use { resp ->
                if (resp.code == 404) {
                    return Result.failure(StreamingError.Unavailable("LRCLIB 404"))
                }
                if (resp.code !in 200..299) {
                    return Result.failure(StreamingError.Network("LRCLIB HTTP ${resp.code}"))
                }
                Result.success(resp.body?.string().orEmpty())
            }
        } catch (io: IOException) {
            Result.failure(StreamingError.Network("LRCLIB gagal: ${io.message}", io))
        }
    }

    companion object {
        const val PROVIDER_ID = "lrclib"
        const val TIMEOUT_SECONDS = 6L
        const val DURATION_TOLERANCE_MS = 2_000L

        internal fun cleanArtistForLyrics(rawArtist: String): String {
            val trimmed = rawArtist.trim()
            if (trimmed.equals("Lokal", ignoreCase = true) ||
                trimmed.equals("Streaming", ignoreCase = true) ||
                trimmed.equals("Unknown", ignoreCase = true) ||
                trimmed.equals("<unknown>", ignoreCase = true) ||
                trimmed.equals("Artis tidak diketahui", ignoreCase = true)
            ) {
                return ""
            }
            return trimmed
                .replace(Regex("""\s*[•·|].*$"""), "")
                .replace(Regex("""\s*-\s*Topic$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:feat\.?|ft\.?|x|&|,)\s+.+$""", RegexOption.IGNORE_CASE), "")
                .trim()
        }

        internal fun selectBestCandidate(
            candidates: List<JsonObject>,
            targetDurationMs: Long,
            expectedTitle: String = "",
            expectedArtist: String = "",
        ): LyricsDocument? {
            if (candidates.isEmpty()) return null

            val normExpectedTitle = expectedTitle.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ").trim()
            val normExpectedArtist = expectedArtist.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ").trim()
            val queryTokens = (normExpectedTitle + " " + normExpectedArtist)
                .split(Regex("""\s+"""))
                .filter { it.length >= 2 }
                .toSet()

            data class ScoredDoc(val doc: LyricsDocument, val score: Int)

            val scored = candidates.mapNotNull { obj ->
                val doc = candidateToDocument(obj) ?: return@mapNotNull null
                val candTitle = obj["trackName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    .lowercase().replace(Regex("""[^a-z0-9\s]"""), " ").trim()
                val candArtist = obj["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    .lowercase().replace(Regex("""[^a-z0-9\s]"""), " ").trim()
                val candCombined = "$candTitle $candArtist"

                var score = 0
                if (doc.isSynced) score += 50

                // Toleransi durasi bertingkat (cocok untuk perbedaan intro/outro YouTube vs studio)
                if (targetDurationMs > 0L) {
                    val durSec = obj["duration"]?.jsonPrimitive?.doubleOrNull
                    if (durSec != null && durSec > 0.0) {
                        val diffMs = abs((durSec * 1_000.0).roundToLong() - targetDurationMs)
                        score += when {
                            diffMs <= DURATION_TOLERANCE_MS -> 35
                            diffMs <= 6_000L -> 24
                            diffMs <= 12_000L -> 12
                            diffMs <= 20_000L -> 0
                            else -> -25
                        }
                    }
                }

                // Kecocokan judul & artis berbasis token
                if (queryTokens.isNotEmpty()) {
                    val hits = queryTokens.count { candCombined.contains(it) }
                    val ratio = hits.toFloat() / queryTokens.size.toFloat()
                    if (ratio < 0.35f) return@mapNotNull null
                    score += (ratio * 50f).toInt()
                }
                if (normExpectedTitle.isNotEmpty() && (candTitle == normExpectedTitle || normExpectedTitle.contains(candTitle))) {
                    score += 20
                }
                if (normExpectedArtist.isNotEmpty() && (candArtist.contains(normExpectedArtist) || normExpectedArtist.contains(candArtist))) {
                    score += 20
                }

                ScoredDoc(doc, score)
            }

            return scored.maxByOrNull { it.score }?.doc
        }

        private fun candidateToDocument(obj: JsonObject): LyricsDocument? {
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            if (!synced.isNullOrBlank()) {
                val parsed = LrcParser.parseDetailed(synced)
                if (parsed.lines.isNotEmpty()) {
                    return LyricsDocument(
                        lines = parsed.lines,
                        rawText = synced,
                        isSynced = parsed.isSynced,
                        providerId = PROVIDER_ID,
                        providerLabel = "LRCLIB",
                    )
                }
            }
            val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
            if (!plain.isNullOrBlank()) {
                val parsed = LrcParser.parseDetailed(plain)
                if (parsed.lines.isNotEmpty()) {
                    return LyricsDocument(
                        lines = parsed.lines,
                        rawText = plain,
                        isSynced = parsed.isSynced,
                        providerId = PROVIDER_ID,
                        providerLabel = "LRCLIB",
                    )
                }
            }
            return null
        }
    }
}
