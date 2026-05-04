package com.example.devicetracker.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.local.entity.DeviceLogEntity
import com.example.devicetracker.data.local.entity.HgtCheckEntity
import com.example.devicetracker.data.local.entity.SyncQueueEntity

@Database(
    entities = [DeviceLogEntity::class, SyncQueueEntity::class, HgtCheckEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun deviceLogDao(): DeviceLogDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun hgtCheckDao(): HgtCheckDao
}
