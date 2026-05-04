package com.example.devicetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.devicetracker.R
import com.example.devicetracker.domain.model.RepairFilter

@Composable
fun StatusBadge(
    status: RepairFilter,
    modifier: Modifier = Modifier
) {
    val isRepaired = status == RepairFilter.REPAIRED
    val background = if (isRepaired) Color(0xFFCFF7DC) else Color(0xFFFFE7A8)
    val foreground = if (isRepaired) Color(0xFF145B31) else Color(0xFF6E4800)
    val label = if (isRepaired) {
        stringResource(R.string.filter_repaired)
    } else {
        stringResource(R.string.filter_pending)
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = foreground,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
