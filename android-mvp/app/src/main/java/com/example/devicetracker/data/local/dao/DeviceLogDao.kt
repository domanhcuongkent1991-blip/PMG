package com.example.devicetracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceLogDao {

    @Query("SELECT COUNT(*) FROM device_logs")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM device_logs WHERE syncStatus = :syncStatus")
    suspend fun countBySyncStatus(syncStatus: String): Int

    @Query("SELECT * FROM device_logs ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeviceLogEntity>>

    @Query(
        """
        SELECT * FROM device_logs
        WHERE maThietBi LIKE '%' || :deviceCode || '%'
        AND (
            :filter = 'ALL' OR
            (:filter = 'REPAIRED' AND ngaySuaChua IS NOT NULL AND ngaySuaChua != '') OR
            (:filter = 'PENDING' AND (ngaySuaChua IS NULL OR ngaySuaChua = ''))
        )
        ORDER BY updatedAt DESC
        """
    )
    fun observeByDeviceCode(deviceCode: String, filter: String): Flow<List<DeviceLogEntity>>

    @Query("SELECT * FROM device_logs WHERE recordId = :recordId LIMIT 1")
    suspend fun getById(recordId: String): DeviceLogEntity?

    @Query("SELECT * FROM device_logs WHERE sourceSheetId = :sourceSheetId AND maThietBi = :deviceCode")
    suspend fun getBySourceSheetAndDeviceCode(sourceSheetId: Int, deviceCode: String): List<DeviceLogEntity>

    @Query("SELECT * FROM device_logs WHERE maThietBi = :deviceCode")
    suspend fun getByDeviceCode(deviceCode: String): List<DeviceLogEntity>

    @Query("SELECT * FROM device_logs WHERE syncStatus = 'PENDING' ORDER BY updatedAt ASC")
    suspend fun getPendingLogs(): List<DeviceLogEntity>

    @Query("SELECT recordId FROM device_logs")
    suspend fun getAllRecordIds(): List<String>

    @Query("SELECT recordId FROM device_logs WHERE sourceSheetId = :sourceSheetId")
    suspend fun getRecordIdsBySourceSheetId(sourceSheetId: Int): List<String>

    @Query("SELECT recordId FROM device_logs WHERE sourceSheetId = :sourceSheetId AND syncStatus = 'SYNCED'")
    suspend fun getSyncedRecordIdsBySourceSheetId(sourceSheetId: Int): List<String>

    @Query("DELETE FROM device_logs WHERE recordId IN (:recordIds)")
    suspend fun deleteByRecordIds(recordIds: List<String>): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DeviceLogEntity>)
}
