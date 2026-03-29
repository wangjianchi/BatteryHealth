package com.batteryhealth.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.data.model.ChargingDataPoint
import com.batteryhealth.app.data.model.ChargingRecord
import com.batteryhealth.app.data.model.ChargingDataPointsJson
import com.batteryhealth.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    record: ChargingRecord,
    onBack: () -> Unit
) {
    // 解析数据点
    val dataPoints = remember(record.dataPointsJson) {
        if (record.hasDetailedData && record.dataPointsJson != "[]") {
            ChargingDataPointsJson.fromJson(record.dataPointsJson)
        } else {
            emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "充电详情",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface,
                    titleContentColor = OnSurface
                )
            )
        },
        containerColor = Background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 基本信息卡片
            BasicInfoCard(record)

            // 统计卡片
            StatsCard(record, dataPoints)

            // 电量与功率双 Y 轴折线图
            if (dataPoints.isNotEmpty()) {
                DualAxisChartCard(dataPoints)
            } else {
                NoDetailedDataCard()
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
fun BasicInfoCard(record: ChargingRecord) {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    val date = Date(record.timestamp)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        tint = BatteryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = dateFormat.format(date),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface
                    )
                }
                HealthBadge(record.healthPercent)
            }

            HorizontalDivider(color = Surface3)

            // 电量变化
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoItem(
                    icon = Icons.Default.Battery1Bar,
                    label = "起始电量",
                    value = "${record.batteryBefore}%",
                    color = BatteryYellow
                )
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                InfoItem(
                    icon = Icons.Default.BatteryFull,
                    label = "结束电量",
                    value = "${record.batteryAfter}%",
                    color = BatteryGreen
                )
                InfoItem(
                    icon = Icons.Default.TrendingUp,
                    label = "充电量",
                    value = "${record.batteryAfter - record.batteryBefore}%",
                    color = BatteryBlue
                )
            }
        }
    }
}

@Composable
fun HealthBadge(healthPercent: Int) {
    val color = when {
        healthPercent >= 90 -> BatteryGreen
        healthPercent >= 80 -> Color(0xFF4CAF50)
        healthPercent >= 70 -> BatteryYellow
        healthPercent >= 60 -> Color(0xFFFF7043)
        else -> BatteryRed
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Text(
            text = "${healthPercent}%",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun InfoItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = OnSurfaceVariant
        )
    }
}

@Composable
fun StatsCard(record: ChargingRecord, dataPoints: List<ChargingDataPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "充电统计",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "充电时长",
                    value = formatDuration(record.chargingMinutes),
                    icon = Icons.Default.AccessTime
                )
                StatItem(
                    label = "平均功率",
                    value = if (record.chargingWatt != null) "${record.chargingWatt.toInt()}W" else "--",
                    icon = Icons.Default.Power
                )
                StatItem(
                    label = "标称容量",
                    value = "${record.ratedCapacity}mAh",
                    icon = Icons.Default.BatteryStd
                )
                StatItem(
                    label = "估算容量",
                    value = "${record.estimatedCapacity}mAh",
                    icon = Icons.Default.Calculate
                )
            }

            if (dataPoints.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Surface3)

                val powerStats = calculatePowerStats(dataPoints)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        label = "最大功率",
                        value = "${powerStats.maxPower.toInt()}W",
                        icon = Icons.Default.TrendingUp,
                        color = BatteryGreen
                    )
                    StatItem(
                        label = "最小功率",
                        value = "${powerStats.minPower.toInt()}W",
                        icon = Icons.Default.TrendingDown,
                        color = BatteryYellow
                    )
                    StatItem(
                        label = "数据点数",
                        value = "${dataPoints.size}次",
                        icon = Icons.Default.DataUsage
                    )
                }
            }
        }
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color = OnSurfaceVariant
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = OnSurfaceVariant
        )
    }
}

@Composable
fun DualAxisChartCard(dataPoints: List<ChargingDataPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "充电过程",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            DualAxisChart(dataPoints)
        }
    }
}

@Composable
fun DualAxisChart(dataPoints: List<ChargingDataPoint>) {
    if (dataPoints.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // 图例
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LegendItem(color = BatteryGreen, label = "电量")
            LegendItem(color = BatteryBlue, label = "功率")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface3.copy(alpha = 0.3f))
        ) {
            val width = size.width
            val height = size.height
            val leftPadding = 40f
            val rightPadding = 40f
            val topPadding = 16f
            val bottomPadding = 32f
            val chartWidth = width - leftPadding - rightPadding
            val chartHeight = height - topPadding - bottomPadding

            val baseTime = dataPoints.first().timestamp

            // 电量 (左Y轴)
            val batteryValues = dataPoints.map { it.batteryPercent }
            val minBattery = 0f
            val maxBattery = 100f

            // 功率 (右Y轴)
            val powerValues = dataPoints.map { it.chargingPower }.filter { it > 0 }
            val minPower = 0f
            val maxPower = (powerValues.maxOrNull() ?: 100f) * 1.1f

            // 绘制水平网格线
            val gridCount = 5
            for (i in 0..gridCount) {
                val y = topPadding + chartHeight * i / gridCount
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(leftPadding, y),
                    end = Offset(width - rightPadding, y),
                    strokeWidth = 1f
                )
            }

            // 绘制电量折线
            val batteryPath = Path()
            dataPoints.forEachIndexed { index, point ->
                val x = leftPadding + chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1)
                val ratio = (point.batteryPercent - minBattery) / (maxBattery - minBattery)
                val y = topPadding + chartHeight * (1 - ratio)
                if (index == 0) batteryPath.moveTo(x, y) else batteryPath.lineTo(x, y)
                drawCircle(color = BatteryGreen, radius = 3f, center = Offset(x, y))
            }
            drawPath(path = batteryPath, color = BatteryGreen, style = Stroke(width = 2.5f))

            // 绘制功率折线
            val powerIndices = dataPoints.mapIndexedNotNull { index, point ->
                if (point.chargingPower > 0) index else null
            }
            if (powerIndices.size >= 2) {
                val powerPath = Path()
                dataPoints.forEachIndexed { index, point ->
                    if (point.chargingPower > 0) {
                        val x = leftPadding + chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1)
                        val ratio = (point.chargingPower - minPower) / (maxPower - minPower)
                        val y = topPadding + chartHeight * (1 - ratio)
                        if (powerIndices.indexOf(index) == 0) {
                            powerPath.moveTo(x, y)
                        } else {
                            powerPath.lineTo(x, y)
                        }
                        drawCircle(color = BatteryBlue, radius = 3f, center = Offset(x, y))
                    }
                }
                drawPath(path = powerPath, color = BatteryBlue, style = Stroke(width = 2.5f))
            }

            // 左Y轴标签 (电量)
            for (i in 0..4) {
                val y = topPadding + chartHeight * i / 4
                val label = "${(100 - 25 * i)}%"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    leftPadding - 8f,
                    y + 4f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#4CAF50")
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                )
            }

            // 右Y轴标签 (功率)
            for (i in 0..4) {
                val y = topPadding + chartHeight * i / 4
                val label = "${(maxPower * (1 - i / 4f)).toInt()}W"
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    width - rightPadding + 8f,
                    y + 4f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#2196F3")
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.LEFT
                    }
                )
            }

            // X轴标签 (时间)
            val labelCount = minOf(dataPoints.size, 6)
            for (i in 0 until labelCount) {
                val index = (dataPoints.size - 1) * i / (labelCount - 1).coerceAtLeast(1)
                val point = dataPoints[index]
                val x = leftPadding + chartWidth * index / (dataPoints.size - 1).coerceAtLeast(1)
                val diffSec = (point.timestamp - baseTime) / 1000
                val label = if (diffSec >= 3600) {
                    String.format("%d:%02d:%02d", diffSec / 3600, (diffSec % 3600) / 60, diffSec % 60)
                } else {
                    String.format("%d:%02d", diffSec / 60, diffSec % 60)
                }
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    x,
                    height - 4f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.GRAY
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }

        // 时间范围说明
        if (dataPoints.size >= 2) {
            val start = dataPoints.first().timestamp
            val end = dataPoints.last().timestamp
            val totalSec = (end - start) / 1000
            val totalStr = if (totalSec >= 3600) {
                String.format("总时长: %d:%02d:%02d", totalSec / 3600, (totalSec % 3600) / 60, totalSec % 60)
            } else {
                String.format("总时长: %d:%02d", totalSec / 60, totalSec % 60)
            }
            Text(
                text = totalStr,
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(modifier = Modifier.size(24.dp)) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2),
                end = Offset(size.width, size.height / 2),
                strokeWidth = 3f
            )
            drawCircle(color = color, radius = 4f, center = Offset(size.width / 2, size.height / 2))
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 12.sp, color = OnSurfaceVariant)
    }
}

@Composable
fun NoDetailedDataCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "无详细充电数据",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "此记录为手动录入，未记录详细充电过程",
                fontSize = 13.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

data class PowerStats(
    val maxPower: Float,
    val minPower: Float,
    val avgPower: Float
)

fun calculatePowerStats(dataPoints: List<ChargingDataPoint>): PowerStats {
    if (dataPoints.isEmpty()) return PowerStats(0f, 0f, 0f)

    val powers = dataPoints.map { it.chargingPower }.filter { it > 0 }
    if (powers.isEmpty()) return PowerStats(0f, 0f, 0f)

    return PowerStats(
        maxPower = powers.maxOrNull() ?: 0f,
        minPower = powers.minOrNull() ?: 0f,
        avgPower = powers.average().toFloat()
    )
}

fun formatDuration(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 -> "${hours}h${mins}m"
        mins > 0 -> "${mins}m"
        else -> "<1m"
    }
}
