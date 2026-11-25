package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * High-level module category used for ForegroundService types, notifications, and Room history.
 */
enum class JobModule(val id: String, val label: String) {
    DOWNLOADER("downloader", "Downloader"),
    MULTI_DOWNLOAD("multi_download", "Multi-Download"),
    COMPRESSOR("compressor", "Compressor"),
    TRIMMER("trimmer", "Recorte Rápido"),
    CONVERTER("converter", "Conversor"),
}

/**
 * Execution lifecycle of a queued or active background job.
 */
enum class JobStatus {
    IDLE,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
}

/**
 * Published media reference returned after atomic MediaStore persistence.
 */
data class PublishedMedia(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val module: JobModule,
    val mediaType: MediaType,
    val durationSec: Int? = null,
    val title: String = fileName,
)

/**
 * Strongly typed requests dispatched to the ForegroundService JobOrchestrator.
 */
sealed interface MediaJobRequest {
    val jobId: String
    val module: JobModule
    val displayTitle: String

    data class SingleDownload(
        override val jobId: String,
        override val displayTitle: String,
        val metadata: MediaMetadata,
        val mode: String, // "video", "audio", "image", "gif"
        val formatId: String? = null,
        val targetImageExt: String? = null,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.DOWNLOADER
    }

    data class CollectionDownload(
        override val jobId: String,
        override val displayTitle: String,
        val metadata: MediaMetadata,
        val selectedItems: List<PlaylistItem>,
        val mode: String,
        val formatId: String? = null,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.DOWNLOADER
    }

    data class MultiDownloadBatch(
        override val jobId: String,
        override val displayTitle: String,
        val items: List<MultiDownloadItem>,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.MULTI_DOWNLOAD
    }

    data class CompressBatch(
        override val jobId: String,
        override val displayTitle: String,
        val files: List<MediaFileInfo>,
        val preset: CompressionPreset,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.COMPRESSOR
    }

    data class ConvertBatch(
        override val jobId: String,
        override val displayTitle: String,
        val files: List<MediaFileInfo>,
        val spec: ConversionSpec,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.CONVERTER
    }

    data class TrimMedia(
        override val jobId: String,
        override val displayTitle: String,
        val request: TrimRequest,
    ) : MediaJobRequest {
        override val module: JobModule = JobModule.TRIMMER
    }
}

/**
 * Snapshot exposed via StateFlow for active and most recently finished jobs.
 */
data class ActiveJobState(
    val jobId: String? = null,
    val module: JobModule? = null,
    val status: JobStatus = JobStatus.IDLE,
    val title: String = "",
    val statusMessage: String = "",
    val progressPercent: Float = 0f,
    val speedText: String = "",
    val etaText: String = "",
    val completedOutputs: List<PublishedMedia> = emptyList(),
    val errorMessage: String? = null,
)
