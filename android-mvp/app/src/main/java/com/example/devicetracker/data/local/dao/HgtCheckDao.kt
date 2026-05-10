package com.example.devicetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.devicetracker.data.local.entity.HgtCheckEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HgtCheckDao {

    @Query("SELECT COUNT(*) FROM hgt_checks")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM hgt_checks WHERE syncStatus = :syncStatus")
    suspend fun countBySyncStatus(syncStatus: String): Int

    @Query("SELECT * FROM hgt_checks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HgtCheckEntity?

    @Query("SELECT * FROM hgt_checks ORDER BY maThietBi ASC")
    suspend fun getAll(): List<HgtCheckEntity>

    @Query("SELECT * FROM hgt_checks WHERE syncStatus = 'PENDING' ORDER BY updatedAt DESC")
    suspend fun getPendingChecks(): List<HgtCheckEntity>

    @Query(
        """
        SELECT * FROM hgt_checks
        WHERE maThietBi LIKE '%' || :query || '%'
        ORDER BY maThietBi ASC
        """
    )
    fun observeByDeviceCode(query: String): Flow<List<HgtCheckEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HgtCheckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<HgtCheckEntity>)

    @Query("DELETE FROM hgt_checks WHERE id = :id")
    suspend fun deleteById(id: String)
}
