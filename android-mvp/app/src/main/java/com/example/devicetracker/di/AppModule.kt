package com.example.devicetracker.di

import android.content.Context
import androidx.room.Room
import com.example.devicetracker.data.local.AppDatabase
import com.example.devicetracker.data.local.DatabaseMigrations
import com.example.devicetracker.data.local.dao.DeviceLogDao
import com.example.devicetracker.data.local.dao.HgtCheckDao
import com.example.devicetracker.data.local.dao.SyncQueueDao
import com.example.devicetracker.data.repository.DeviceLogRepositoryImpl
import com.example.devicetracker.data.repository.HgtCheckRepositoryImpl
import com.example.devicetracker.domain.repository.DeviceLogRepository
import com.example.devicetracker.domain.repository.HgtCheckRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "device_tracker.db"
        )
            .addMigrations(DatabaseMigrations.MIGRATION_1_2)
            .addMigrations(DatabaseMigrations.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideDeviceLogDao(database: AppDatabase): DeviceLogDao = database.deviceLogDao()

    @Provides
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao = database.syncQueueDao()

    @Provides
    fun provideHgtCheckDao(database: AppDatabase): HgtCheckDao = database.hgtCheckDao()

    @Provides
    @Singleton
    fun provideDeviceLogRepository(
        impl: DeviceLogRepositoryImpl
    ): DeviceLogRepository = impl

    @Provides
    @Singleton
    fun provideHgtCheckRepository(
        impl: HgtCheckRepositoryImpl
    ): HgtCheckRepository = impl
}
