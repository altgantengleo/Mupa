package com.xeno.mupa

import com.xeno.mupa.core.model.GlassStyle
import com.xeno.mupa.core.model.UserPreferences
import com.xeno.mupa.data.backup.MupaBackupCodec
import com.xeno.mupa.data.lyrics.LrcParser
import com.xeno.mupa.playback.eq.EqPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Stage4IntegrationTest {

    @Test
    fun lrcParserHandlesSyncedMultiTimestampsOffsetAndPlainFallback() {
        val raw = """
            [offset:500]
            [00:01.00][00:10.50]Baris berulang
            [00:05.25]Baris tengah
        """.trimIndent()

        val lines = LrcParser.parse(raw)
        assertEquals(3, lines.size)
        assertEquals(1_500L, lines[0].timestampMs)
        assertEquals("Baris berulang", lines[0].text)
        assertEquals(5_750L, lines[1].timestampMs)
        assertEquals("Baris tengah", lines[1].text)
        assertEquals(11_000L, lines[2].timestampMs)

        val plain = LrcParser.parse("Baris satu\nBaris dua")
        assertEquals(2, plain.size)
        assertEquals("Baris satu", plain[0].text)
    }

    @Test
    fun backupCodecRoundTripsPreferencesAndEqWithoutSecrets() {
        val prefs = UserPreferences(
            onboardingCompleted = true,
            favoriteArtistIds = setOf("art-hindia", "art-nadin", "art-tulus"),
            glassStyle = GlassStyle.CRYSTAL,
            isOledMode = true,
            isAuroraEnabled = false,
            isPerformanceMode = true,
            stripArtistName = false,
        )
        val json = MupaBackupCodec.exportJson(
            preferences = prefs,
            eqPreset = EqPreset.BASS,
            eqGainsDb = EqPreset.BASS.gainsDb,
            nowEpochMs = 1_750_000_000_000L,
        )

        // Pastikan tidak ada field rahasia/cookie/token
        assertFalse(json.contains("cookie", ignoreCase = true))
        assertFalse(json.contains("token", ignoreCase = true))
        assertFalse(json.contains("password", ignoreCase = true))

        val restored = MupaBackupCodec.importJson(json).getOrThrow()
        assertEquals(prefs.favoriteArtistIds, restored.preferences.favoriteArtistIds)
        assertEquals(GlassStyle.CRYSTAL, restored.preferences.glassStyle)
        assertTrue(restored.preferences.isOledMode)
        assertEquals(EqPreset.BASS, restored.eqPreset)
        assertEquals(EqPreset.BASS.gainsDb, restored.eqGainsDb)
    }

    @Test
    fun backupCodecRejectsInvalidSchemaOrOversizedPayload() {
        val wrongApp = MupaBackupCodec.importJson("""{"app":"other","schemaVersion":1}""")
        assertTrue(wrongApp.isFailure)

        val broken = MupaBackupCodec.importJson("{bad")
        assertTrue(broken.isFailure)
    }
}
