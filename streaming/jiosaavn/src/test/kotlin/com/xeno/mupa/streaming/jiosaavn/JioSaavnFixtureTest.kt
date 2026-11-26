package com.xeno.mupa.streaming.jiosaavn

import com.xeno.mupa.streaming.api.AudioQualityPreference
import com.xeno.mupa.streaming.api.StreamingError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JioSaavnFixtureTest {

    @Test
    fun parsesFixtureAndDecryptsUrlToRequestedBitrate() {
        val encrypted = JioSaavnParser.encryptForTest("http://aac.saavncdn.example/track_96.mp4")
        val fixture = """
            {
              "results": [
                {
                  "id": "saavn-1",
                  "title": "Test Song",
                  "more_info": {
                    "encrypted_media_url": "$encrypted"
                  }
                }
              ]
            }
        """.trimIndent()

        val high = JioSaavnParser.parseBestStream(fixture, AudioQualityPreference.HIGH).getOrThrow()
        assertEquals("https://aac.saavncdn.example/track_320.mp4", high.url)
        assertEquals(320, high.bitrateKbps)

        val med = JioSaavnParser.parseBestStream(fixture, AudioQualityPreference.MEDIUM).getOrThrow()
        assertEquals("https://aac.saavncdn.example/track_160.mp4", med.url)
        assertEquals(160, med.bitrateKbps)
    }

    @Test
    fun emptyResultsReturnTypedUnavailableError() {
        val result = JioSaavnParser.parseBestStream("""{"results": []}""")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StreamingError.Unavailable)
    }
}
