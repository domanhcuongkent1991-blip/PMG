package com.example.devicetracker.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.domain.usecase.SearchLogsByDeviceCodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchLogsByDeviceCodeUseCase: SearchLogsByDeviceCodeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var timelineJob: Job? = null
    private var filterJob: Job? = null
    private var filterGeneration: Long = 0L
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
                .collectLatest { items ->
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
                .collectLatest { items ->
                    timelineReferenceYears = extractTimelineYears(items)
                    applyTimelineFilter(isLoading = _uiState.value.isLoading && latestItems.isEmpty())
                }
        }
    }

    private fun applyTimelineFilter(isLoading: Boolean = _uiState.value.isLoading) {
        val queryItems = latestItems
        val referenceYears = timelineReferenceYears
        val selectedCategoryId = _uiState.value.selectedCategoryId
        val selectedSortField = _uiState.value.selectedSortField
        val selectedSortOrder = _uiState.value.selectedSortOrder
        val generation = ++filterGeneration

        filterJob?.cancel()
        filterJob = viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val presentation = buildTimelineFilterPresentation(
                        items = queryItems,
                        selectedYear = null,
                        additionalYears = referenceYears
                    )
                    val categoryPresentation = buildMaintenanceCategoryPresentation(
                        items = queryItems,
                        selectedCategoryId = selectedCategoryId,
                        additionalYears = referenceYears
                    )
                    val sortedItems = sortDeviceLogsByDate(
                        items = categoryPresentation.visibleItems,
                        field = selectedSortField,
                        order = selectedSortOrder
                    )

                    FilterComputation(
                        items = sortedItems,
                        years = presentation.years,
                        categoryOptions = categoryPresentation.categoryOptions,
                        selectedCategoryId = categoryPresentation.selectedCategoryId
                    )
                }
            }.onSuccess { computed ->
                if (generation != filterGeneration) return@onSuccess
                _uiState.update {
                    it.copy(
                        items = computed.items,
                        timelineYearOptions = computed.years,
                        categoryOptions = computed.categoryOptions,
                        selectedCategoryId = computed.selectedCategoryId,
                        isLoading = isLoading
                    )
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@onFailure
                Log.e(TAG, "applyTimelineFilter failed: ${throwable.message}", throwable)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = throwable.message ?: "Khong the tai du lieu danh sach."
                    )
                }
            }
        }
    }

    private data class FilterComputation(
        val items: List<DeviceLog>,
        val years: List<Int>,
        val categoryOptions: List<MaintenanceCategoryOption>,
        val selectedCategoryId: String
    )

    companion object {
        private const val TAG = "SearchViewModel"
    }
}
