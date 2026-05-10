package com.example.devicetracker.ui.search

import android.util.Log

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.devicetracker.R
import com.example.devicetracker.data.local.preferences.SidebarMonthlyLabelStore
import com.example.devicetracker.domain.model.RepairFilter
import com.example.devicetracker.ui.components.DeviceLogCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenSyncStatus: () -> Unit,
    onOpenHgtChecks: () -> Unit,
    onAddNew: () -> Unit,
    onOpenDetail: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val labelSettingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val sidebarLabelStore = remember(context.applicationContext) {
        SidebarMonthlyLabelStore(context.applicationContext)
    }
    val defaultMonthlyDmbtLabel = stringResource(R.string.category_monthly_dmbt)
    val defaultMonthlyRepairLabel = stringResource(R.string.category_monthly_repair)
    var requestSearchFocusSignal by remember { mutableIntStateOf(0) }
    var isFilterSheetVisible by remember { mutableStateOf(false) }
    var isLabelSettingsVisible by remember { mutableStateOf(false) }
    var monthlyDmbtLabelOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var monthlyRepairLabelOverride by rememberSaveable { mutableStateOf<String?>(null) }
    var monthlyDmbtLabelDraft by rememberSaveable { mutableStateOf("") }
    var monthlyRepairLabelDraft by rememberSaveable { mutableStateOf("") }
    var navigationDispatched by rememberSaveable { mutableStateOf(false) }
    val monthlyDmbtLabel = monthlyDmbtLabelOverride ?: defaultMonthlyDmbtLabel
    val monthlyRepairLabel = monthlyRepairLabelOverride ?: defaultMonthlyRepairLabel

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                navigationDispatched = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openFilterSheet() {
        isFilterSheetVisible = true
    }

    fun applyFilterAndClose(action: () -> Unit) {
        action()
        scope.launch {
            filterSheetState.hide()
            isFilterSheetVisible = false
        }
    }

    fun openLabelSettingsSheet() {
        monthlyDmbtLabelDraft = monthlyDmbtLabel
        monthlyRepairLabelDraft = monthlyRepairLabel
        isLabelSettingsVisible = true
    }

    fun closeLabelSettingsSheet() {
        scope.launch {
            labelSettingsSheetState.hide()
            isLabelSettingsVisible = false
        }
    }

    fun saveLabelOverrides() {
        sidebarLabelStore.save(
            monthlyDmbtLabelInput = monthlyDmbtLabelDraft,
            monthlyRepairLabelInput = monthlyRepairLabelDraft
        )
        val saved = sidebarLabelStore.load()
        monthlyDmbtLabelOverride = saved.monthlyDmbtLabel
        monthlyRepairLabelOverride = saved.monthlyRepairLabel
        closeLabelSettingsSheet()
    }

    fun resetLabelOverrides() {
        sidebarLabelStore.resetToDefault()
        monthlyDmbtLabelOverride = null
        monthlyRepairLabelOverride = null
        monthlyDmbtLabelDraft = defaultMonthlyDmbtLabel
        monthlyRepairLabelDraft = defaultMonthlyRepairLabel
    }

    LaunchedEffect(requestSearchFocusSignal) {
        if (requestSearchFocusSignal > 0) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(Unit) {
        val loaded = sidebarLabelStore.load()
        monthlyDmbtLabelOverride = loaded.monthlyDmbtLabel
        monthlyRepairLabelOverride = loaded.monthlyRepairLabel
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            MaintenanceDrawer(
                uiState = uiState,
                onAddNew = {
                    if (navigationDispatched) return@MaintenanceDrawer
                    navigationDispatched = true
                    Log.i(TAG, "WS_FIX_SEARCH_NAV_ADD")
                    scope.launch { drawerState.close() }
                    onAddNew()
                },
                onOpenSyncStatus = {
                    if (navigationDispatched) return@MaintenanceDrawer
                    navigationDispatched = true
                    Log.i(TAG, "WS_FIX_SEARCH_NAV_SYNC")
                    scope.launch { drawerState.close() }
                    onOpenSyncStatus()
                },
                onOpenHgtChecks = {
                    if (navigationDispatched) return@MaintenanceDrawer
                    navigationDispatched = true
                    Log.i(TAG, "WS_FIX_SEARCH_NAV_HGT")
                    scope.launch { drawerState.close() }
                    onOpenHgtChecks()
                },
                onOpenSearch = {
                    scope.launch {
                        drawerState.close()
                        requestSearchFocusSignal += 1
                    }
                },
                onFilterSelected = { filter ->
                    viewModel.onFilterSelected(filter)
                    scope.launch { drawerState.close() }
                },
                onCategorySelected = { categoryId ->
                    viewModel.onCategorySelected(categoryId)
                    scope.launch { drawerState.close() }
                },
                monthlyDmbtLabel = monthlyDmbtLabel,
                monthlyRepairLabel = monthlyRepairLabel,
                onOpenMonthlyLabelSettings = {
                    scope.launch { drawerState.close() }
                    openLabelSettingsSheet()
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFEAF7F8),
                            Color(0xFFFFF8FC),
                            Color(0xFFEFF8FA)
                        )
                    )
                )
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.search_title),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.cd_open_sidebar)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = {
                            if (navigationDispatched) return@FloatingActionButton
                            navigationDispatched = true
                            Log.i(TAG, "WS_FIX_SEARCH_NAV_ADD_FAB")
                            onAddNew()
                        },
                        modifier = Modifier.navigationBarsPadding(),
                        containerColor = Color(0xFF0B8FD3),
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_new))
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    MaintenanceSearchField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        focusRequester = searchFocusRequester
                    )
                    CompactFilterBar(
                        statusText = "${filterLabel(uiState.selectedFilter)} ▼",
                        sortText = "${sortFieldLabel(uiState.selectedSortField)} ${sortOrderArrow(uiState.selectedSortOrder)}",
                        onStatusClick = ::openFilterSheet,
                        onSortClick = ::openFilterSheet,
                        onFilterClick = ::openFilterSheet
                    )

                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }

                    when {
                        uiState.items.isEmpty() && uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        uiState.items.isEmpty() -> {
                            EmptyStateCard()
                        }

                        else -> {
                            Box(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(bottom = 104.dp)
                                ) {
                                    itemsIndexed(uiState.items, key = { _, item -> item.recordId }) { index, item ->
                                        DeviceLogCard(
                                            item = item,
                                            displayIndex = index + 1,
                                            onClick = {
                                                if (navigationDispatched) return@DeviceLogCard
                                                navigationDispatched = true
                                                Log.i(TAG, "WS_FIX_SEARCH_NAV_DETAIL:${item.recordId}")
                                                onOpenDetail(item.recordId)
                                            }
                                        )
                                    }
                                }
                                if (uiState.isLoading && uiState.items.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isFilterSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { isFilterSheetVisible = false },
            sheetState = filterSheetState
        ) {
            FilterOptionsSheet(
                selectedFilter = uiState.selectedFilter,
                selectedSortField = uiState.selectedSortField,
                selectedSortOrder = uiState.selectedSortOrder,
                onFilterSelected = { filter ->
                    applyFilterAndClose { viewModel.onFilterSelected(filter) }
                },
                onSortFieldSelected = { field ->
                    applyFilterAndClose { viewModel.onSortFieldSelected(field) }
                },
                onSortOrderSelected = { order ->
                    applyFilterAndClose { viewModel.onSortOrderSelected(order) }
                }
            )
        }
    }

    if (isLabelSettingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { isLabelSettingsVisible = false },
            sheetState = labelSettingsSheetState
        ) {
            MonthlyLabelSettingsSheet(
                monthlyDmbtLabel = monthlyDmbtLabelDraft,
                monthlyRepairLabel = monthlyRepairLabelDraft,
                onMonthlyDmbtLabelChange = { monthlyDmbtLabelDraft = it },
                onMonthlyRepairLabelChange = { monthlyRepairLabelDraft = it },
                onSave = ::saveLabelOverrides,
                onReset = ::resetLabelOverrides,
                onClose = ::closeLabelSettingsSheet
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.label_device_code)) },
        placeholder = { Text(stringResource(R.string.placeholder_device_code)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF71797E)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF0E7D73),
            unfocusedBorderColor = Color(0xFFE3EAEC),
            focusedContainerColor = Color.White.copy(alpha = 0.86f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.72f)
        )
    )
}

@Composable
private fun CompactFilterBar(
    statusText: String,
    sortText: String,
    onStatusClick: () -> Unit,
    onSortClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompactFilterChip(
            text = statusText,
            onClick = onStatusClick
        )
        CompactFilterChip(
            text = sortText,
            onClick = onSortClick
        )
        CompactFilterChip(
            text = stringResource(R.string.filter_action_button),
            leadingIcon = Icons.Default.FilterList,
            onClick = onFilterClick
        )
    }
}

@Composable
private fun CompactFilterChip(
    text: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.86f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            leadingIcon?.let { icon ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF4B575C),
                    modifier = Modifier.width(16.dp)
                )
            }
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF182327),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun FilterOptionsSheet(
    selectedFilter: RepairFilter,
    selectedSortField: DateSortField,
    selectedSortOrder: DateSortOrder,
    onFilterSelected: (RepairFilter) -> Unit,
    onSortFieldSelected: (DateSortField) -> Unit,
    onSortOrderSelected: (DateSortOrder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.filter_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        FilterOptionsSection(title = stringResource(R.string.search_status_filter_title)) {
            RepairFilter.entries.forEach { filter ->
                CompactSheetOption(
                    text = filterLabel(filter),
                    active = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) }
                )
            }
        }

        FilterOptionsSection(title = stringResource(R.string.search_date_sort_title)) {
            DateSortField.entries.forEach { field ->
                CompactSheetOption(
                    text = sortFieldLabel(field),
                    active = selectedSortField == field,
                    onClick = { onSortFieldSelected(field) }
                )
            }
        }

        FilterOptionsSection(title = stringResource(R.string.filter_order_title)) {
            DateSortOrder.entries.forEach { order ->
                CompactSheetOption(
                    text = sortOrderLabel(order),
                    active = selectedSortOrder == order,
                    onClick = { onSortOrderSelected(order) }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

@Composable
private fun FilterOptionsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun MonthlyLabelSettingsSheet(
    monthlyDmbtLabel: String,
    monthlyRepairLabel: String,
    onMonthlyDmbtLabelChange: (String) -> Unit,
    onMonthlyRepairLabelChange: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.sidebar_monthly_label_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.sidebar_monthly_label_settings_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF4B575C)
        )
        OutlinedTextField(
            value = monthlyDmbtLabel,
            onValueChange = onMonthlyDmbtLabelChange,
            label = { Text(stringResource(R.string.sidebar_monthly_dmbt_label_input)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = monthlyRepairLabel,
            onValueChange = onMonthlyRepairLabelChange,
            label = { Text(stringResource(R.string.sidebar_monthly_repair_label_input)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CompactSheetOption(
                text = stringResource(R.string.sidebar_label_save),
                active = true,
                onClick = onSave
            )
            CompactSheetOption(
                text = stringResource(R.string.sidebar_label_reset_default),
                active = false,
                onClick = onReset
            )
            CompactSheetOption(
                text = stringResource(R.string.sidebar_label_close),
                active = false,
                onClick = onClose
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun CompactSheetOption(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (active) Color(0xFFBDECF8) else Color.White,
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = active,
            borderColor = Color(0xFFD2D8DC),
            selectedBorderColor = Color(0xFF1688B3)
        ),
        tonalElevation = if (active) 1.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .height(38.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color(0xFF10242A),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun MaintenanceDrawer(
    uiState: SearchUiState,
    onAddNew: () -> Unit,
    onOpenSyncStatus: () -> Unit,
    onOpenHgtChecks: () -> Unit,
    onOpenSearch: () -> Unit,
    onFilterSelected: (RepairFilter) -> Unit,
    onCategorySelected: (String) -> Unit,
    monthlyDmbtLabel: String,
    monthlyRepairLabel: String,
    onOpenMonthlyLabelSettings: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        drawerContainerColor = Color.White
    ) {
        Text(
            text = stringResource(R.string.sidebar_title),
            style = MaterialTheme.typography.headlineSmall,
            color = Color(0xFF7A7F84),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
        )

        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QuickActionCard(
                icon = Icons.Default.Search,
                label = stringResource(R.string.sidebar_search),
                selected = true,
                onClick = onOpenSearch,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.Add,
                label = stringResource(R.string.sidebar_add_record),
                onClick = onAddNew,
                modifier = Modifier.weight(1f)
            )
            QuickActionCard(
                icon = Icons.Default.Sync,
                label = stringResource(R.string.sidebar_sync_status),
                onClick = onOpenSyncStatus,
                modifier = Modifier.weight(1f)
            )
        }

        DrawerSectionTitle(text = stringResource(R.string.sidebar_status_section))
        RepairFilter.entries.forEach { filter ->
            DrawerMenuItem(
                label = filterLabel(filter),
                selected = uiState.selectedFilter == filter,
                icon = when (filter) {
                    RepairFilter.ALL -> Icons.Default.Check
                    RepairFilter.REPAIRED -> Icons.Default.Build
                    RepairFilter.PENDING -> Icons.Default.Sync
                },
                onClick = { onFilterSelected(filter) }
            )
        }

        DrawerSectionTitle(text = stringResource(R.string.sidebar_yearly_dmbt_section))
        uiState.categoryOptions
            .filter { it.type == MaintenanceCategoryType.YEARLY_ALL || it.type == MaintenanceCategoryType.YEARLY }
            .forEach { option ->
                DrawerMenuItem(
                    label = categoryLabel(option, monthlyDmbtLabel, monthlyRepairLabel),
                    selected = uiState.selectedCategoryId == option.id,
                    icon = Icons.Default.CalendarMonth,
                    onClick = { onCategorySelected(option.id) }
                )
            }

        DrawerSectionTitle(text = stringResource(R.string.sidebar_monthly_dmbt_section))
        uiState.categoryOptions
            .filter { it.type == MaintenanceCategoryType.MONTHLY_DMBT }
            .forEach { option ->
                DrawerMenuItem(
                    label = categoryLabel(option, monthlyDmbtLabel, monthlyRepairLabel),
                    selected = uiState.selectedCategoryId == option.id,
                    icon = Icons.Default.CalendarMonth,
                    onClick = { onCategorySelected(option.id) }
                )
            }

        DrawerSectionTitle(text = stringResource(R.string.sidebar_monthly_repair_section))
        uiState.categoryOptions
            .filter { it.type == MaintenanceCategoryType.MONTHLY_REPAIR }
            .forEach { option ->
                DrawerMenuItem(
                    label = categoryLabel(option, monthlyDmbtLabel, monthlyRepairLabel),
                    selected = uiState.selectedCategoryId == option.id,
                    icon = Icons.Default.Build,
                    onClick = { onCategorySelected(option.id) }
                )
            }
        DrawerMenuItem(
            label = stringResource(R.string.sidebar_monthly_label_settings),
            selected = false,
            icon = Icons.Default.Settings,
            onClick = onOpenMonthlyLabelSettings
        )

        DrawerSectionTitle(text = stringResource(R.string.sidebar_hgt_section))
        DrawerMenuItem(
            label = stringResource(R.string.sidebar_hgt_checks),
            selected = false,
            icon = Icons.Default.Settings,
            onClick = onOpenHgtChecks
        )
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: () -> Unit = {}
) {
    ElevatedCard(
        modifier = modifier.height(92.dp),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) Color(0xFFEAF7FC) else Color.White
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Black,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF797D82),
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 8.dp)
    )
}

@Composable
private fun DrawerMenuItem(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFE7F6FC) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (selected) Color(0xFF239FD2) else Color.Transparent)
            )
            Spacer(modifier = Modifier.width(18.dp))
            Icon(imageVector = icon, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(18.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black
            )
        }
    }
}

@Composable
private fun EmptyStateCard() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White.copy(alpha = 0.82f),
            tonalElevation = 2.dp
        ) {
            Text(
                text = stringResource(R.string.search_empty_state),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(22.dp)
            )
        }
    }
}

@Composable
private fun filterLabel(filter: RepairFilter): String = when (filter) {
    RepairFilter.ALL -> stringResource(R.string.filter_all)
    RepairFilter.REPAIRED -> stringResource(R.string.filter_repaired)
    RepairFilter.PENDING -> stringResource(R.string.filter_pending)
}

@Composable
private fun categoryLabel(
    option: MaintenanceCategoryOption,
    monthlyDmbtLabel: String,
    monthlyRepairLabel: String
): String = when (option.type) {
    MaintenanceCategoryType.YEARLY_ALL -> stringResource(R.string.category_all)
    MaintenanceCategoryType.YEARLY -> stringResource(R.string.timeline_chip_label, option.year ?: 0)
    MaintenanceCategoryType.MONTHLY_DMBT -> monthlyDmbtLabel
    MaintenanceCategoryType.MONTHLY_REPAIR -> monthlyRepairLabel
}

@Composable
private fun sortFieldLabel(field: DateSortField): String = when (field) {
    DateSortField.DISCOVERY_DATE -> stringResource(R.string.sort_field_discovery_date)
    DateSortField.REPAIR_DATE -> stringResource(R.string.sort_field_repair_date)
}

@Composable
private fun sortOrderLabel(order: DateSortOrder): String = when (order) {
    DateSortOrder.NEWEST_FIRST -> stringResource(R.string.sort_order_newest_first)
    DateSortOrder.OLDEST_FIRST -> stringResource(R.string.sort_order_oldest_first)
}

private fun sortOrderArrow(order: DateSortOrder): String = when (order) {
    DateSortOrder.NEWEST_FIRST -> "↓"
    DateSortOrder.OLDEST_FIRST -> "↑"
}






private const val TAG = "SearchScreen"





