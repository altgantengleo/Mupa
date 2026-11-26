package com.xeno.mupa.streaming.jiosaavn

import com.xeno.mupa.core.domain.ResolvedStream
import com.xeno.mupa.streaming.api.AudioQualityPreference
import com.xeno.mupa.streaming.api.StreamLookupRequest
import com.xeno.mupa.streaming.api.StreamingAudioProvider
import com.xeno.mupa.streaming.api.StreamingError
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
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Penyedia fallback audio terisolasi (JioSaavn guest API — AAC 96/160/320 kbps).
 * Tidak ada autentikasi user; terpisah total dari UI dan InnerTube.
 */
class JioSaavnProvider(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
) : StreamingAudioProvider {

    override val providerId: String = "jiosaavn"
    override val priority: Int = 20
    override val supportsDirectTrackId: Boolean = false

    override suspend fun resolveAudio(request: StreamLookupRequest): Result<ResolvedStream> =
        withContext(Dispatchers.IO) {
            val query = "${request.artistName} ${request.title}".trim()
            if (query.isEmpty()) {
                return@withContext Result.failure(StreamingError.Unavailable("Kata kunci kosong"))
            }
            val url = "https://www.jiosaavn.com/api.php".toHttpUrl().newBuilder()
                .addQueryParameter("__call", "search.getResults")
                .addQueryParameter("_format", "json")
                .addQueryParameter("_marker", "0")
                .addQueryParameter("api_version", "4")
                .addQueryParameter("ctx", "android")
                .addQueryParameter("p", "1")
                .addQueryParameter("n", "6")
                .addQueryParameter("q", query)
                .build()

            val httpReq = Request.Builder()
                .url(url)
                .header("Cookie", "explicit_content=1")
                .get()
                .build()

            val raw = try {
                httpClient.newCall(httpReq).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    when (resp.code) {
                        in 200..299 -> Result.success(text)
                        429 -> Result.failure(StreamingError.RateLimited())
                        else -> Result.failure(StreamingError.Network("JioSaavn HTTP ${resp.code}"))
                    }
                }
            } catch (io: IOException) {
                Result.failure(StreamingError.Network("Koneksi JioSaavn gagal: ${io.message}", io))
            }

            raw.fold(
                onSuccess = { JioSaavnParser.parseBestStream(it, request.quality) },
                onFailure = { Result.failure(it) },
            )
        }
}

object JioSaavnParser {
    private const val MEDIA_DES_KEY = "38346591"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseBestStream(
        rawJson: String,
        quality: AudioQualityPreference = AudioQualityPreference.HIGH,
    ): Result<ResolvedStream> {
        val root = runCatching { json.parseToJsonElement(rawJson).jsonObject }
            .getOrElse { return Result.failure(StreamingError.ParseError("JSON JioSaavn tidak valid", it)) }

        val results = root["results"]?.jsonArray
            ?: return Result.failure(StreamingError.Unavailable("Hasil pencarian JioSaavn kosong"))

        for (item in results) {
            val obj = runCatching { item.jsonObject }.getOrNull() ?: continue
            val moreInfo = runCatching { obj["more_info"]?.jsonObject }.getOrNull() ?: continue
            val encrypted = moreInfo["encrypted_media_url"]?.jsonPrimitive?.contentOrNull ?: continue
            val decoded = decryptMediaUrl(encrypted) ?: continue
            val targetSuffix = when (quality) {
                AudioQualityPreference.LOW -> "_96.mp4"
                AudioQualityPreference.MEDIUM -> "_160.mp4"
                AudioQualityPreference.HIGH -> "_320.mp4"
            }
            val finalUrl = decoded
                .replace("_96.mp4", targetSuffix)
                .replace("_160.mp4", targetSuffix)
                .replace("http://", "https://")
            val bitrate = when (quality) {
                AudioQualityPreference.LOW -> 96
                AudioQualityPreference.MEDIUM -> 160
                AudioQualityPreference.HIGH -> 320
            }
            return Result.success(
                ResolvedStream(
                    url = finalUrl,
                    mimeType = "audio/mp4",
                    bitrateKbps = bitrate,
                ),
            )
        }

        return Result.failure(StreamingError.Unavailable("Tidak ada stream cocok di JioSaavn"))
    }

    internal fun decryptMediaUrl(encryptedBase64: String): String? = runCatching {
        val rawBytes = Base64.getDecoder().decode(encryptedBase64.trim())
        val keySpec = SecretKeySpec(MEDIA_DES_KEY.toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, keySpec)
        String(cipher.doFinal(rawBytes), Charsets.UTF_8).trim()
    }.getOrNull()

    internal fun encryptForTest(plainUrl: String): String {
        val keySpec = SecretKeySpec(MEDIA_DES_KEY.toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, keySpec)
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainUrl.toByteArray(Charsets.UTF_8)))
    }
}
