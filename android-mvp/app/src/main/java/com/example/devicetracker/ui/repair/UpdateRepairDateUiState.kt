package com.example.devicetracker.ui.repair

data class UpdateRepairDateUiState(
    val recordId: String = "",
    val maThietBi: String = "",
    val ngayPhatHien: String = "",
    val ngaySuaChuaInput: String = "",
    val ghiChuInput: String = "",
    val isLoading: Boolean = true,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null
)
