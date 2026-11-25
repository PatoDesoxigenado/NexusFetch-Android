package io.github.patodesoxigenado.nexusfetch.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntity::class, PendingQueueEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NexusDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun pendingQueueDao(): PendingQueueDao
}
