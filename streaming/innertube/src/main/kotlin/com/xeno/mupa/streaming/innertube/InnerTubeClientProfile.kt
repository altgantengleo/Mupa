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
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: Int? = null,
    val clientScreen: String? = null,
    val apiFormatVersion: String? = null,
    val ownVisitorId: Boolean = false,
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
            deviceMake?.let { put("deviceMake", it) }
            deviceModel?.let { put("deviceModel", it) }
            androidSdkVersion?.let { put("androidSdkVersion", it) }
            clientScreen?.let { put("clientScreen", it) }
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
        clientVersion = "1.20260707.12.00",
        apiOrigin = YTM_ORIGIN,
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
        platform = "DESKTOP",
    )

    val VISIONOS = InnerTubeClientProfile(
        id = "visionos",
        clientName = "VISIONOS",
        clientNameHeaderId = "101",
        clientVersion = "1.04",
        apiOrigin = YT_ORIGIN,
        userAgent = "com.google.visionos.youtube/1.04(RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; US)",
        osName = "visionOS",
        osVersion = "26.6.0.23O770",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1",
        clientScreen = "WATCH",
        apiFormatVersion = "2",
        ownVisitorId = true,
        platform = "MOBILE",
    )

    val ANDROID_VR = InnerTubeClientProfile(
        id = "android_vr",
        clientName = "ANDROID_VR",
        clientNameHeaderId = "28",
        clientVersion = "1.61.48",
        apiOrigin = YT_ORIGIN,
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
        osName = "Android",
        osVersion = "12",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = 32,
        ownVisitorId = true,
        platform = "MOBILE",
    )

    val IOS = InnerTubeClientProfile(
        id = "ios",
        clientName = "IOS",
        clientNameHeaderId = "5",
        clientVersion = "21.03.1",
        apiOrigin = YT_ORIGIN,
        userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        osName = "iOS",
        osVersion = "18.2.22C152",
        ownVisitorId = true,
        platform = "MOBILE",
    )

    val STREAM_WALK_ORDER: List<InnerTubeClientProfile> = listOf(VISIONOS, ANDROID_VR, IOS, WEB_REMIX)
}
