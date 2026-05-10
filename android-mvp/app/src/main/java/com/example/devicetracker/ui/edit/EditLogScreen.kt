package com.example.devicetracker.ui.edit

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devicetracker.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLogScreen(
    onBack: () -> Unit,
    viewModel: EditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var backDispatched by remember { mutableStateOf(false) }

    BackHandler(enabled = uiState.isSaving) {
        Log.i(TAG, "WS_FIX_EDIT_BACK_BLOCKED: ignoring system back while save is in progress")
    }

    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess && !backDispatched) {
            backDispatched = true
            Log.i(TAG, "WS_FIX_EDIT_SAVE_SUCCESS_NAV_BACK")
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.edit_title)) },
                navigationIcon = {
                    IconButton(
                        enabled = !uiState.isSaving,
                        onClick = {
                            if (uiState.isSaving) {
                                Log.i(TAG, "WS_FIX_EDIT_TOPBAR_BACK_BLOCKED: save in progress")
                                return@IconButton
                            }
                            if (backDispatched) return@IconButton
                            backDispatched = true
                            Log.i(TAG, "WS_FIX_EDIT_TOPBAR_BACK")
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
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
                Button(
                    onClick = viewModel::save,
                    enabled = !uiState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        stringResource(R.string.edit_save_button)
                    )
                }
            }
        }
    ) { padding ->
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
                    text = stringResource(R.string.edit_section_device_info),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.maThietBi,
                    onValueChange = { value -> viewModel.update { it.copy(maThietBi = value) } },
                    label = { Text(stringResource(R.string.label_device_code_required)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.hangMuc,
                    onValueChange = { value -> viewModel.update { it.copy(hangMuc = value) } },
                    label = { Text(stringResource(R.string.label_category)) },
                    placeholder = { Text(stringResource(R.string.placeholder_category_example)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.nguoiBaoCao,
                    onValueChange = { value -> viewModel.update { it.copy(nguoiBaoCao = value) } },
                    label = { Text(stringResource(R.string.label_reporter)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.tinhTrangThietBi,
                    onValueChange = { value -> viewModel.update { it.copy(tinhTrangThietBi = value) } },
                    label = { Text(stringResource(R.string.label_device_condition)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ktvPhuTrach,
                    onValueChange = { value -> viewModel.update { it.copy(ktvPhuTrach = value) } },
                    label = { Text(stringResource(R.string.label_technician)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text(
                    text = stringResource(R.string.edit_section_dates),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ngayPhatHien,
                    onValueChange = { value -> viewModel.update { it.copy(ngayPhatHien = value) } },
                    label = { Text(stringResource(R.string.label_discovery_date_required)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ngaySuaChua,
                    onValueChange = { value -> viewModel.update { it.copy(ngaySuaChua = value) } },
                    label = { Text(stringResource(R.string.label_repair_date_optional)) },
                    placeholder = { Text(stringResource(R.string.placeholder_repair_date_optional)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.ghiChu,
                    onValueChange = { value -> viewModel.update { it.copy(ghiChu = value) } },
                    label = { Text(stringResource(R.string.label_note)) },
                    placeholder = { Text(stringResource(R.string.placeholder_note)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

private const val TAG = "EditLogScreen"
