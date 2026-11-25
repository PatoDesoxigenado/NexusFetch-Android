package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaSanitizerTest {

    @Test
    fun `upgrades Twitter media URL to original quality preserving other query params`() {
        val upgraded = MediaSanitizer.upgradeTwitterImageUrlToOrig(
            "https://pbs.twimg.com/media/GXabc123?format=jpg&name=small",
        )
        assertTrue(upgraded.contains("format=jpg"))
        assertTrue(upgraded.contains("name=orig"))
        assertFalse(upgraded.contains("name=small"))
    }

    @Test
    fun `deduplicates carousel URLs by canonical path keeping original order`() {
        val input = listOf(
            "https://cdn.example.com/abc/photo_1.jpg?width=480",
            "https://cdn.example.com/xyz/photo_1.jpg?width=1080",
            "https://cdn.example.com/abc/video_2.mp4?token=123",
            "https://cdn.example.com/abc/photo_3.png",
        )
        val result = MediaSanitizer.deduplicateMediaUrls(input)
        assertEquals(3, result.size)
        assertEquals("https://cdn.example.com/abc/photo_1.jpg?width=480", result[0])
        assertEquals("https://cdn.example.com/abc/video_2.mp4?token=123", result[1])
        assertEquals("https://cdn.example.com/abc/photo_3.png", result[2])
    }

    @Test
    fun `sanitizes path traversal and illegal filesystem characters`() {
        assertEquals("etc_passwd", MediaSanitizer.sanitizeFilename("../../etc/passwd"))
        assertEquals("video_legal_test", MediaSanitizer.sanitizeFilename("video:legal*test?"))
        assertEquals("download", MediaSanitizer.sanitizeFilename("..."))
    }

    @Test
    fun `redacts sensitive query parameters and private paths from logs`() {
        val raw = "Failed GET https://api.site.com/dl?token=secret123&sig=abc /data/user/0/pkg/cache/foo.mp4"
        val redacted = MediaSanitizer.redactSensitiveText(raw)
        assertFalse(redacted.contains("secret123"))
        assertFalse(redacted.contains("=abc"))
        assertFalse(redacted.contains("/data/user/0"))
        assertTrue(redacted.contains("<REDACTED>"))
        assertTrue(redacted.contains("<APP_PRIVATE_DIR>"))
    }

    @Test
    fun `infers extensions and formats bytes and duration`() {
        assertEquals(".jpg", MediaSanitizer.inferExtension("https://pbs.twimg.com/media/a.jpeg?name=orig", MediaType.IMAGE))
        assertEquals(".mp4", MediaSanitizer.inferExtension("https://cdn.example.com/stream", MediaType.VIDEO))
        assertEquals("1.5 MB", MediaSanitizer.formatBytes(1572864L))
        assertEquals("01:02:05", MediaSanitizer.formatDuration(3725))
    }
}
