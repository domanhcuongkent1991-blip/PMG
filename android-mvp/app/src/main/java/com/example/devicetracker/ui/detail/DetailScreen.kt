package com.example.devicetracker.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.devicetracker.R
import com.example.devicetracker.ui.components.StatusBadge
import com.example.devicetracker.util.DateTextFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    recordId: String,
    onBack: () -> Unit,
    onUpdateRepairDate: (String) -> Unit,
    viewModel: DetailViewModel = hiltViewModel()
) {
    val log by viewModel.log.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (log == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(stringResource(R.string.detail_not_found))
            }
            return@Scaffold
        }

        val detailRows = listOf(
            stringResource(R.string.label_category) to log!!.hangMuc,
            stringResource(R.string.label_reporter) to log!!.nguoiBaoCao,
            stringResource(R.string.label_technician) to log!!.ktvPhuTrach,
            stringResource(R.string.label_discovery_date) to DateTextFormatter.formatForDisplay(log!!.ngayPhatHien),
            stringResource(R.string.label_repair_date) to DateTextFormatter.formatForDisplay(log!!.ngaySuaChua),
            stringResource(R.string.label_device_condition) to log!!.tinhTrangThietBi,
            stringResource(R.string.label_note) to log!!.ghiChu,
            stringResource(R.string.label_record_id) to recordId
        ).filter { (_, value) -> value.isNotBlank() }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = log!!.maThietBi,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    StatusBadge(status = log!!.repairStatus)
                }
            }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        detailRows.forEach { (label, value) ->
                            DetailRow(label = label, value = value)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { onUpdateRepairDate(recordId) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.detail_update_repair_date_button))
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
