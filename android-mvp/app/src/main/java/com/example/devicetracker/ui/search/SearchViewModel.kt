package com.example.devicetracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.usecase.SearchLogsByDeviceCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchLogsByDeviceCodeUseCase: SearchLogsByDeviceCodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var timelineJob: Job? = null
    private var latestItems: List<DeviceLog> = emptyList()
    private var timelineReferenceYears: List<Int> = emptyList()

    init {
        observeTimelineReferenceYears()
        observeLogs()
    }

    fun onQueryChanged(value: String) {
        _uiState.update { it.copy(query = value) }
        observeLogs()
    }

    fun onFilterSelected(filter: RepairFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        observeLogs()
    }

    fun onCategorySelected(categoryId: String) {
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        applyTimelineFilter()
    }

    fun onSortFieldSelected(field: DateSortField) {
        _uiState.update { it.copy(selectedSortField = field) }
        applyTimelineFilter()
    }

    fun onSortOrderSelected(order: DateSortOrder) {
        _uiState.update { it.copy(selectedSortOrder = order) }
        applyTimelineFilter()
    }

    private fun observeLogs() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            searchLogsByDeviceCodeUseCase(
                deviceCode = uiState.value.query,
                filter = uiState.value.selectedFilter
            )
                .catch { throwable ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = throwable.message)
                    }
                }
                .collect { items ->
                    latestItems = items
                    applyTimelineFilter(isLoading = false)
                }
        }
    }

    private fun observeTimelineReferenceYears() {
        timelineJob?.cancel()
        timelineJob = viewModelScope.launch {
            searchLogsByDeviceCodeUseCase(
                deviceCode = "",
                filter = RepairFilter.ALL
            )
                .catch {
                    // Keep fallback timeline years from baseline when this stream fails.
                }
                .collect { items ->
                    timelineReferenceYears = extractTimelineYears(items)
                    applyTimelineFilter()
                }
        }
    }

    private fun applyTimelineFilter(isLoading: Boolean = _uiState.value.isLoading) {
        val presentation = buildTimelineFilterPresentation(
            items = latestItems,
            selectedYear = null,
            additionalYears = timelineReferenceYears
        )
        val categoryPresentation = buildMaintenanceCategoryPresentation(
            items = latestItems,
            selectedCategoryId = _uiState.value.selectedCategoryId,
            additionalYears = timelineReferenceYears
        )
        val sortedItems = sortDeviceLogsByDate(
            items = categoryPresentation.visibleItems,
            field = _uiState.value.selectedSortField,
            order = _uiState.value.selectedSortOrder
        )
        _uiState.update {
            it.copy(
                items = sortedItems,
                timelineYearOptions = presentation.years,
                categoryOptions = categoryPresentation.categoryOptions,
                selectedCategoryId = categoryPresentation.selectedCategoryId,
                isLoading = isLoading
            )
        }
    }
}
