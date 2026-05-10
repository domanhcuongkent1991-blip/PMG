package com.example.devicetracker.ui.edit

data class EditUiState(
    val maThietBi: String = "",
    val hangMuc: String = "",
    val nguoiBaoCao: String = "",
    val tinhTrangThietBi: String = "",
    val ktvPhuTrach: String = "",
    val ngayPhatHien: String = "",
    val ngaySuaChua: String = "",
    val ghiChu: String = "",
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)
