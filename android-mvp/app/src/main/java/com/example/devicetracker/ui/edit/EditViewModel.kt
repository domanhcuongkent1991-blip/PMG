package com.example.devicetracker.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.usecase.SaveDeviceLogUseCase
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.RecordIdFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class EditViewModel @Inject constructor(
    private val saveDeviceLogUseCase: SaveDeviceLogUseCase,
    private val recordIdFactory: RecordIdFactory
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    fun update(transform: (EditUiState) -> EditUiState) {
        _uiState.update(transform)
    }

    fun save() {
        val state = uiState.value
        if (state.maThietBi.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Mã thiết bị là bắt buộc") }
            return
        }
        if (state.ngayPhatHien.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Ngày phát hiện là bắt buộc") }
            return
        }
        val normalizedDiscoveryDate = DateTextFormatter.normalizeInputOrNull(state.ngayPhatHien)
        if (normalizedDiscoveryDate == null) {
            _uiState.update { it.copy(errorMessage = "Ngày phát hiện phải đúng định dạng dd/MM/yyyy") }
            return
        }

        val repairDate = state.ngaySuaChua.trim().ifBlank { null }?.let {
            DateTextFormatter.normalizeInputOrNull(it)
        }
        if (state.ngaySuaChua.trim().isNotEmpty() && repairDate == null) {
            _uiState.update { it.copy(errorMessage = "Ngày sửa chữa phải đúng định dạng dd/MM/yyyy") }
            return
        }

        viewModelScope.launch {
            saveDeviceLogUseCase(
                DeviceLog(
                    recordId = recordIdFactory.newId(),
                    maThietBi = state.maThietBi.trim(),
                    hangMuc = state.hangMuc.trim(),
                    nguoiBaoCao = state.nguoiBaoCao.trim(),
                    tinhTrangThietBi = state.tinhTrangThietBi.trim(),
                    ktvPhuTrach = state.ktvPhuTrach.trim(),
                    ngayPhatHien = normalizedDiscoveryDate,
                    ngaySuaChua = repairDate,
                    ghiChu = state.ghiChu.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(saveSuccess = true, errorMessage = null) }
        }
    }
}
