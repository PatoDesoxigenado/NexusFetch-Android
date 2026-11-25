package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class YouTubeFastMetaParserTest {

    @Test
    fun `extracts videoId from standard shorts live and music URLs`() {
        assertEquals("d6qlphlhfOg", YouTubeFastMetaParser.extractVideoId("https://youtu.be/d6qlphlhfOg"))
        assertEquals("d6qlphlhfOg", YouTubeFastMetaParser.extractVideoId("https://www.youtube.com/watch?v=d6qlphlhfOg&t=12s"))
        assertEquals("AbC_123-xyZ", YouTubeFastMetaParser.extractVideoId("https://youtube.com/shorts/AbC_123-xyZ"))
        assertEquals("AbC_123-xyZ", YouTubeFastMetaParser.extractVideoId("https://music.youtube.com/watch?feature=share&v=AbC_123-xyZ"))
        assertNull(YouTubeFastMetaParser.extractVideoId("https://www.youtube.com/playlist?list=PL123456"))
    }

    @Test
    fun `extracts ytInitialPlayerResponse handling nested braces inside strings`() {
        val html = """
            <html><head><script>
              var ytInitialPlayerResponse = {
                "playabilityStatus": {"status": "OK"},
                "videoDetails": {"videoId": "d6qlphlhfOg", "title": "Song {Remix} \"Live\" } test"},
                "streamingData": {"adaptiveFormats": []}
              }; var meta = {};
            </script></head></html>
        """.trimIndent()

        val extracted = YouTubeFastMetaParser.extractPlayerResponseJson(html)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("Song {Remix}"))
        assertTrue(extracted.trim().endsWith("}"))
    }

    @Test
    fun `parses valid playerResponse into MediaMetadata and rejects non-OK playability`() {
        val playerJson = """
            {
              "playabilityStatus": { "status": "OK" },
              "videoDetails": {
                "videoId": "d6qlphlhfOg",
                "title": "ANNY - PARABELLUM",
                "lengthSeconds": "188",
                "author": "Anny",
                "thumbnail": {
                  "thumbnails": [
                    { "url": "https://i.ytimg.com/vi/d6qlphlhfOg/hqdefault.jpg" },
                    { "url": "https://i.ytimg.com/vi/d6qlphlhfOg/maxresdefault.jpg" }
                  ]
                }
              },
              "streamingData": {
                "adaptiveFormats": [
                  { "height": 2160, "mimeType": "video/webm; codecs=\"vp9\"", "contentLength": "90000000" },
                  { "height": 1080, "mimeType": "video/av01; codecs=\"av01\"", "contentLength": "42700000" },
                  { "height": 720, "mimeType": "video/mp4; codecs=\"avc1\"", "bitrate": 1500000 },
                  { "mimeType": "audio/webm; codecs=\"opus\"", "contentLength": "3041382" }
                ]
              }
            }
        """.trimIndent()

        val meta = YouTubeFastMetaParser.parsePlayerResponse(
            originalUrl = "https://youtu.be/d6qlphlhfOg",
            videoId = "d6qlphlhfOg",
            playerJson = playerJson,
        )
        assertNotNull(meta)
        assertEquals(Platform.YOUTUBE, meta?.platform)
        assertEquals(MediaType.VIDEO, meta?.mediaType)
        assertEquals("ANNY - PARABELLUM", meta?.title)
        assertEquals("Anny", meta?.uploader)
        assertEquals(188, meta?.durationSec)
        assertEquals(3041382L, meta?.estimatedAudioSizeBytes)
        assertEquals("https://i.ytimg.com/vi/d6qlphlhfOg/maxresdefault.jpg", meta?.thumbnailUrl)
        assertEquals(listOf(null, 2160, 1080, 720), meta?.qualities?.map { it.height })

        val loginRequired = """{"playabilityStatus":{"status":"LOGIN_REQUIRED"},"videoDetails":{"title":"X"}}"""
        assertNull(YouTubeFastMetaParser.parsePlayerResponse("https://youtu.be/d6qlphlhfOg", "d6qlphlhfOg", loginRequired))
    }
}
