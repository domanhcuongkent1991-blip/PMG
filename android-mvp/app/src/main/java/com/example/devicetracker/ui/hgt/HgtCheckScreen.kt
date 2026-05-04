package com.example.devicetracker.ui.hgt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devicetracker.R
import com.example.devicetracker.domain.model.HgtCheck
import com.example.devicetracker.util.DateTextFormatter
import com.example.devicetracker.util.HgtDateCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HgtCheckScreen(
    onBack: () -> Unit,
    viewModel: HgtCheckViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                            text = stringResource(R.string.hgt_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::openReminderSettings) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = stringResource(R.string.hgt_reminder_settings_button)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.query,
                        onValueChange = viewModel::onQueryChanged,
                        label = { Text(stringResource(R.string.label_device_code)) },
                        placeholder = { Text(stringResource(R.string.placeholder_device_code)) },
                        modifier = Modifier.weight(1f),
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

                    FilledTonalButton(
                        onClick = viewModel::startAdd,
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.hgt_add_button))
                    }
                }

                if (!uiState.errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                when {
                    uiState.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    uiState.items.isEmpty() -> {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp),
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White.copy(alpha = 0.82f),
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = stringResource(R.string.hgt_empty_state),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(22.dp)
                            )
                        }
                    }

                    else -> {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            itemsIndexed(uiState.items, key = { _, item -> item.id }) { index, item ->
                                HgtCheckCard(
                                    item = item,
                                    displayIndex = index + 1,
                                    onEdit = { viewModel.startEdit(item) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isEditorVisible) {
        EditHgtCheckDialog(
            isCreateMode = uiState.isCreateMode,
            deviceCode = uiState.editingDeviceCode,
            cycleDays = uiState.editingCycleDays,
            latestDate = uiState.editingLatestDate,
            errorMessage = uiState.editingError,
            onDeviceCodeChanged = viewModel::onEditingDeviceCodeChanged,
            onCycleDaysChanged = viewModel::onEditingCycleDaysChanged,
            onLatestDateChanged = viewModel::onEditingLatestDateChanged,
            onDismiss = viewModel::dismissEdit,
            onSave = viewModel::saveHgtCheck,
            onDelete = viewModel::deleteEditingItem
        )
    }

    if (uiState.isReminderDialogVisible) {
        ReminderSettingsDialog(
            reminderEnabled = uiState.reminderEnabled,
            daysBefore = uiState.reminderDaysBefore,
            reminderTime = uiState.reminderTime,
            errorMessage = uiState.reminderError,
            onReminderEnabledChanged = viewModel::onReminderEnabledChanged,
            onDaysBeforeChanged = viewModel::onReminderDaysBeforeChanged,
            onReminderTimeChanged = viewModel::onReminderTimeChanged,
            onDismiss = viewModel::dismissReminderSettings,
            onSave = viewModel::saveReminderSettings
        )
    }
}

@Composable
private fun HgtCheckCard(
    item: HgtCheck,
    displayIndex: Int,
    onEdit: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${stringResource(R.string.label_index)} $displayIndex",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF667176)
                    )
                    Text(
                        text = item.maThietBi,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF0B7FB2),
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = stringResource(R.string.hgt_cycle_days, item.chuKyNgay),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF0E7D73)
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.EditCalendar,
                        contentDescription = stringResource(R.string.hgt_edit_item)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HgtDateColumn(
                    title = stringResource(R.string.hgt_latest_date),
                    value = DateTextFormatter.formatForDisplay(item.lanGanNhat),
                    modifier = Modifier.weight(1f)
                )
                HgtDateColumn(
                    title = stringResource(R.string.hgt_next_date),
                    value = DateTextFormatter.formatForDisplay(item.lanTiepTheo),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HgtDateColumn(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EditHgtCheckDialog(
    isCreateMode: Boolean,
    deviceCode: String,
    cycleDays: String,
    latestDate: String,
    errorMessage: String?,
    onDeviceCodeChanged: (String) -> Unit,
    onCycleDaysChanged: (String) -> Unit,
    onLatestDateChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit
) {
    val parsedCycleDays = cycleDays.trim().toIntOrNull() ?: 0
    val previewNextDate = HgtDateCalculator
        .calculateNextDate(latestDate, parsedCycleDays)
        .ifBlank { "--" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isCreateMode) {
                    stringResource(R.string.hgt_add_dialog_title)
                } else {
                    stringResource(R.string.hgt_edit_dialog_title)
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = deviceCode,
                    onValueChange = onDeviceCodeChanged,
                    label = { Text(stringResource(R.string.hgt_device_code_input)) },
                    singleLine = true,
                    isError = !errorMessage.isNullOrBlank()
                )
                OutlinedTextField(
                    value = cycleDays,
                    onValueChange = onCycleDaysChanged,
                    label = { Text(stringResource(R.string.hgt_cycle_days_input)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !errorMessage.isNullOrBlank()
                )
                OutlinedTextField(
                    value = latestDate,
                    onValueChange = onLatestDateChanged,
                    label = { Text(stringResource(R.string.hgt_latest_date_input)) },
                    singleLine = true,
                    isError = !errorMessage.isNullOrBlank()
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    text = stringResource(R.string.hgt_next_date_preview, previewNextDate),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(stringResource(R.string.repair_save_button))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!isCreateMode) {
                    TextButton(onClick = onDelete) {
                        Text(
                            text = stringResource(R.string.hgt_delete_button),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cd_back))
                }
            }
        }
    )
}

@Composable
private fun ReminderSettingsDialog(
    reminderEnabled: Boolean,
    daysBefore: String,
    reminderTime: String,
    errorMessage: String?,
    onReminderEnabledChanged: (Boolean) -> Unit,
    onDaysBeforeChanged: (String) -> Unit,
    onReminderTimeChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.hgt_reminder_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.hgt_reminder_enable_label))
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = onReminderEnabledChanged
                    )
                }
                OutlinedTextField(
                    value = daysBefore,
                    onValueChange = onDaysBeforeChanged,
                    label = { Text(stringResource(R.string.hgt_reminder_days_before_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = !errorMessage.isNullOrBlank()
                )
                OutlinedTextField(
                    value = reminderTime,
                    onValueChange = onReminderTimeChanged,
                    label = { Text(stringResource(R.string.hgt_reminder_time_label)) },
                    singleLine = true,
                    isError = !errorMessage.isNullOrBlank()
                )
                Text(
                    text = stringResource(R.string.hgt_reminder_time_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF667176)
                )
                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave) {
                Text(stringResource(R.string.repair_save_button))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.cd_back))
            }
        }
    )
}
