package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.Platform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UrlValidatorTest {

    @Test
    fun `detects YouTube, Instagram, and Twitter URLs automatically`() {
        assertEquals(Platform.YOUTUBE, UrlValidator.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, UrlValidator.detectPlatform("youtu.be/dQw4w9WgXcQ"))
        assertEquals(Platform.YOUTUBE, UrlValidator.detectPlatform("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(Platform.INSTAGRAM, UrlValidator.detectPlatform("https://www.instagram.com/reel/C8abc123XYZ/"))
        assertEquals(Platform.INSTAGRAM, UrlValidator.detectPlatform("https://www.instagram.com/p/J2HGVL/?img_index=1"))
        assertEquals(Platform.INSTAGRAM, UrlValidator.detectPlatform("https://www.instagram.com/einerd.com.br/p/ABC_123-xy/"))
        assertEquals(Platform.TWITTER, UrlValidator.detectPlatform("https://x.com/user_name/status/1823456789012345678"))
    }

    @Test
    fun `returns CROSS_MATCH when validating URL against a different platform`() {
        val result = UrlValidator.validateUrl(
            rawUrl = "https://x.com/nasa/status/123456789",
            currentPlatform = Platform.YOUTUBE,
        )
        assertEquals(ValidationStatus.CROSS_MATCH, result.status)
        assertEquals(Platform.TWITTER, result.suggestedPlatform)
    }

    @Test
    fun `rejects non-https dangerous schemes and unsupported domains`() {
        assertEquals("", UrlValidator.cleanUrl("file:///sdcard/secret.txt"))
        assertEquals("", UrlValidator.cleanUrl("content://media/external/video/media/1"))
        assertEquals("", UrlValidator.cleanUrl("https://evil.example.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("https://youtube.com/watch?v=dQw4w9WgXcQ", UrlValidator.cleanUrl("http://youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun `detects playlists accurately`() {
        assertTrue(UrlValidator.isPlaylistUrl("https://www.youtube.com/playlist?list=PL1234567890"))
        assertTrue(UrlValidator.isPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PL1234567890"))
        assertFalse(UrlValidator.isPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
}
