package io.github.patodesoxigenado.nexusfetch.domain.model

import io.github.patodesoxigenado.nexusfetch.domain.policy.MediaSanitizer

/**
 * Metadata describing a local video or audio file chosen by the user.
 */
data class MediaFileInfo(
    val uriString: String,
    val name: String,
    val sizeBytes: Long,
    val isVideo: Boolean,
    val isAudio: Boolean,
    val ext: String,
    val durationSec: Double,
    val width: Int? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val fps: Double? = null,
) {
    val sizeStr: String
        get() = MediaSanitizer.formatBytes(sizeBytes)

    val durationStr: String
        get() = if (durationSec > 0.0) MediaSanitizer.formatDuration(durationSec.toInt()) else "--:--"

    val resolutionStr: String
        get() = if (width != null && height != null) "${width}x$height" else "N/A"
}
