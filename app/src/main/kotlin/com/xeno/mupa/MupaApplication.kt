package com.xeno.mupa

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MupaApplication : Application() {

    @Inject
    lateinit var okHttpClient: OkHttpClient

    val isLowRamDevice: Boolean
        get() {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return am?.isLowRamDevice == true
        }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && ::okHttpClient.isInitialized) {
            // Evict idle HTTP sockets on memory pressure without disrupting active Media3 playback.
            okHttpClient.connectionPool.evictAll()
        }
    }
}
