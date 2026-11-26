package com.xeno.mupa.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Pengelola SimpleCache Media3 untuk cache audio (default 500 MB).
 */
@OptIn(UnstableApi::class)
class MupaAudioCache(
    context: Context,
    maxBytes: Long = PlaybackServiceConfig.DEFAULT_AUDIO_CACHE_BYTES,
) {
    private val cacheDir = File(context.cacheDir, "mupa_audio_cache")
    private val databaseProvider = StandaloneDatabaseProvider(context.applicationContext)

    val simpleCache: SimpleCache = SimpleCache(
        cacheDir,
        LeastRecentlyUsedCacheEvictor(maxBytes.coerceAtLeast(50L * 1024L * 1024L)),
        databaseProvider,
    )

    fun release() {
        runCatching { simpleCache.release() }
    }
}
