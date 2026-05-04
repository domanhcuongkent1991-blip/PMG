package com.example.devicetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_logs")
data class DeviceLogEntity(
    @PrimaryKey val recordId: String,
    val maThietBi: String,
    val hangMuc: String,
    val nguoiBaoCao: String,
    val tinhTrangThietBi: String,
    val ktvPhuTrach: String,
    val ngayPhatHien: String,
    val ngaySuaChua: String?,
    val ghiChu: String,
    val updatedAt: Long,
    val sourceSheetId: Int? = null,
    val syncStatus: String = "PENDING"
)
