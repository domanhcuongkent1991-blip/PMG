package com.example.devicetracker.ui.hgt

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.data.local.preferences.HgtReminderSettingsStore
import com.example.devicetracker.domain.model.HgtReminderSettings
import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.domain.usecase.DeleteHgtCheckUseCase
import com.example.devicetracker.domain.usecase.ObserveHgtChecksUseCase
import com.example.devicetracker.domain.usecase.UpsertHgtCheckUseCase
import com.example.devicetracker.reminder.HgtReminderScheduler
import com.example.devicetracker.util.DateTextFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class HgtCheckViewModel @Inject constructor(
    private val observeHgtChecksUseCase: ObserveHgtChecksUseCase,
    private val upsertHgtCheckUseCase: UpsertHgtCheckUseCase,
    private val deleteHgtCheckUseCase: DeleteHgtCheckUseCase,
    private val reminderSettingsStore: HgtReminderSettingsStore,
    private val hgtReminderScheduler: HgtReminderScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow(HgtCheckUiState(isLoading = true))
    val uiState: StateFlow<HgtCheckUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    init {
        loadReminderSettings()
        observeChecks()
    }

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
        observeChecks()
    }

    fun startEdit(item: HgtCheck) {
        _uiState.update {
            it.copy(
                isEditorVisible = true,
                isCreateMode = false,
                editingItemId = item.id,
                editingDeviceCode = item.maThietBi,
                editingCycleDays = item.chuKyNgay.toString(),
                editingLatestDate = DateTextFormatter.formatForDisplay(item.lanGanNhat).takeIf { date -> date != "--" }.orEmpty(),
                editingError = null
            )
        }
    }

    fun startAdd() {
        _uiState.update {
            it.copy(
                isEditorVisible = true,
                isCreateMode = true,
                editingItemId = null,
                editingDeviceCode = "",
                editingCycleDays = "120",
                editingLatestDate = "",
                editingError = null
            )
        }
    }

    fun dismissEdit() {
        _uiState.update {
            it.copy(
                isEditorVisible = false,
                isCreateMode = false,
                editingItemId = null,
                editingDeviceCode = "",
                editingCycleDays = "",
                editingLatestDate = "",
                editingError = null
            )
        }
    }

    fun onEditingDeviceCodeChanged(value: String) {
        _uiState.update { it.copy(editingDeviceCode = value, editingError = null) }
    }

    fun onEditingCycleDaysChanged(value: String) {
        _uiState.update { it.copy(editingCycleDays = value, editingError = null) }
    }

    fun onEditingLatestDateChanged(value: String) {
        _uiState.update { it.copy(editingLatestDate = value, editingError = null) }
    }

    fun saveHgtCheck() {
        val state = _uiState.value
        if (!state.isEditorVisible) return

        val deviceCode = state.editingDeviceCode.trim()
        if (deviceCode.isBlank()) {
            _uiState.update { it.copy(editingError = "Mã thiết bị không được để trống") }
            return
        }

        val cycleDays = state.editingCycleDays.trim().toIntOrNull()
        if (cycleDays == null || cycleDays <= 0) {
            _uiState.update { it.copy(editingError = "Chu kỳ phải là số nguyên dương") }
            return
        }

        val normalized = DateTextFormatter.normalizeInputOrNull(state.editingLatestDate)
        if (normalized == null) {
            _uiState.update { it.copy(editingError = "Ngày cần đúng định dạng dd/MM/yyyy") }
            return
        }

        viewModelScope.launch {
            runCatching {
                upsertHgtCheckUseCase(
                    id = state.editingItemId,
                    maThietBi = deviceCode,
                    chuKyNgay = cycleDays,
                    lanGanNhat = normalized
                )
                hgtReminderScheduler.rescheduleAll()
            }
                .onSuccess { dismissEdit() }
                .onFailure { throwable ->
                    _uiState.update { it.copy(editingError = throwable.message ?: "Không lưu được dữ liệu HGT") }
                }
        }
    }

    fun deleteEditingItem() {
        val itemId = _uiState.value.editingItemId ?: return
        viewModelScope.launch {
            runCatching {
                deleteHgtCheckUseCase(itemId)
                hgtReminderScheduler.rescheduleAll()
            }
                .onSuccess { dismissEdit() }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(editingError = throwable.message ?: "Không xóa được dữ liệu HGT")
                    }
                }
        }
    }

    fun openReminderSettings() {
        _uiState.update { it.copy(isReminderDialogVisible = true, reminderError = null) }
    }

    fun dismissReminderSettings() {
        _uiState.update { it.copy(isReminderDialogVisible = false, reminderError = null) }
    }

    fun onReminderEnabledChanged(value: Boolean) {
        _uiState.update { it.copy(reminderEnabled = value, reminderError = null) }
    }

    fun onReminderDaysBeforeChanged(value: String) {
        _uiState.update { it.copy(reminderDaysBefore = value, reminderError = null) }
    }

    fun onReminderTimeChanged(value: String) {
        _uiState.update { it.copy(reminderTime = value, reminderError = null) }
    }

    fun saveReminderSettings() {
        val state = _uiState.value
        val daysBefore = state.reminderDaysBefore.trim().toIntOrNull()
        if (daysBefore == null || daysBefore < 0 || daysBefore > 3650) {
            _uiState.update { it.copy(reminderError = "So ngay nhac truoc phai tu 0 den 3650") }
            return
        }

        val timeRegex = Regex("""^([01]\d|2[0-3]):([0-5]\d)$""")
        val match = timeRegex.matchEntire(state.reminderTime.trim())
        if (match == null) {
            _uiState.update { it.copy(reminderError = "Gio nhac can dung dinh dang HH:mm") }
            return
        }
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].toInt()

        viewModelScope.launch {
            runCatching {
                reminderSettingsStore.save(
                    HgtReminderSettings(
                        enabled = state.reminderEnabled,
                        daysBefore = daysBefore,
                        hourOfDay = hour,
                        minute = minute
                    )
                )
                hgtReminderScheduler.rescheduleAll()
            }
                .onSuccess {
                    _uiState.update { it.copy(isReminderDialogVisible = false, reminderError = null) }
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(reminderError = throwable.message ?: "Khong luu duoc cai dat nhac lich")
                    }
                }
        }
    }

    private fun loadReminderSettings() {
        val settings = reminderSettingsStore.load()
        _uiState.update {
            it.copy(
                reminderEnabled = settings.enabled,
                reminderDaysBefore = settings.daysBefore.toString(),
                reminderTime = String.format(Locale.US, "%02d:%02d", settings.hourOfDay, settings.minute)
            )
        }
    }

    private fun observeChecks() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            observeHgtChecksUseCase(_uiState.value.query)
                .catch { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
                .collect { items ->
                    _uiState.update {
                        it.copy(items = items, isLoading = false)
                    }
                }
        }
    }
}
