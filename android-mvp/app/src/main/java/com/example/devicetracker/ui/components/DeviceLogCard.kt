package com.example.devicetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.devicetracker.R
import com.example.devicetracker.domain.model.DeviceLog
import com.example.devicetracker.util.DateTextFormatter

@Composable
fun DeviceLogCard(
    item: DeviceLog,
    displayIndex: Int? = null,
    onClick: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    displayIndex?.let { index ->
                        Text(
                            text = "${stringResource(R.string.label_index)} $index",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF667176)
                        )
                    }
                    Text(
                        text = item.maThietBi,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF0B7FB2),
                        fontWeight = FontWeight.Black
                    )
                    if (item.hangMuc.isNotBlank()) {
                        Text(
                            text = item.hangMuc,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF0E7D73)
                        )
                    }
                }
                StatusBadge(status = item.repairStatus)
            }

            if (item.tinhTrangThietBi.isNotBlank()) {
                Text(
                    text = item.tinhTrangThietBi,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF202526)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                DateMetaBlock(
                    icon = Icons.Default.CalendarMonth,
                    title = stringResource(R.string.label_discovery_date),
                    value = DateTextFormatter.formatForDisplay(item.ngayPhatHien),
                    modifier = Modifier.weight(1f)
                )
                DateMetaBlock(
                    icon = Icons.Default.Construction,
                    title = stringResource(R.string.label_repair_date),
                    value = DateTextFormatter.formatForDisplay(item.ngaySuaChua),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DateMetaBlock(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF7D858A)
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF1F2527)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF111517)
            )
        }
    }
}
