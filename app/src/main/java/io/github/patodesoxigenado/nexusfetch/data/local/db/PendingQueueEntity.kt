package io.github.patodesoxigenado.nexusfetch.data.local.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_queue",
    indices = [Index(value = ["position"])],
)
data class PendingQueueEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val rawUrl: String,
    val platformId: String? = null,
    val title: String? = null,
    val mediaTypeId: String? = null,
    val selectedFormatKey: String? = null,
    val statusName: String,
    val updatedAtEpochMs: Long,
)
