package com.xeno.mupa.data.lyrics

import com.xeno.mupa.core.model.LyricsLine
import com.xeno.mupa.streaming.api.StreamingError
import com.xeno.mupa.streaming.api.StreamingLyricsProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Parser LRC tersinkron (mendukung [mm:ss.xx], [mm:ss:xxx], multi-timestamp per baris,
 * [offset:±ms], dan fallback teks polos).
 */
object LrcParser {
    private val timestampRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(rawText: String): List<LyricsLine> {
        if (rawText.isBlank()) return emptyList()
        val offsetMs = offsetRegex.find(rawText)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
        val timed = mutableListOf<Pair<Long, String>>()

        rawText.lineSequence().forEach { rawLine ->
            val matches = timestampRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = timestampRegex.replace(rawLine, "").trim()
            if (text.isEmpty()) return@forEach

            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0L
                val sec = m.groupValues[2].toLongOrNull() ?: 0L
                val fracRaw = m.groupValues[3]
                val fracMs = when (fracRaw.length) {
                    0 -> 0L
                    1 -> (fracRaw.toLongOrNull() ?: 0L) * 100L
                    2 -> (fracRaw.toLongOrNull() ?: 0L) * 10L
                    else -> fracRaw.take(3).toLongOrNull() ?: 0L
                }
                val ts = (min * 60_000L + sec * 1_000L + fracMs + offsetMs).coerceAtLeast(0L)
                timed.add(ts to text)
            }
        }

        if (timed.size >= 2) {
            val sorted = timed.sortedBy { it.first }
            return sorted.mapIndexed { idx, (ts, text) ->
                val endTs = sorted.getOrNull(idx + 1)?.first
                LyricsLine(timestampMs = ts, endTimestampMs = endTs, text = text)
            }
        }

        // Fallback teks polos tanpa timestamp
        return rawText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("[") }
            .mapIndexed { idx, text ->
                LyricsLine(
                    timestampMs = idx * 4_000L,
                    endTimestampMs = (idx + 1) * 4_000L,
                    text = text,
                )
            }
            .toList()
    }
}

/**
 * Penyedia lirik online LRCLib (open-source, tanpa auth/cookie — Default D4).
 */
class LrcLibLyricsProvider(
    private val httpClient: OkHttpClient,
) : StreamingLyricsProvider {

    override val providerId: String = "lrclib"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun fetchLyrics(
        title: String,
        artistName: String,
        durationMs: Long,
    ): Result<List<LyricsLine>> = withContext(Dispatchers.IO) {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artistName)
            .build()

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "MupaAndroid/0.4 (GPL-3.0)")
            .get()
            .build()

        val body = try {
            httpClient.newCall(req).execute().use { resp ->
                if (resp.code !in 200..299) {
                    return@withContext Result.failure(StreamingError.Network("LRCLib HTTP ${resp.code}"))
                }
                resp.body?.string().orEmpty()
            }
        } catch (io: IOException) {
            return@withContext Result.failure(StreamingError.Network("LRCLib gagal: ${io.message}", io))
        }

        val items = runCatching { json.parseToJsonElement(body).jsonArray }.getOrNull()
            ?: return@withContext Result.failure(StreamingError.ParseError("Respons LRCLib tidak valid"))

        for (el in items) {
            val obj = runCatching { el.jsonObject }.getOrNull() ?: continue
            val synced = obj["syncedLyrics"]?.jsonPrimitive?.contentOrNull
            if (!synced.isNullOrBlank()) {
                val parsed = LrcParser.parse(synced)
                if (parsed.isNotEmpty()) return@withContext Result.success(parsed)
            }
        }
        for (el in items) {
            val obj = runCatching { el.jsonObject }.getOrNull() ?: continue
            val plain = obj["plainLyrics"]?.jsonPrimitive?.contentOrNull
            if (!plain.isNullOrBlank()) {
                val parsed = LrcParser.parse(plain)
                if (parsed.isNotEmpty()) return@withContext Result.success(parsed)
            }
        }
        Result.failure(StreamingError.Unavailable("Lirik tidak ditemukan di LRCLib"))
    }
}
