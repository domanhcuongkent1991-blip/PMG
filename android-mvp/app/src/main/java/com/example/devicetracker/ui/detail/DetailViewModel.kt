package com.example.devicetracker.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.repository.DeviceLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: DeviceLogRepository
) : ViewModel() {

    private val recordId: String = checkNotNull(savedStateHandle["recordId"])

    private val _log = MutableStateFlow<DeviceLog?>(null)
    val log: StateFlow<DeviceLog?> = _log.asStateFlow()

    init {
        viewModelScope.launch {
            _log.value = repository.getLog(recordId)
        }
    }
}
