package io.github.patodesoxigenado.nexusfetch.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.patodesoxigenado.nexusfetch.domain.model.HistoryEntry
import io.github.patodesoxigenado.nexusfetch.domain.model.JobModule
import io.github.patodesoxigenado.nexusfetch.domain.model.MediaType

@Entity(
    tableName = "history_entries",
    indices = [
        Index(value = ["createdAtEpochMs"]),
        Index(value = ["moduleId"]),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val moduleId: String,
    val mediaTypeId: String,
    val mimeType: String,
    val mediaStoreUri: String,
    val fileName: String,
    val sizeBytes: Long,
    val durationSec: Int? = null,
    val sourceUrl: String? = null,
    val createdAtEpochMs: Long,
) {
    fun toDomain(): HistoryEntry = HistoryEntry(
        id = id,
        title = title,
        module = JobModule.entries.firstOrNull { it.id == moduleId } ?: JobModule.DOWNLOADER,
        mediaType = MediaType.entries.firstOrNull { it.id == mediaTypeId } ?: MediaType.VIDEO,
        mimeType = mimeType,
        mediaStoreUri = mediaStoreUri,
        fileName = fileName,
        sizeBytes = sizeBytes,
        durationSec = durationSec,
        sourceUrl = sourceUrl,
        createdAtEpochMs = createdAtEpochMs,
    )

    companion object {
        fun fromDomain(entry: HistoryEntry): HistoryEntity = HistoryEntity(
            id = entry.id,
            title = entry.title,
            moduleId = entry.module.id,
            mediaTypeId = entry.mediaType.id,
            mimeType = entry.mimeType,
            mediaStoreUri = entry.mediaStoreUri,
            fileName = entry.fileName,
            sizeBytes = entry.sizeBytes,
            durationSec = entry.durationSec,
            sourceUrl = entry.sourceUrl,
            createdAtEpochMs = entry.createdAtEpochMs,
        )
    }
}
