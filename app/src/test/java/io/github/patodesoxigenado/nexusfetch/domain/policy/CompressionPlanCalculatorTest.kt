package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CompressionPlanCalculatorTest {

    private fun sampleVideo(sizeBytes: Long, durationSec: Double, height: Int? = 1080) = MediaFileInfo(
        uriString = "content://media/1",
        name = "clip.mp4",
        sizeBytes = sizeBytes,
        isVideo = true,
        isAudio = false,
        ext = ".mp4",
        durationSec = durationSec,
        width = 1920,
        height = height,
    )

    @Test
    fun `skips compression when file is already within preset limit`() {
        val file = sampleVideo(sizeBytes = 8L * 1024 * 1024, durationSec = 30.0)
        val plan = CompressionPlanCalculator.calculate(file, CompressionPreset.DISCORD_10)
        assertTrue(plan.alreadyWithinLimit)
    }

    @Test
    fun `downscales to 480p 30fps and raises low quality warning for long video on 10MB preset`() {
        // 600s (~10 min) target 9.4 MB -> totalKbps ~ 122 -> videoKbps=120 (<280)
        val file = sampleVideo(sizeBytes = 90L * 1024 * 1024, durationSec = 600.0, height = 1080)
        val plan = CompressionPlanCalculator.calculate(file, CompressionPreset.DISCORD_10)
        assertFalse(plan.alreadyWithinLimit)
        assertEquals(64, plan.audioKbps)
        assertEquals(120, plan.videoKbps)
        assertEquals(480, plan.scaleHeight)
        assertEquals(30, plan.targetFps)
        assertTrue(plan.lowQualityWarning)
    }

    @Test
    fun `uses 720p 30fps and 96kbps audio for medium budget and never upscales smaller source`() {
        // 120s target 15.2 MB (WhatsApp 16MB) -> totalKbps = (15.2*1024*1024*8 / 120 / 1000 * 0.94) ≈ 998 -> audio=128, video≈870
        val hdFile = sampleVideo(sizeBytes = 50L * 1024 * 1024, durationSec = 120.0, height = 1080)
        val hdPlan = CompressionPlanCalculator.calculate(hdFile, CompressionPreset.WHATSAPP_16)
        assertEquals(128, hdPlan.audioKbps)
        assertEquals(720, hdPlan.scaleHeight)
        assertEquals(30, hdPlan.targetFps)
        assertFalse(hdPlan.lowQualityWarning)

        // Source already 480p -> must NOT upscale to 720p
        val sdFile = sampleVideo(sizeBytes = 50L * 1024 * 1024, durationSec = 120.0, height = 480)
        val sdPlan = CompressionPlanCalculator.calculate(sdFile, CompressionPreset.WHATSAPP_16)
        assertNull(sdPlan.scaleHeight)
    }

    @Test
    fun `calculates audio-only mp3 plan within 48 to 320 kbps`() {
        val wavFile = MediaFileInfo(
            uriString = "content://media/2",
            name = "podcast.wav",
            sizeBytes = 80L * 1024 * 1024,
            isVideo = false,
            isAudio = true,
            ext = ".wav",
            durationSec = 1200.0,
        )
        val plan = CompressionPlanCalculator.calculate(wavFile, CompressionPreset.DISCORD_10)
        assertFalse(plan.alreadyWithinLimit)
        assertFalse(plan.isVideo)
        assertEquals(".mp3", plan.outputExt)
        assertTrue(plan.audioKbps in 48..320)
    }

    @Test
    fun `handles 4K AV1 videos and MP3 samples matching arquivos-teste profiles`() {
        // 01 - Meliodas (211.22 MB, 218.161s, 3840x2160) -> Discord 10 MB
        val vid1 = MediaFileInfo(
            uriString = "content://media/10",
            name = "01 - Meliodas.mp4",
            sizeBytes = 221_483_113L,
            isVideo = true,
            isAudio = false,
            ext = ".mp4",
            durationSec = 218.161,
            width = 3840,
            height = 2160,
        )
        val planVid1Discord = CompressionPlanCalculator.calculate(vid1, CompressionPreset.DISCORD_10)
        assertFalse(planVid1Discord.alreadyWithinLimit)
        assertEquals(".mp4", planVid1Discord.outputExt)
        assertEquals(480, planVid1Discord.scaleHeight)
        assertEquals(30, planVid1Discord.targetFps)
        assertEquals(64, planVid1Discord.audioKbps)
        assertTrue(planVid1Discord.videoKbps >= 120)

        // 02 - Loki (383.03 MB, 252.361s, 3840x2160) -> WhatsApp 16 MB
        val vid2 = MediaFileInfo(
            uriString = "content://media/11",
            name = "02 - Loki.mp4",
            sizeBytes = 401_636_015L,
            isVideo = true,
            isAudio = false,
            ext = ".mp4",
            durationSec = 252.361,
            width = 3840,
            height = 2160,
        )
        val planVid2Wa = CompressionPlanCalculator.calculate(vid2, CompressionPreset.WHATSAPP_16)
        assertFalse(planVid2Wa.alreadyWithinLimit)
        assertEquals(480, planVid2Wa.scaleHeight)
        assertTrue(planVid2Wa.videoKbps >= 280)

        // 02 - Loki.mp3 (9.63 MB, 252.34s) -> Discord 10 MB (already within limit)
        val audSmall = MediaFileInfo(
            uriString = "content://media/12",
            name = "02 - Loki.mp3",
            sizeBytes = 10_098_594L,
            isVideo = false,
            isAudio = true,
            ext = ".mp3",
            durationSec = 252.342,
        )
        assertTrue(CompressionPlanCalculator.calculate(audSmall, CompressionPreset.DISCORD_10).alreadyWithinLimit)

        // 03 - Itoshi Rin.mp3 (12.74 MB, 333.75s) -> Discord 10 MB (must compress to ~213 kbps)
        val audLarge = MediaFileInfo(
            uriString = "content://media/13",
            name = "03 - Itoshi Rin.mp3",
            sizeBytes = 13_354_988L,
            isVideo = false,
            isAudio = true,
            ext = ".mp3",
            durationSec = 333.752,
        )
        val planAudLarge = CompressionPlanCalculator.calculate(audLarge, CompressionPreset.DISCORD_10)
        assertFalse(planAudLarge.alreadyWithinLimit)
        assertEquals(".mp3", planAudLarge.outputExt)
        assertTrue(planAudLarge.audioKbps in 180..256)
    }

    @Test
    fun `never upsamples 24fps source to 30fps while still capping 60fps when budget is tight`() {
        val anny24fps = MediaFileInfo(
            uriString = "content://media/anny",
            name = "ANNY - PARABELLUM.mp4",
            sizeBytes = 45_797_750L,
            isVideo = true,
            isAudio = false,
            ext = ".mp4",
            durationSec = 187.84,
            width = 1920,
            height = 1080,
            fps = 23.976,
        )
        val planAnny = CompressionPlanCalculator.calculate(anny24fps, CompressionPreset.DISCORD_10)
        assertFalse(planAnny.alreadyWithinLimit)
        assertEquals(480, planAnny.scaleHeight)
        assertNull(planAnny.targetFps)

        val clip60fps = anny24fps.copy(fps = 60.0)
        val plan60 = CompressionPlanCalculator.calculate(clip60fps, CompressionPreset.DISCORD_10)
        assertEquals(30, plan60.targetFps)
    }
}
