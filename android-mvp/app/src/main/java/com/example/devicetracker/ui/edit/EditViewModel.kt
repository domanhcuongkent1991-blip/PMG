package com.example.devicetracker.ui.edit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.data.sheet.SheetConfig
import com.example.devicetracker.domain.usecase.SaveDeviceLogUseCase
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.RecordIdFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class EditViewModel @Inject constructor(
    private val saveDeviceLogUseCase: SaveDeviceLogUseCase,
    private val recordIdFactory: RecordIdFactory,
    private val sheetConfig: SheetConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditUiState())
    val uiState: StateFlow<EditUiState> = _uiState.asStateFlow()

    fun update(transform: (EditUiState) -> EditUiState) {
        _uiState.update(transform)
    }

    fun save() {
        val state = uiState.value
        if (state.isSaving) {
            Log.i(TAG, "WS_FIX_EDIT_SAVE_SKIPPED_ALREADY_SAVING")
            return
        }
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
        val targetSourceSheetId = sheetConfig.dmbtSheetIdForDiscoveryDate(normalizedDiscoveryDate)
        if (targetSourceSheetId == null) {
            _uiState.update { it.copy(errorMessage = "Ngày phát hiện chưa có sheet DMBT theo năm tương ứng") }
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
            Log.i(TAG, "WS_FIX_EDIT_SAVE_START")
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            runCatching {
                withContext(Dispatchers.IO) {
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
                            updatedAt = System.currentTimeMillis(),
                            sourceSheetId = targetSourceSheetId
                        )
                    )
                }
            }.onSuccess {
                Log.i(TAG, "WS_FIX_EDIT_SAVE_SUCCESS")
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveSuccess = true,
                        errorMessage = null
                    )
                }
            }.onFailure { throwable ->
                Log.e(TAG, "WS_FIX_EDIT_SAVE_FAILURE: ${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = throwable.message ?: "Lưu dữ liệu thất bại. Vui lòng thử lại."
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "EditViewModel"
    }
}
