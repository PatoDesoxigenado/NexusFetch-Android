package io.github.patodesoxigenado.nexusfetch.domain.model

/**
 * Domain representation of a completed output saved in Room history.
 */
data class HistoryEntry(
    val id: Long = 0L,
    val title: String,
    val module: JobModule,
    val mediaType: MediaType,
    val mimeType: String,
    val mediaStoreUri: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationSec: Int? = null,
    val sourceUrl: String? = null,
    val createdAtEpochMs: Long,
)
