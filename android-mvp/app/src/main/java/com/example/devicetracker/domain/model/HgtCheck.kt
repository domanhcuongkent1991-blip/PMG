package com.example.devicetracker.domain.model

data class HgtCheck(
    val id: String,
    val maThietBi: String,
    val chuKyNgay: Int,
    val lanGanNhat: String,
    val lanTiepTheo: String,
    val ghiChu: String = "",
    val updatedAt: Long
)
