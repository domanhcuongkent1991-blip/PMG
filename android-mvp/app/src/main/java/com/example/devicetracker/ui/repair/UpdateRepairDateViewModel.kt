package com.example.devicetracker.ui.repair

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.repository.DeviceLogRepository
import com.example.devicetracker.domain.usecase.UpdateRepairDateUseCase
import com.example.devicetracker.util.DateTextFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class UpdateRepairDateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeviceLogRepository,
    private val updateRepairDateUseCase: UpdateRepairDateUseCase
) : ViewModel() {

    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    private val _uiState = MutableStateFlow(UpdateRepairDateUiState())
    val uiState: StateFlow<UpdateRepairDateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val log = repository.getLog(recordId)
            if (log == null) {
                _uiState.update {
                    it.copy(
                        recordId = recordId,
                        isLoading = false,
                        errorMessage = "Không tìm thấy bản ghi cần cập nhật"
                    )
                }
                return@launch
            }

            _uiState.update {
                it.copy(
                    recordId = log.recordId,
                    maThietBi = log.maThietBi,
                    ngayPhatHien = DateTextFormatter.formatForDisplay(log.ngayPhatHien),
                    ngaySuaChuaInput = if (log.ngaySuaChua.isNullOrBlank()) {
                        ""
                    } else {
                        DateTextFormatter.formatForDisplay(log.ngaySuaChua)
                    },
                    ghiChuInput = log.ghiChu,
                    isLoading = false,
                    errorMessage = null
                )
            }
        }
    }

    fun onRepairDateChanged(value: String) {
        _uiState.update { it.copy(ngaySuaChuaInput = value, errorMessage = null) }
    }

    fun onNoteChanged(value: String) {
        _uiState.update { it.copy(ghiChuInput = value, errorMessage = null) }
    }

    fun clearRepairDate() {
        _uiState.update { it.copy(ngaySuaChuaInput = "", errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        val inputValue = state.ngaySuaChuaInput.trim()
        val normalizedNote = state.ghiChuInput.trim()
        val normalizedRepairDate = if (inputValue.isEmpty()) {
            null
        } else {
            DateTextFormatter.normalizeInputOrNull(inputValue)
        }
        if (inputValue.isNotEmpty() && normalizedRepairDate == null) {
            _uiState.update { it.copy(errorMessage = "Ngày sửa chữa phải đúng định dạng dd/MM/yyyy") }
            return
        }

        viewModelScope.launch {
            runCatching {
                updateRepairDateUseCase(
                    recordId = recordId,
                    ngaySuaChua = normalizedRepairDate,
                    ghiChu = normalizedNote
                )
            }.onSuccess {
                _uiState.update { it.copy(saveSuccess = true, errorMessage = null) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(errorMessage = throwable.message ?: "Cập nhật ngày sửa chữa thất bại")
                }
            }
        }
    }
}
