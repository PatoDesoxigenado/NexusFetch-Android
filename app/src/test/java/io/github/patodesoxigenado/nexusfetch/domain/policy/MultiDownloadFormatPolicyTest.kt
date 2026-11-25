package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.MediaMetadata
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType
import io.github.patodesoxigenado.nexusfetch.domain.model.VideoQualityOption
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MultiDownloadFormatPolicyTest {

    @Test
    fun `never mixes video formats onto image or gif items`() {
        val imageMeta = MediaMetadata(
            url = "https://x.com/u/status/1",
            title = "Foto",
            uploader = "u",
            mediaType = MediaType.IMAGE,
        )
        val imageOptions = MultiDownloadFormatPolicy.getAllowedOutputOptions(imageMeta)
        assertTrue(imageOptions.all { it.mode == "image" })
        assertFalse(imageOptions.any { it.mode == "video" })

        val gifMeta = MediaMetadata(
            url = "https://x.com/u/status/2",
            title = "GIF",
            uploader = "u",
            mediaType = MediaType.GIF,
        )
        val gifOptions = MultiDownloadFormatPolicy.getAllowedOutputOptions(gifMeta)
        assertTrue(gifOptions.any { it.mode == "gif" })
        assertFalse(gifOptions.any { it.mode == "audio" })
    }

    @Test
    fun `allows mp4 qualities plus mp3 extraction for video items`() {
        val videoMeta = MediaMetadata(
            url = "https://youtube.com/watch?v=12345678901",
            title = "Video",
            uploader = "chan",
            mediaType = MediaType.VIDEO,
            qualities = listOf(VideoQualityOption("2160p (4K)", 2160, "bestvideo[height<=2160]+bestaudio/best")),
        )
        val options = MultiDownloadFormatPolicy.getAllowedOutputOptions(videoMeta)
        assertTrue(options.any { it.mode == "video" && it.label.contains("2160p") })
        assertTrue(options.any { it.mode == "audio" && it.key == "mp3_320" })
    }
}
