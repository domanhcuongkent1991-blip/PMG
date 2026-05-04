package com.example.devicetracker.domain.model

data class HgtCheck(
    val id: String,
    val maThietBi: String,
    val chuKyNgay: Int,
    val lanGanNhat: String,
    val lanTiepTheo: String,
    val updatedAt: Long
)
