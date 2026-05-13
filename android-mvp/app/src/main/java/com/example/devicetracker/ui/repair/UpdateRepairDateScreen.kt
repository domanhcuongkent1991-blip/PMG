package com.example.devicetracker.ui.repair

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devicetracker.R
import com.example.devicetracker.util.DateTextFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateRepairDateScreen(
    onBack: () -> Unit,
    viewModel: UpdateRepairDateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.repair_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (!uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!uiState.errorMessage.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = viewModel::clearRepairDate,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.repair_clear_button))
                        }
                        Button(
                            onClick = viewModel::save,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.repair_save_button))
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.repair_section_info),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.repair_device_code_value,
                        uiState.maThietBi
                    )
                )
            }
            item {
                Text(
                    text = stringResource(
                        R.string.repair_discovery_date_value,
                        DateTextFormatter.formatForDisplay(uiState.ngayPhatHien)
                    )
                )
            }
            item {
                Text(
                    text = stringResource(R.string.repair_record_id_value, uiState.recordId)
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.tinhTrangThietBiInput,
                    onValueChange = viewModel::onConditionChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_device_condition)) },
                    minLines = 3,
                    maxLines = 8
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ngaySuaChuaInput,
                    onValueChange = viewModel::onRepairDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_repair_date_optional)) },
                    placeholder = { Text(stringResource(R.string.placeholder_repair_date_optional)) },
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ghiChuInput,
                    onValueChange = viewModel::onNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.label_note)) },
                    placeholder = { Text(stringResource(R.string.placeholder_note)) },
                    minLines = 3,
                    maxLines = 5
                )
            }
        }
    }
}
