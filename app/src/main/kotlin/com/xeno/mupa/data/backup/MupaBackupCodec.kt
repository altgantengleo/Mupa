package com.xeno.mupa.data.backup

import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.playback.eq.EqPreset
import com.xeno.mupa.playback.eq.FiveBandEqualizerProcessor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MupaBackupPayload(
    val app: String = "mupa",
    val schemaVersion: Int = 1,
    val exportedAtEpochMs: Long = 0L,
    val favoriteArtistIds: List<String> = emptyList(),
    val glassStyle: String = GlassStyle.LIQUID_PLUS.name,
    val isOledMode: Boolean = false,
    val isAuroraEnabled: Boolean = true,
    val isPerformanceMode: Boolean = false,
    val stripArtistName: Boolean = true,
    val eqPreset: String = EqPreset.FLAT.name,
    val eqGainsDb: List<Float> = EqPreset.FLAT.gainsDb,
)

/**
 * Backup & Restore JSON ringan Mupa.
 * Secara ketat TIDAK pernah mengekspor token, cookie, kunci API, atau file audio mentah.
 */
object MupaBackupCodec {
    const val MAX_BACKUP_BYTES = 2 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun exportJson(
        preferences: UserPreferences,
        eqPreset: EqPreset,
        eqGainsDb: List<Float>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): String {
        val payload = MupaBackupPayload(
            app = "mupa",
            schemaVersion = 1,
            exportedAtEpochMs = nowEpochMs,
            favoriteArtistIds = preferences.favoriteArtistIds.sorted(),
            glassStyle = preferences.glassStyle.name,
            isOledMode = preferences.isOledMode,
            isAuroraEnabled = preferences.isAuroraEnabled,
            isPerformanceMode = preferences.isPerformanceMode,
            stripArtistName = preferences.stripArtistName,
            eqPreset = eqPreset.name,
            eqGainsDb = eqGainsDb.take(FiveBandEqualizerProcessor.BAND_COUNT).map {
                it.coerceIn(FiveBandEqualizerProcessor.MIN_GAIN_DB, FiveBandEqualizerProcessor.MAX_GAIN_DB)
            },
        )
        return json.encodeToString(payload)
    }

    data class RestoredBackup(
        val preferences: UserPreferences,
        val eqPreset: EqPreset,
        val eqGainsDb: List<Float>,
    )

    fun importJson(rawJson: String): Result<RestoredBackup> {
        if (rawJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) {
            return Result.failure(IllegalArgumentException("Ukuran berkas cadangan melebihi batas 2 MB."))
        }
        val payload = runCatching { json.decodeFromString<MupaBackupPayload>(rawJson) }
            .getOrElse { return Result.failure(IllegalArgumentException("Format JSON cadangan tidak valid.", it)) }

        if (!payload.app.equals("mupa", ignoreCase = true) || payload.schemaVersion !in 1..1) {
            return Result.failure(IllegalArgumentException("Berkas bukan cadangan Mupa yang didukung."))
        }

        val glass = GlassStyle.entries.firstOrNull { it.name == payload.glassStyle } ?: GlassStyle.LIQUID_PLUS
        val preset = EqPreset.entries.firstOrNull { it.name == payload.eqPreset } ?: EqPreset.FLAT
        val gains = List(FiveBandEqualizerProcessor.BAND_COUNT) { idx ->
            payload.eqGainsDb.getOrElse(idx) { 0f }
                .coerceIn(FiveBandEqualizerProcessor.MIN_GAIN_DB, FiveBandEqualizerProcessor.MAX_GAIN_DB)
        }
        val artists = payload.favoriteArtistIds.filter { it.isNotBlank() }.take(8).toSet()

        return Result.success(
            RestoredBackup(
                preferences = UserPreferences(
                    onboardingCompleted = artists.size >= 2,
                    favoriteArtistIds = artists,
                    glassStyle = glass,
                    isOledMode = payload.isOledMode,
                    isAuroraEnabled = payload.isAuroraEnabled,
                    isPerformanceMode = payload.isPerformanceMode,
                    stripArtistName = payload.stripArtistName,
                ),
                eqPreset = preset,
                eqGainsDb = gains,
            ),
        )
    }
}
