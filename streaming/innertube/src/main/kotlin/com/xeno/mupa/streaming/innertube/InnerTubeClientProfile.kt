package com.xeno.mupa.streaming.innertube

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Konfigurasi klien InnerTube (guest mode — tanpa login/cookie).
 * Ditempatkan terpusat agar mudah diperbarui mengikuti docs/STREAMING.md bila YouTube mengubah versi klien.
 */
data class InnerTubeClientProfile(
    val id: String,
    val clientName: String,
    val clientNameHeaderId: String,
    val clientVersion: String,
    val apiOrigin: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val platform: String = "MOBILE",
) {
    fun buildContextJson(
        hl: String = "id",
        gl: String = "ID",
        visitorData: String? = null,
    ): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", clientName)
            put("clientVersion", clientVersion)
            put("hl", hl)
            put("gl", gl)
            put("platform", platform)
            osName?.let { put("osName", it) }
            osVersion?.let { put("osVersion", it) }
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
        }
    }
}

object InnerTubeProfiles {
    const val YTM_ORIGIN = "https://music.youtube.com"
    const val YT_ORIGIN = "https://www.youtube.com"

    val WEB_REMIX = InnerTubeClientProfile(
        id = "web_remix",
        clientName = "WEB_REMIX",
        clientNameHeaderId = "67",
        clientVersion = "1.20250401.01.00",
        apiOrigin = YTM_ORIGIN,
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:136.0) Gecko/20100101 Firefox/136.0",
        platform = "DESKTOP",
    )

    val ANDROID_VR = InnerTubeClientProfile(
        id = "android_vr",
        clientName = "ANDROID_VR",
        clientNameHeaderId = "28",
        clientVersion = "1.61.48",
        apiOrigin = YT_ORIGIN,
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
        osName = "Android",
        osVersion = "12L",
        platform = "MOBILE",
    )

    val IOS = InnerTubeClientProfile(
        id = "ios",
        clientName = "IOS",
        clientNameHeaderId = "5",
        clientVersion = "20.10.4",
        apiOrigin = YT_ORIGIN,
        userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X)",
        osName = "iOS",
        osVersion = "18.2.0",
        platform = "MOBILE",
    )

    val STREAM_WALK_ORDER: List<InnerTubeClientProfile> = listOf(ANDROID_VR, IOS, WEB_REMIX)
}
