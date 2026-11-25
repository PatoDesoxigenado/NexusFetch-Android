package io.github.patodesoxigenado.nexusfetch.data.engine

import io.github.patodesoxigenado.nexusfetch.domain.model.ConversionSpec
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import io.github.patodesoxigenado.nexusfetch.domain.model.Mp4Codec
import io.github.patodesoxigenado.nexusfetch.domain.model.ResolutionPreset
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MediaConverterPolicyTest {

    private val annySample = MediaFileInfo(
        uriString = "content://media/anny",
        name = "ANNY - PARABELLUM.mp4",
        sizeBytes = 45_797_750L,
        isVideo = true,
        isAudio = false,
        ext = ".mp4",
        durationSec = 187.84,
        width = 1920,
        height = 1080,
        vcodec = "av1",
        acodec = "opus",
        fps = 23.976,
    )

    @Test
    fun `allows instant lossless remux to MKV at original resolution and disallows on resize`() {
        val toMkvOriginal = ConversionSpec(
            targetExtension = "mkv",
            isAudioTarget = false,
            resolution = ResolutionPreset.ORIGINAL,
            mp4Codec = Mp4Codec.H264,
        )
        assertTrue(MediaConverterEngine.canRemuxLosslessly(annySample, toMkvOriginal))

        val toMkv720p = toMkvOriginal.copy(resolution = ResolutionPreset.P720)
        assertFalse(MediaConverterEngine.canRemuxLosslessly(annySample, toMkv720p))

        val toWebm = toMkvOriginal.copy(targetExtension = "webm")
        assertFalse(MediaConverterEngine.canRemuxLosslessly(annySample, toWebm))

        val toAudio = ConversionSpec(targetExtension = "mp3", isAudioTarget = true)
        assertFalse(MediaConverterEngine.canRemuxLosslessly(annySample, toAudio))
    }

    @Test
    fun `allows MKV to MP4 remux only when codecs match requested MP4 codec`() {
        val h264Mkv = annySample.copy(name = "clip.mkv", ext = ".mkv", vcodec = "h264", acodec = "aac")
        val mp4H264Spec = ConversionSpec(
            targetExtension = "mp4",
            isAudioTarget = false,
            resolution = ResolutionPreset.ORIGINAL,
            mp4Codec = Mp4Codec.H264,
        )
        assertTrue(MediaConverterEngine.canRemuxLosslessly(h264Mkv, mp4H264Spec))
        assertFalse(MediaConverterEngine.canRemuxLosslessly(h264Mkv, mp4H264Spec.copy(mp4Codec = Mp4Codec.HEVC)))
    }
}
