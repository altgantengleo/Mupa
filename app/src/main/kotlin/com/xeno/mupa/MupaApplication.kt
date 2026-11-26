package com.xeno.mupa

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import java.io.File
import javax.inject.Inject

@HiltAndroidApp
class MupaApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    val isLowRamDevice: Boolean
        get() {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return am?.isLowRamDevice == true
        }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            enableDebugStrictMode()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val memPercent = if (isLowRamDevice) 0.12 else 0.20
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, memPercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "coil_artwork_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024L * 1024L)
                    .build()
            }
            .crossfade(180)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            if (::okHttpClient.isInitialized) {
                // Evict idle HTTP sockets on memory pressure without disrupting active Media3 playback.
                okHttpClient.connectionPool.evictAll()
            }
            runCatching {
                SingletonImageLoader.get(this).memoryCache?.clear()
            }
        }
    }

    private fun enableDebugStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
