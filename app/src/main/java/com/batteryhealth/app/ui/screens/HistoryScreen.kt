package com.batteryhealth.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.data.model.ChargingRecord
import com.batteryhealth.app.data.model.getHealthGrade
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.BatteryViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(
    viewModel: BatteryViewModel,
    onRecordClick: (ChargingRecord) -> Unit = {}
) {
    val records by viewModel.recordsDesc.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "共 ${records.size} 条记录（点击查看详情）",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
        }

        if (records.isEmpty()) {
            EmptyHistoryState()
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    HistoryItem(
                        record = record,
                        onDelete = { viewModel.deleteRecord(record) },
                        onClick = { onRecordClick(record) }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun HistoryItem(
    record: ChargingRecord,
    onDelete: () -> Unit,
    onClick: () -> Unit = {}
) {
    val grade = getHealthGrade(record.healthPercent)
    val gradeColor = Color(grade.color)
    val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.CHINA)
    val dateStr = sdf.format(Date(record.timestamp))

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除记录", fontSize = 16.sp) },
            text = { Text("确认删除该充电记录？此操作无法撤销。", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {
                    Text("删除", color = BatteryRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消", color = OnSurfaceVariant)
                }
            },
            containerColor = Surface2
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(gradeColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(grade.emoji, fontSize = 20.sp)
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateStr + if (record.note.isNotBlank()) " · ${record.note}" else "",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${record.batteryBefore}% → ${record.batteryAfter}%  ·  ${record.chargingMinutes}min" +
                            (record.chargingWatt?.let { "  ·  ${it}W" } ?: ""),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "估算：${record.estimatedCapacity} mAh / ${record.ratedCapacity} mAh",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                )
            }

            // Right column
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 详情图标
                if (record.hasDetailedData) {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "查看详情",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Health badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(gradeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${record.healthPercent}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = gradeColor
                    )
                }
                // Delete button
                IconButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "删除",
                        tint = OnSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📋", fontSize = 48.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "暂无充电记录",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "点击「记录」标签\n添加第一条充电数据",
            fontSize = 14.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
