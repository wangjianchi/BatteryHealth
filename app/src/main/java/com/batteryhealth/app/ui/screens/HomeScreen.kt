package com.batteryhealth.app.ui.screens

import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.data.model.getHealthGrade
import com.batteryhealth.app.manager.HealthEstimation
import com.batteryhealth.app.manager.HealthEstimationResult
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.HomeUiState

@Composable
fun HomeScreen(homeState: HomeUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Health Gauge Card
        HealthGaugeCard(homeState)

        // 5 Methods Comparison Card
        if (homeState.healthEstimations != null) {
            HealthMethodsComparisonCard(homeState.healthEstimations)
        }

        // Stats Row
        StatsRow(homeState)

        // Health Tips
        if (homeState.healthPercent != null) {
            HealthTipsCard(homeState.healthPercent)
        }

        // Info Card
        InfoCard()

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun HealthGaugeCard(state: HomeUiState) {
    // 优先使用系统健康度（来自内核数据，最准确），否则用充电估算
    val healthPct = if (state.systemHealthPercent >= 0) state.systemHealthPercent else state.healthPercent
    val grade = healthPct?.let { getHealthGrade(it) }
    val gaugeColor = grade?.let { Color(it.color) } ?: OnSurfaceVariant
    val isSystemHealth = state.systemHealthPercent >= 0

    // Animated sweep angle
    val animatedSweep by animateFloatAsState(
        targetValue = if (healthPct != null) (healthPct / 100f) * 180f else 0f,
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "gauge"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "电池健康度",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurfaceVariant,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Semicircle Gauge
            Box(
                modifier = Modifier
                    .width(220.dp)
                    .height(120.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 20.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, (size.width - strokeWidth))
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    // Background arc
                    drawArc(
                        color = Surface3,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Colored arc
                    drawArc(
                        color = gaugeColor,
                        startAngle = 180f,
                        sweepAngle = animatedSweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Center text
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = healthPct?.toString() ?: "--",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                        color = gaugeColor,
                        lineHeight = 42.sp
                    )
                    Text(
                        text = "%",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (grade != null) "${grade.emoji} ${grade.label}" else "暂无数据",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = gaugeColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isSystemHealth && state.averageCapacity != null && state.latestRatedCapacity != null ->
                        "系统值 ${state.systemHealthPercent}% | 充电估算 ${state.healthPercent ?: "--"}%"
                    isSystemHealth -> "来自系统内核数据"
                    state.averageCapacity != null && state.latestRatedCapacity != null ->
                        "额定 ${state.latestRatedCapacity} mAh，估算 ${state.averageCapacity} mAh"
                    else -> "添加充电记录开始检测"
                },
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun StatsRow(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.BatteryFull,
            iconTint = BatteryGreen,
            value = state.latestRatedCapacity?.let { "${it}" } ?: "--",
            unit = "mAh",
            label = "标称容量"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.ListAlt,
            iconTint = BatteryBlue,
            value = state.recordCount.toString(),
            unit = "次",
            label = "记录次数"
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.CalendarToday,
            iconTint = BatteryPurple,
            value = if (state.recordCount > 0) state.monitoringDays.toString() else "--",
            unit = "天",
            label = "监测天数"
        )
    }

    // 系统健康度（如果有的话）
    if (state.systemHealthPercent >= 0) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Favorite,
                iconTint = if (state.systemHealthPercent >= 80) BatteryGreen else BatteryYellow,
                value = "${state.systemHealthPercent}",
                unit = "%",
                label = "系统健康度"
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.BatteryChargingFull,
                iconTint = BatteryBlue,
                value = state.latestRatedCapacity?.let { "${it}" } ?: "--",
                unit = "mAh",
                label = "设计容量"
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    value: String,
    unit: String,
    label: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                if (value != "--") {
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(unit, fontSize = 10.sp, color = OnSurfaceVariant)
                }
            }
            Text(
                text = label,
                fontSize = 10.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HealthTipsCard(healthPercent: Int) {
    val tips = when {
        healthPercent >= 90 -> listOf(
            "✅" to "电池状态极佳，保持当前使用习惯即可",
            "🔌" to "建议保持电量在 20%–80% 之间充电，延缓老化",
            "🌡️" to "避免手机长时间暴露在高温环境"
        )
        healthPercent >= 80 -> listOf(
            "👍" to "电池状态良好，续航表现接近新机",
            "🔋" to "尽量避免过度放电至 5% 以下再充电",
            "⚡" to "减少超快充频率，适当使用慢充保护电池"
        )
        healthPercent >= 70 -> listOf(
            "⚠️" to "电池开始老化，续航可能缩短约 20–30%",
            "🔄" to "可每月进行一次完整充放电校准电量计",
            "📱" to "开启省电模式，减少后台应用活动"
        )
        healthPercent >= 60 -> listOf(
            "🔶" to "电池老化明显，建议携带充电宝备用",
            "🛠️" to "可联系官方售后评估更换电池",
            "🚫" to "避免高负荷使用时同时充电（如玩游戏）"
        )
        else -> listOf(
            "🔴" to "电池严重老化，强烈建议更换电池",
            "⚠️" to "老化电池存在异常发热或膨胀风险",
            "🏪" to "前往品牌授权店进行专业检测与更换服务"
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "健康建议",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            tips.forEach { (emoji, text) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(emoji, fontSize = 15.sp, modifier = Modifier.padding(end = 10.dp, top = 1.dp))
                    Text(
                        text = text,
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 19.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BatteryBlueDim)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = BatteryBlue,
                modifier = Modifier
                    .size(16.dp)
                    .padding(top = 1.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "通过「充电前电量」「充电后电量」「充电时长」三个参数，利用专业算法估算电池实际容量。建议累积多次记录以提升准确度。",
                fontSize = 12.sp,
                color = BatteryBlue,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun HealthMethodsComparisonCard(estimations: HealthEstimationResult) {
    val allEstimations = listOf(
        estimations.systemDesign,
        estimations.systemCurrent,
        estimations.powerIntegral,
        estimations.percentBased,
        estimations.historyWeighted
    )

    val validEstimations = allEstimations.filter { it.value > 0 }
    val avgValue = estimations.average
    val medianValue = estimations.median

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Compare,
                        contentDescription = null,
                        tint = BatteryPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "健康度估算对比",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }
                if (avgValue != null) {
                    Text(
                        "均值 ${avgValue}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = BatteryPurple
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Each method row
            allEstimations.forEach { est ->
                HealthMethodRow(est)
                if (est != allEstimations.last()) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Summary
            if (validEstimations.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = Surface3)
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SummaryItem("均值", avgValue, BatteryBlue)
                    SummaryItem("中位数", medianValue, BatteryGreen)
                    SummaryItem("最高", validEstimations.maxOf { it.value }, BatteryGreen)
                    SummaryItem("最低", validEstimations.minOf { it.value }, BatteryYellow)
                }
            }
        }
    }
}

@Composable
fun HealthMethodRow(est: HealthEstimation) {
    val valueColor = when {
        est.value < 0 -> OnSurfaceVariant
        est.value >= 80 -> BatteryGreen
        est.value >= 60 -> BatteryYellow
        else -> BatteryRed
    }

    val reliabilityIcon = when (est.reliability) {
        1 -> "●"
        2 -> "○"
        else -> "◎"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = reliabilityIcon,
                fontSize = 10.sp,
                color = when (est.reliability) {
                    1 -> BatteryGreen
                    2 -> BatteryYellow
                    else -> OnSurfaceVariant
                }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = est.label,
                fontSize = 13.sp,
                color = if (est.value > 0) OnSurface else OnSurfaceVariant
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (est.capacity > 0) {
                Text(
                    text = "${est.capacity}mAh",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = if (est.value > 0) "${est.value}%" else "--",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor
            )
        }
    }
}

@Composable
fun SummaryItem(label: String, value: Int?, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = OnSurfaceVariant
        )
        Text(
            text = if (value != null) "${value}%" else "--",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
