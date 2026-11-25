package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Computed bitrate, resolution, FPS, and container plan for compressing a media file.
 */
data class CompressionPlan(
    val alreadyWithinLimit: Boolean,
    val isVideo: Boolean,
    val targetBytes: Long,
    val limitBytes: Long,
    val videoKbps: Int,
    val audioKbps: Int,
    val scaleHeight: Int?,
    val outputExt: String,
    val targetFps: Int? = null,
    val lowQualityWarning: Boolean = false,
)
