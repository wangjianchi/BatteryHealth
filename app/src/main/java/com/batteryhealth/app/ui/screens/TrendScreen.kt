package com.batteryhealth.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.data.model.ChargingRecord
import com.batteryhealth.app.data.model.getHealthGrade
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.BatteryViewModel

@Composable
fun TrendScreen(viewModel: BatteryViewModel) {
    val records by viewModel.recordsAsc.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Trend Chart
        TrendChartCard(records)

        // Stats
        TrendStatsCard(records)

        // Distribution
        if (records.isNotEmpty()) {
            HealthDistributionCard(records)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun TrendChartCard(records: List<ChargingRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("健康度变化趋势", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (records.size >= 2) "共 ${records.size} 次记录" else "添加更多记录以查看趋势",
                fontSize = 12.sp, color = OnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (records.size < 2) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("📊 暂无足够数据", fontSize = 14.sp, color = OnSurfaceVariant)
                }
            } else {
                HealthLineChart(records = records, modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp))
            }
        }
    }
}

@Composable
fun HealthLineChart(records: List<ChargingRecord>, modifier: Modifier = Modifier) {
    val healthValues = records.map { it.healthPercent.toFloat() }
    val minVal = (healthValues.min() - 10f).coerceAtLeast(0f)
    val maxVal = (healthValues.max() + 5f).coerceAtMost(100f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val paddingLeft = 40.dp.toPx()
        val paddingBottom = 30.dp.toPx()
        val chartW = w - paddingLeft - 16.dp.toPx()
        val chartH = h - paddingBottom - 10.dp.toPx()

        // Grid lines
        val gridSteps = 4
        repeat(gridSteps + 1) { i ->
            val y = 10.dp.toPx() + (i.toFloat() / gridSteps) * chartH
            drawLine(
                color = Surface3,
                start = Offset(paddingLeft, y),
                end = Offset(w - 16.dp.toPx(), y),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Points
        val pts = records.mapIndexed { i, r ->
            val x = paddingLeft + (i.toFloat() / (records.size - 1)) * chartW
            val normalizedY = 1f - ((r.healthPercent.toFloat() - minVal) / (maxVal - minVal))
            val y = 10.dp.toPx() + normalizedY * chartH
            Offset(x, y)
        }

        // Filled area under line
        val fillPath = Path().apply {
            moveTo(pts.first().x, h - paddingBottom)
            pts.forEach { lineTo(it.x, it.y) }
            lineTo(pts.last().x, h - paddingBottom)
            close()
        }
        drawPath(fillPath, color = BatteryPurple.copy(alpha = 0.08f))

        // Line
        val linePath = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val cp1x = (pts[i-1].x + pts[i].x) / 2
                cubicTo(cp1x, pts[i-1].y, cp1x, pts[i].y, pts[i].x, pts[i].y)
            }
        }
        drawPath(linePath, color = BatteryPurple, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))

        // Points
        pts.forEachIndexed { i, pt ->
            val grade = getHealthGrade(records[i].healthPercent)
            val dotColor = Color(grade.color)
            drawCircle(color = Surface, radius = 7.dp.toPx(), center = pt)
            drawCircle(color = dotColor, radius = 5.dp.toPx(), center = pt)
        }
    }
}

@Composable
fun TrendStatsCard(records: List<ChargingRecord>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "统计分析",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            val maxCap = records.maxOfOrNull { it.estimatedCapacity }
            val minCap = records.minOfOrNull { it.estimatedCapacity }
            val avgHealth = if (records.isEmpty()) null
            else records.sumOf { it.healthPercent } / records.size

            val decayTrend = if (records.size >= 2) {
                val first = records.first().healthPercent
                val last = records.last().healthPercent
                val diff = last - first
                when {
                    diff < -2 -> "↓ 衰减 ${kotlin.math.abs(diff)}%"
                    diff > 2 -> "↑ 回升 ${diff}%"
                    else -> "→ 基本稳定"
                }
            } else "—"

            listOf(
                Triple(Icons.Default.ListAlt, "记录次数", "${records.size} 次"),
                Triple(Icons.Default.TrendingUp, "最高估算容量", maxCap?.let { "${it} mAh" } ?: "—"),
                Triple(Icons.Default.TrendingDown, "最低估算容量", minCap?.let { "${it} mAh" } ?: "—"),
                Triple(Icons.Default.Percent, "平均健康度", avgHealth?.let { "${it}%" } ?: "—"),
                Triple(Icons.Default.ShowChart, "衰减趋势", decayTrend)
            ).forEachIndexed { index, (icon, label, value) ->
                if (index > 0) HorizontalDivider(color = Border, modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                        Text(label, fontSize = 13.sp, color = OnSurfaceVariant)
                    }
                    Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                }
            }
        }
    }
}

@Composable
fun HealthDistributionCard(records: List<ChargingRecord>) {
    val excellent = records.count { it.healthPercent >= 90 }
    val good = records.count { it.healthPercent in 80..89 }
    val fair = records.count { it.healthPercent in 70..79 }
    val poor = records.count { it.healthPercent < 70 }
    val total = records.size.toFloat()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "健康度分布",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(14.dp))

            listOf(
                Triple("优秀 ≥90%", excellent, BatteryGreen),
                Triple("良好 80–89%", good, Color(0xFF4CAF50)),
                Triple("一般 70–79%", fair, BatteryYellow),
                Triple("较差 <70%", poor, BatteryRed)
            ).forEach { (label, count, color) ->
                if (count > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(label, fontSize = 12.sp, color = OnSurfaceVariant, modifier = Modifier.width(90.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(8.dp)
                                .background(Surface3, RoundedCornerShape(4.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(count / total)
                                    .background(color, RoundedCornerShape(4.dp))
                            )
                        }
                        Text(
                            "$count",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = color,
                            modifier = Modifier.width(24.dp)
                        )
                    }
                }
            }
        }
    }
}
