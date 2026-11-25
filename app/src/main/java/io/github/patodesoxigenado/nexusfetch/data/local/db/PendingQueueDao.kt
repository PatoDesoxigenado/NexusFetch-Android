package io.github.patodesoxigenado.nexusfetch.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingQueueDao {

    @Query("SELECT * FROM pending_queue ORDER BY position ASC")
    fun observeQueue(): Flow<List<PendingQueueEntity>>

    @Query("SELECT * FROM pending_queue ORDER BY position ASC")
    suspend fun getQueueSnapshot(): List<PendingQueueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PendingQueueEntity>)

    @Query("DELETE FROM pending_queue")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceQueue(items: List<PendingQueueEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }
}
