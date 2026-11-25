package io.github.patodesoxigenado.nexusfetch.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_entries ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_entries WHERE moduleId = :moduleId ORDER BY createdAtEpochMs DESC")
    fun observeByModule(moduleId: String): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearAll()
}
