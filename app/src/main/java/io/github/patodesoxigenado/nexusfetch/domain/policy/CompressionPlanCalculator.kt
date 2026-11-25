package io.github.patodesoxigenado.nexusfetch.domain.policy

import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPlan
import io.github.patodesoxigenado.nexusfetch.domain.model.CompressionPreset
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaFileInfo
import kotlin.math.max

/**
 * Pure compression math ported identically from NexusFetch Desktop v1.4.
 */
object CompressionPlanCalculator {

    private const val CONTAINER_OVERHEAD_FACTOR = 0.94
    private const val BYTES_PER_MB = 1024.0 * 1024.0
    private const val LOW_QUALITY_VIDEO_KBPS_THRESHOLD = 280

    fun calculate(fileInfo: MediaFileInfo, preset: CompressionPreset): CompressionPlan {
        val limitBytes = (preset.limitMb * BYTES_PER_MB).toLong()
        val targetBytes = (preset.targetMb * BYTES_PER_MB).toLong()

        if (fileInfo.sizeBytes in 1..limitBytes) {
            return CompressionPlan(
                alreadyWithinLimit = true,
                isVideo = fileInfo.isVideo,
                targetBytes = targetBytes,
                limitBytes = limitBytes,
                videoKbps = 0,
                audioKbps = 0,
                scaleHeight = null,
                outputExt = fileInfo.ext.ifBlank { if (fileInfo.isVideo) ".mp4" else ".mp3" },
            )
        }

        val duration = max(1.0, if (fileInfo.durationSec > 0.0) fileInfo.durationSec else 1.0)
        // 6% margin reserved for MP4/MP3 container muxing overhead
        val totalKbps = ((targetBytes * 8.0) / duration / 1000.0 * CONTAINER_OVERHEAD_FACTOR).toInt()

        if (!fileInfo.isVideo) {
            val audioKbps = totalKbps.coerceIn(48, 320)
            return CompressionPlan(
                alreadyWithinLimit = false,
                isVideo = false,
                targetBytes = targetBytes,
                limitBytes = limitBytes,
                videoKbps = 0,
                audioKbps = audioKbps,
                scaleHeight = null,
                outputExt = ".mp3",
            )
        }

        val audioKbps = when {
            totalKbps >= 900 -> 128
            totalKbps >= 450 -> 96
            else -> 64
        }

        val videoKbps = max(120, totalKbps - audioKbps)
        val lowQualityWarning = videoKbps < LOW_QUALITY_VIDEO_KBPS_THRESHOLD

        // Adaptive downscale + 30 FPS cap when bitrate budget is tight
        var scaleHeight: Int? = null
        var targetFps: Int? = null
        when {
            videoKbps < 550 -> {
                scaleHeight = 480
                targetFps = 30
            }
            videoKbps < 1400 -> {
                scaleHeight = 720
                targetFps = 30
            }
            videoKbps < 3500 -> {
                scaleHeight = 1080
            }
        }

        // Never upscale above the source height
        if (scaleHeight != null && fileInfo.height != null && fileInfo.height <= scaleHeight) {
            scaleHeight = null
        }

        // Never upsample FPS above the source frame rate (avoids wasted encoding work)
        if (targetFps != null && fileInfo.fps != null && fileInfo.fps > 0.0 && fileInfo.fps <= targetFps + 1.0) {
            targetFps = null
        }

        return CompressionPlan(
            alreadyWithinLimit = false,
            isVideo = true,
            targetBytes = targetBytes,
            limitBytes = limitBytes,
            videoKbps = videoKbps,
            audioKbps = audioKbps,
            scaleHeight = scaleHeight,
            outputExt = ".mp4",
            targetFps = targetFps,
            lowQualityWarning = lowQualityWarning,
        )
    }
}
