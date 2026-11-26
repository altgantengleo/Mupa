package com.xeno.mupa.streaming.innertube

import com.xeno.mupa.streaming.api.AudioQualityPreference
import com.xeno.mupa.streaming.api.StreamingError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InnerTubeParserFixtureTest {

    @Test
    fun parsesPlayerAdaptiveFormatsAndSelectsHighestBitrateOpus() {
        val fixture = """
            {
              "playabilityStatus": { "status": "OK" },
              "streamingData": {
                "expiresInSeconds": "21540",
                "adaptiveFormats": [
                  {
                    "itag": 140,
                    "url": "https://rr1.example.test/audio140",
                    "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                    "bitrate": 129000
                  },
                  {
                    "itag": 251,
                    "url": "https://rr1.example.test/audio251",
                    "mimeType": "audio/webm; codecs=\"opus\"",
                    "bitrate": 132000
                  }
                ]
              }
            }
        """.trimIndent()

        val parsed = InnerTubeParser.parsePlayerResponse(
            rawJson = fixture,
            quality = AudioQualityPreference.HIGH,
            nowEpochMs = 1_000_000L,
        ).getOrThrow()

        val stream = parsed.stream!!
        assertEquals("https://rr1.example.test/audio251", stream.url)
        assertEquals("audio/webm", stream.mimeType)
        assertEquals(132, stream.bitrateKbps)
        assertEquals(1_000_000L + 21_540_000L, stream.expiresAtEpochMs)
    }

    @Test
    fun mapsBotCheckPlayabilityToTypedBotCheckError() {
        val fixture = """
            {
              "playabilityStatus": {
                "status": "LOGIN_REQUIRED",
                "reason": "Sign in to confirm you're not a bot"
              }
            }
        """.trimIndent()

        val result = InnerTubeParser.parsePlayerResponse(fixture)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StreamingError.BotCheck)
    }

    @Test
    fun parsesSearchSongsFixtureTolerantlyWithoutCrashingOnExtraFields() {
        val fixture = """
            {
              "contents": {
                "tabbedSearchResultsRenderer": {
                  "tabs": [
                    {
                      "tabRenderer": {
                        "content": {
                          "sectionListRenderer": {
                            "contents": [
                              {
                                "musicShelfRenderer": {
                                  "contents": [
                                    {
                                      "musicResponsiveListItemRenderer": {
                                        "playlistItemData": { "videoId": "dQw4w9WgXcQ" },
                                        "flexColumns": [
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Evaluasi" } ] } } },
                                          { "musicResponsiveListItemFlexColumnRenderer": { "text": { "runs": [ { "text": "Hindia" }, { "text": " • " }, { "text": "3:34" } ] } } }
                                        ],
                                        "thumbnail": {
                                          "musicThumbnailRenderer": {
                                            "thumbnail": {
                                              "thumbnails": [ { "url": "https://img.example.test/cover.jpg" } ]
                                            }
                                          }
                                        }
                                      }
                                    }
                                  ]
                                }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val tracks = InnerTubeParser.parseSearchTracks(fixture).getOrThrow()
        assertEquals(1, tracks.size)
        assertEquals("dQw4w9WgXcQ", tracks[0].id)
        assertEquals("Evaluasi", tracks[0].title)
        assertEquals("Hindia", tracks[0].artistName)
        assertEquals(214_000L, tracks[0].durationMs)
    }

    @Test
    fun handlesMalformedJsonWithParseErrorInsteadOfCrash() {
        val result = InnerTubeParser.parseSearchTracks("{broken-json")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is StreamingError.ParseError)
    }

    @Test
    fun upscalesGoogleThumbnailUrlsToHdAndExtractsVisitorData() {
        val lowRes = "https://yt3.googleusercontent.com/abc123=w60-h60-p-l90-rj"
        val hd = InnerTubeParser.upscaleArtworkUrl(lowRes)
        assertEquals("https://yt3.googleusercontent.com/abc123=w544-h544-p-l90-rj", hd)

        val visitorJson = """{"responseContext":{"visitorData":"CgtVisitor12345"}}"""
        assertEquals("CgtVisitor12345", InnerTubeParser.extractVisitorData(visitorJson))
    }
}
