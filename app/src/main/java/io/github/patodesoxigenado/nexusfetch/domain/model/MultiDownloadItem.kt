package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Lifecycle state for an individual link entry in the Multi-Download queue.
 */
enum class MultiItemStatus {
    PENDING_INPUT,
    ANALYZING,
    READY,
    INVALID,
    DOWNLOADING,
    COMPLETED,
    FAILED,
}

/**
 * Valid per-item output choice constrained by the detected media type.
 */
data class OutputFormatOption(
    val key: String,
    val label: String,
    val mode: String, // "video", "audio", "image", "gif"
    val formatId: String? = null,
    val targetImageExt: String? = null,
)

/**
 * Single row in the Multi-Download queue (up to 10 links).
 */
data class MultiDownloadItem(
    val id: String,
    val position: Int,
    val rawUrl: String = "",
    val platform: Platform? = null,
    val metadata: MediaMetadata? = null,
    val allowedFormats: List<OutputFormatOption> = emptyList(),
    val selectedFormat: OutputFormatOption? = null,
    val status: MultiItemStatus = MultiItemStatus.PENDING_INPUT,
    val errorMessage: String? = null,
)
