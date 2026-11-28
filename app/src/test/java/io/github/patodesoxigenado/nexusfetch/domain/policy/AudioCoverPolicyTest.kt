package io.github.patodesoxigenado.nexusfetch.domain.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AudioCoverPolicyTest {

    @Test
    fun `selects ordered YouTube and YouTube Music thumbnail candidates and ignores other platforms`() {
        val ytCandidates = AudioCoverPolicy.buildThumbnailCandidates(
            sourceUrl = "https://music.youtube.com/watch?v=dQw4w9WgXcQ&list=RD1",
            fallbackThumbnailUrl = "https://lh3.googleusercontent.com/cover_art=w544-h544",
        )
        assertEquals(
            listOf(
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxresdefault.jpg",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg",
                "https://lh3.googleusercontent.com/cover_art=w544-h544",
                "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
            ),
            ytCandidates,
        )

        val shortCandidates = AudioCoverPolicy.buildThumbnailCandidates(
            sourceUrl = "https://youtu.be/dQw4w9WgXcQ",
            fallbackThumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
        )
        assertEquals(3, shortCandidates.size)
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", shortCandidates.last())

        assertTrue(
            AudioCoverPolicy.buildThumbnailCandidates(
                sourceUrl = "https://www.instagram.com/p/DgJgmzgPF0L/",
                fallbackThumbnailUrl = "https://scontent.cdninstagram.com/v/photo.jpg",
            ).isEmpty(),
        )
    }

    @Test
    fun `computes exact 720x720 center square crop for 16_9 YouTube Music thumbnails`() {
        val rect = AudioCoverPolicy.computeCoverCrop(width = 1280, height = 720)
        assertEquals(CoverCropRect(left = 280, top = 0, width = 720, height = 720), rect)
    }

    @Test
    fun `strips 4_3 black letterbox bars before square crop while preserving bright 4_3 images`() {
        assertTrue(AudioCoverPolicy.isApprox43Ratio(480, 360))
        assertEquals(45, AudioCoverPolicy.letterboxBandHeight(360))

        val darkLetterbox = AudioCoverPolicy.computeCoverCrop(
            width = 480,
            height = 360,
            topBandMeanLuma = 1.5,
            bottomBandMeanLuma = 2.0,
        )
        assertEquals(CoverCropRect(left = 105, top = 45, width = 270, height = 270), darkLetterbox)

        val bright43 = AudioCoverPolicy.computeCoverCrop(
            width = 480,
            height = 360,
            topBandMeanLuma = 120.0,
            bottomBandMeanLuma = 110.0,
        )
        assertEquals(CoverCropRect(left = 60, top = 0, width = 360, height = 360), bright43)
    }

    @Test
    fun `handles already square and portrait thumbnails cleanly`() {
        assertFalse(AudioCoverPolicy.isApprox43Ratio(600, 600))
        assertEquals(
            CoverCropRect(left = 0, top = 0, width = 600, height = 600),
            AudioCoverPolicy.computeCoverCrop(width = 600, height = 600),
        )
        assertEquals(
            CoverCropRect(left = 0, top = 180, width = 720, height = 720),
            AudioCoverPolicy.computeCoverCrop(width = 720, height = 1080),
        )
    }

    @Test
    fun `builds FFmpeg ID3v2_3 attached_pic copy command args`() {
        val args = AudioCoverPolicy.buildFfmpegEmbedArgs(
            mp3Path = "/tmp/song.mp3",
            coverJpgPath = "/tmp/cover.jpg",
            outputMp3Path = "/tmp/out.mp3",
        )
        assertEquals(
            listOf(
                "-y",
                "-i", "/tmp/song.mp3",
                "-i", "/tmp/cover.jpg",
                "-map", "0:a",
                "-map", "1:0",
                "-c", "copy",
                "-id3v2_version", "3",
                "-metadata:s:v", "title=Album cover",
                "-metadata:s:v", "comment=Cover (front)",
                "/tmp/out.mp3",
            ),
            args,
        )
    }
}
