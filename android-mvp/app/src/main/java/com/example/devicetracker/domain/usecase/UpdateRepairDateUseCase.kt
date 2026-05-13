package com.example.devicetracker.domain.usecase

import com.example.devicetracker.domain.repository.DeviceLogRepository
import javax.inject.Inject

class UpdateRepairDateUseCase @Inject constructor(
    private val repository: DeviceLogRepository
) {
    suspend operator fun invoke(
        recordId: String,
        ngaySuaChua: String?,
        ghiChu: String,
        tinhTrangThietBi: String
    ) {
        repository.updateRepairDate(recordId, ngaySuaChua, ghiChu, tinhTrangThietBi)
    }
}
