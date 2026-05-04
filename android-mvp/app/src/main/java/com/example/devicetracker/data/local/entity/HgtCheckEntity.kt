package com.example.devicetracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "hgt_checks")
data class HgtCheckEntity(
    @PrimaryKey val id: String,
    val maThietBi: String,
    val chuKyNgay: Int,
    val lanGanNhat: String,
    val lanTiepTheo: String,
    val updatedAt: Long,
    val syncStatus: String = "SYNCED"
)
