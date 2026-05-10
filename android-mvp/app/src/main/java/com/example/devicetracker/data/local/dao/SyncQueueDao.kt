package com.example.devicetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.devicetracker.data.local.entity.SyncQueueEntity

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM sync_queue WHERE retryCount > 0 OR (lastError IS NOT NULL AND lastError != '')")
    suspend fun countWithErrors(): Int

    @Query("SELECT lastError FROM sync_queue WHERE lastError IS NOT NULL AND lastError != '' ORDER BY createdAt DESC LIMIT 1")
    suspend fun latestError(): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE recordId = :recordId")
    suspend fun deleteByRecordId(recordId: String)

    @Query(
        "DELETE FROM sync_queue " +
            "WHERE operation = 'UPSERT_LOG' " +
            "AND recordId = :recordId " +
            "AND lastError LIKE 'Ambiguous DMBT fallback key for push:%'"
    )
    suspend fun deleteAmbiguousPushErrorByRecordId(recordId: String): Int
}
