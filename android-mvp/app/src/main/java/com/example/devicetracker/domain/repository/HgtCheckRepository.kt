package com.example.devicetracker.domain.repository

import com.example.devicetracker.domain.model.HgtCheck
import kotlinx.coroutines.flow.Flow

interface HgtCheckRepository {
    fun observeChecks(query: String): Flow<List<HgtCheck>>
    suspend fun upsertCheck(
        id: String?,
        maThietBi: String,
        chuKyNgay: Int,
        lanGanNhat: String
    )
    suspend fun deleteCheck(id: String)
    suspend fun syncPending(): Result<Unit>
    suspend fun refreshFromRemote(): Result<Unit>
    suspend fun updateLatestCheckDate(id: String, latestDate: String)
}
