package com.xeno.mupa.data.lyrics

import android.content.Context
import android.net.Uri
import com.xeno.mupa.core.model.AudioSourceType
import com.xeno.mupa.data.local.LocalLibraryRepository
import com.xeno.mupa.data.local.db.LyricsCacheDao
import com.xeno.mupa.data.local.db.LyricsCacheEntity
import com.xeno.mupa.streaming.api.StreamingError
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Penyedia lirik lokal (prioritas tertinggi untuk lagu Lokal):
 * 1. Sidecar `.lrc` di folder SAF yang sama.
 * 2. Lirik tertanam (USLT ID3v2 / Vorbis Comment LYRICS) di dalam file audio.
 */
@Singleton
class LocalLyricsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val localLibraryRepository: LocalLibraryRepository,
) : LyricsProvider {

    override val providerId: String = "local"
    override val providerLabel: String = "Lokal"
    override val priority: Int = 10

    override suspend fun fetch(request: LyricsRequest): Result<LyricsDocument> = withContext(Dispatchers.IO) {
        if (request.sourceType != AudioSourceType.LOCAL || !request.trackId.startsWith("content://")) {
            return@withContext Result.failure(StreamingError.Unavailable("Bukan trek lokal SAF"))
        }

        // 1. Cek sidecar .lrc di folder yang sama
        val sidecar = localLibraryRepository.readSidecarLrc(request.trackId)
        if (!sidecar.isNullOrBlank()) {
            val parsed = LrcParser.parseDetailed(sidecar)
            if (parsed.lines.isNotEmpty()) {
                return@withContext Result.success(
                    LyricsDocument(
                        lines = parsed.lines,
                        rawText = sidecar,
                        isSynced = parsed.isSynced,
                        providerId = "local_lrc",
                        providerLabel = "File .lrc",
                    ),
                )
            }
        }

        // 2. Cek lirik tertanam (USLT / Vorbis)
        val embedded = EmbeddedAudioLyricsReader.readEmbeddedLyrics(context, Uri.parse(request.trackId))
        if (!embedded.isNullOrBlank()) {
            val parsed = LrcParser.parseDetailed(embedded)
            if (parsed.lines.isNotEmpty()) {
                return@withContext Result.success(
                    LyricsDocument(
                        lines = parsed.lines,
                        rawText = embedded,
                        isSynced = parsed.isSynced,
                        providerId = "local_tag",
                        providerLabel = "Tag Audio",
                    ),
                )
            }
        }

        Result.failure(StreamingError.Unavailable("Tidak ada lirik lokal (.lrc / tag)"))
    }
}

/**
 * Orkestrator lirik Mupa dengan cache Room terkelola:
 * - Cache positif TTL 30 hari, batas maks 500 entri (evict tertua otomatis).
 * - Negative cache 24 jam agar tidak mengulang panggilan jaringan gagal sia-sia (kecuali forceRefresh).
 * - Urutan: Cache Room -> Penyedia Lokal (jika LOCAL) -> Penyedia Eksternal (LRCLIB, timeout 6 dtk).
 * - Berjalan penuh di Dispatchers.IO, tidak pernah memblokir playback.
 */
@Singleton
class LyricsRepository internal constructor(
    private val cacheDao: LyricsCacheDao,
    private val localProvider: LyricsProvider,
    private val externalProvider: LyricsProvider,
    internal var clockMs: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(
        cacheDao: LyricsCacheDao,
        localProvider: LocalLyricsProvider,
        externalProvider: LrcLibLyricsProvider,
    ) : this(
        cacheDao = cacheDao,
        localProvider = localProvider as LyricsProvider,
        externalProvider = externalProvider as LyricsProvider,
    )

    suspend fun getLyrics(
        request: LyricsRequest,
        forceRefresh: Boolean = false,
    ): Result<LyricsDocument> = withContext(Dispatchers.IO) {
        val now = clockMs()
        val key = buildCacheKey(request)

        if (!forceRefresh) {
            val cached = runCatching { cacheDao.getByKey(key) }.getOrNull()
            if (cached != null) {
                val ageMs = now - cached.cachedAtEpochMs
                if (cached.isNegative) {
                    if (ageMs < NEGATIVE_CACHE_TTL_MS) {
                        return@withContext Result.failure(
                            StreamingError.Unavailable("Lirik tidak tersedia untuk lagu ini"),
                        )
                    }
                } else if (ageMs < POSITIVE_CACHE_TTL_MS && !cached.rawLrc.isNullOrBlank()) {
                    val parsed = LrcParser.parseDetailed(cached.rawLrc)
                    if (parsed.lines.isNotEmpty()) {
                        return@withContext Result.success(
                            LyricsDocument(
                                lines = parsed.lines,
                                rawText = cached.rawLrc,
                                isSynced = cached.isSynced && parsed.isSynced,
                                providerId = cached.providerId,
                                providerLabel = providerDisplayLabel(cached.providerId),
                            ),
                        )
                    }
                }
            }
        }

        // Susun rantai penyedia sesuai sumber trek
        val providers = buildList<LyricsProvider> {
            if (request.sourceType == AudioSourceType.LOCAL) {
                add(localProvider)
            }
            add(externalProvider)
        }.sortedBy { it.priority }

        var lastError: Throwable? = null
        var hadNetworkFailure = false

        for (provider in providers) {
            val result = provider.fetch(request)
            if (result.isSuccess) {
                val doc = result.getOrThrow()
                persistCacheAndPrune(
                    LyricsCacheEntity(
                        cacheKey = key,
                        rawLrc = doc.rawText,
                        isSynced = doc.isSynced,
                        providerId = doc.providerId,
                        isNegative = false,
                        cachedAtEpochMs = now,
                    ),
                    now = now,
                )
                return@withContext Result.success(doc)
            } else {
                val err = result.exceptionOrNull()
                if (err is StreamingError.Network) {
                    hadNetworkFailure = true
                }
                lastError = err
            }
        }

        // Simpan negative cache 24 jam hanya jika benar-benar tidak ditemukan (bukan error jaringan sementara)
        if (!hadNetworkFailure) {
            persistCacheAndPrune(
                LyricsCacheEntity(
                    cacheKey = key,
                    rawLrc = null,
                    isSynced = false,
                    providerId = "none",
                    isNegative = true,
                    cachedAtEpochMs = now,
                ),
                now = now,
            )
        }

        Result.failure(lastError ?: StreamingError.Unavailable("Lirik tidak tersedia"))
    }

    private suspend fun persistCacheAndPrune(entry: LyricsCacheEntity, now: Long) {
        runCatching {
            cacheDao.upsert(entry)
            cacheDao.deleteOlderThan(now - POSITIVE_CACHE_TTL_MS)
            val total = cacheDao.count()
            if (total > MAX_CACHE_ENTRIES) {
                cacheDao.deleteOldest(total - MAX_CACHE_ENTRIES)
            }
        }
    }

    companion object {
        const val POSITIVE_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1_000L
        const val NEGATIVE_CACHE_TTL_MS = 24L * 60 * 60 * 1_000L
        const val MAX_CACHE_ENTRIES = 500

        fun buildCacheKey(request: LyricsRequest): String {
            if (request.sourceType == AudioSourceType.LOCAL && request.trackId.startsWith("content://")) {
                return "LOCAL|${request.trackId}"
            }
            val cleanTitle = com.xeno.mupa.core.model.TrackDisplayRules.cleanTitleForLyrics(request.title)
                .lowercase(Locale.ROOT)
                .trim()
            val cleanArtist = LrcLibLyricsProvider.cleanArtistForLyrics(request.artistName)
                .lowercase(Locale.ROOT)
                .trim()
            val durationBucket = if (request.durationMs > 0L) request.durationMs / 2_000L else 0L
            return "${request.sourceType.name}|$cleanTitle|$cleanArtist|$durationBucket"
        }

        private fun providerDisplayLabel(providerId: String): String = when (providerId) {
            "local_lrc" -> "File .lrc"
            "local_tag" -> "Tag Audio"
            LrcLibLyricsProvider.PROVIDER_ID -> "LRCLIB"
            else -> providerId
        }
    }
}
