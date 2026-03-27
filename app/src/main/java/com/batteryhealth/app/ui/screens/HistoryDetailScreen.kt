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

            // 电量变化折线图
            if (dataPoints.isNotEmpty()) {
                BatteryChartCard(dataPoints)
                PowerChartCard(dataPoints)
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
fun BatteryChartCard(dataPoints: List<ChargingDataPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "电量变化曲线",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LineChart(
                dataPoints = dataPoints,
                valueSelector = { it.batteryPercent },
                color = BatteryGreen,
                label = "电量 %"
            )
        }
    }
}

@Composable
fun PowerChartCard(dataPoints: List<ChargingDataPoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "功率变化曲线",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            LineChart(
                dataPoints = dataPoints,
                valueSelector = { it.chargingPower },
                color = BatteryBlue,
                label = "功率 W"
            )
        }
    }
}

@Composable
fun LineChart(
    dataPoints: List<ChargingDataPoint>,
    valueSelector: (ChargingDataPoint) -> Float,
    color: Color,
    label: String
) {
    if (dataPoints.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        // 图表
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Surface3.copy(alpha = 0.3f))
        ) {
            val width = size.width
            val height = size.height
            val padding = 40f

            val values = dataPoints.map { valueSelector(it) }
            val minValue = values.minOrNull() ?: 0f
            val maxValue = values.maxOrNull() ?: 100f
            val valueRange = (maxValue - minValue).coerceAtLeast(1f)

            // 绘制网格线
            for (i in 0..4) {
                val y = padding + (height - 2 * padding) * i / 4
                drawLine(
                    color = Color.Gray.copy(alpha = 0.2f),
                    start = Offset(padding, y),
                    end = Offset(width - padding, y),
                    strokeWidth = 1f
                )
            }

            // 绘制折线
            val path = Path()
            dataPoints.forEachIndexed { index, point ->
                val x = padding + (width - 2 * padding) * index / (dataPoints.size - 1).coerceAtLeast(1)
                val y = padding + (height - 2 * padding) * (1 - (valueSelector(point) - minValue) / valueRange)

                if (index == 0) {
                    path.moveTo(x, y)
                } else {
                    path.lineTo(x, y)
                }

                // 绘制数据点
                drawCircle(
                    color = color,
                    radius = 4f,
                    center = Offset(x, y)
                )
            }

            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 3f)
            )
        }

        // Y轴标签
        if (dataPoints.isNotEmpty()) {
            val values = dataPoints.map { valueSelector(it) }
            val maxValue = values.maxOrNull() ?: 100f
            val minValue = values.minOrNull() ?: 0f

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    fontSize = 10.sp,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "${((maxValue + minValue) / 2).toInt()} $label",
                    fontSize = 10.sp,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "${maxValue.toInt()} $label",
                    fontSize = 10.sp,
                    color = OnSurfaceVariant
                )
            }

            // 时间范围
            if (dataPoints.size >= 2) {
                val startTime = formatTimeFromTimestamp(dataPoints.first().timestamp, dataPoints.first().timestamp)
                val endTime = formatTimeFromTimestamp(dataPoints.last().timestamp, dataPoints.first().timestamp)
                Text(
                    text = "$startTime → $endTime",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

fun formatTimeFromTimestamp(timestamp: Long, baseTimestamp: Long): String {
    val diffSeconds = kotlin.math.abs(timestamp - baseTimestamp) / 1000
    val diffMinutes = diffSeconds / 60
    val hours = (diffMinutes / 60).toInt()
    val mins = (diffMinutes % 60).toInt()
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, (diffSeconds % 60).toInt())
    } else {
        String.format("%02d:%02d", mins, (diffSeconds % 60).toInt())
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
