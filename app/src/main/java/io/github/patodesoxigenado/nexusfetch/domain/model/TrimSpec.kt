package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Distinguishes whether a trim source comes from a remote URL or a local file URI.
 */
enum class TrimSourceType {
    URL,
    FILE,
}

/**
 * Remote resolution choices for Fast Trim (remote links).
 */
enum class RemoteTrimQuality(val label: String, val maxHeight: Int?) {
    BEST("Melhor Disponível", null),
    P2160("4K (2160p)", 2160),
    P1440("2K (1440p)", 1440),
    P1080("1080p (Full HD)", 1080),
    P720("720p (HD)", 720),
    P480("480p (SD)", 480),
}

/**
 * Analyzed metadata for a trim source (remote URL or local file).
 */
data class TrimSourceInfo(
    val sourceType: TrimSourceType,
    val source: String,
    val title: String,
    val durationSec: Double,
    val isVideo: Boolean = true,
    val uploader: String = "",
    val platform: Platform? = null,
    val fileExt: String = ".mp4",
    val error: String? = null,
)

/**
 * Request payload for executing a trim job.
 */
data class TrimRequest(
    val sourceInfo: TrimSourceInfo,
    val startSec: Double,
    val endSec: Double,
    val audioOnly: Boolean = false,
    val maxHeight: Int? = null,
)
