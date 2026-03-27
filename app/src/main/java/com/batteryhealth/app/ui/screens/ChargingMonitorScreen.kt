package com.batteryhealth.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batteryhealth.app.data.model.ChargingSessionState
import com.batteryhealth.app.manager.BatteryInfo
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.ChargingMonitorUiState

@Composable
fun ChargingMonitorScreen(
    monitorState: ChargingMonitorUiState,
    onStartCharging: (Int) -> Unit = {},
    onEndCharging: () -> Unit = {},
    onSaveSession: () -> Unit = {},
    onClearSession: () -> Unit = {},
    onToggleAutoRecord: (Boolean) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 电池状态卡片
        BatteryStatusCard(monitorState.batteryInfo)

        // 充电会话卡片
        when (val sessionState = monitorState.sessionState) {
            is ChargingSessionState.NotCharging -> {
                NotChargingCard(
                    ratedCapacity = monitorState.batteryInfo.ratedCapacity,
                    onStartCharging = onStartCharging
                )
            }
            is ChargingSessionState.Charging -> {
                ChargingSessionCard(
                    session = sessionState.session,
                    onEndCharging = onEndCharging
                )
            }
            is ChargingSessionState.Completed -> {
                CompletedSessionCard(
                    session = sessionState.session,
                    onSaveSession = onSaveSession,
                    onClearSession = onClearSession
                )
            }
        }

        // 自动记录开关
        AutoRecordSwitchCard(
            enabled = monitorState.autoRecordEnabled,
            onToggle = onToggleAutoRecord
        )

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun BatteryStatusCard(batteryInfo: BatteryInfo) {
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
            // 电池图标和百分比
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        when {
                            batteryInfo.batteryPercent >= 60f -> BatteryGreenDim
                            batteryInfo.batteryPercent >= 30f -> BatteryYellowDim
                            else -> BatteryRedDim
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = when {
                            batteryInfo.isCharging -> Icons.Default.BatteryChargingFull
                            batteryInfo.batteryPercent >= 80f -> Icons.Default.BatteryFull
                            batteryInfo.batteryPercent >= 40f -> Icons.Default.Battery3Bar
                            batteryInfo.batteryPercent >= 20f -> Icons.Default.Battery2Bar
                            else -> Icons.Default.Battery1Bar
                        },
                        contentDescription = null,
                        tint = when {
                            batteryInfo.batteryPercent >= 60f -> BatteryGreen
                            batteryInfo.batteryPercent >= 30f -> BatteryYellow
                            else -> BatteryRed
                        },
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${batteryInfo.batteryPercent.toInt()}%",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            batteryInfo.batteryPercent >= 60f -> BatteryGreen
                            batteryInfo.batteryPercent >= 30f -> BatteryYellow
                            else -> BatteryRed
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 状态文本
            Text(
                text = batteryInfo.chargingStatusText,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )

            // 显示电芯类型
            if (batteryInfo.isDualBattery) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚡ ${batteryInfo.batteryTypeText} - ${batteryInfo.chargingPowerText}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = BatteryBlue
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 详细信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BatteryInfoItem(
                    imageVector = Icons.Default.Power,
                    label = "功率",
                    value = if (batteryInfo.chargingPower > 0)
                        batteryInfo.chargingPowerText else "--"
                )
                BatteryInfoItem(
                    imageVector = Icons.Default.ElectricalServices,
                    label = "电压",
                    value = if (batteryInfo.voltageV > 0)
                        "${String.format("%.1f", batteryInfo.voltageV)}V" else "--"
                )
                BatteryInfoItem(
                    imageVector = Icons.Default.Thermostat,
                    label = "温度",
                    value = if (batteryInfo.temperatureC > 0)
                        "${String.format("%.1f", batteryInfo.temperatureC)}°C" else "--"
                )
            }

            // 双电芯提示
            if (batteryInfo.isDualBattery) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BatteryBlueDim)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = BatteryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "双电芯并联充电，功率已自动修正",
                            fontSize = 12.sp,
                            color = BatteryBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BatteryInfoItem(
    imageVector: ImageVector,
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
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
fun NotChargingCard(
    ratedCapacity: Int,
    onStartCharging: (Int) -> Unit
) {
    var showCapacityDialog by remember { mutableStateOf(false) }
    var inputCapacity by remember { mutableStateOf(ratedCapacity.toString()) }

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
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                tint = OnSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "未在充电",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "连接充电器后可自动监测充电数据",
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (ratedCapacity > 0) {
                Button(
                    onClick = { onStartCharging(ratedCapacity) },
                    colors = ButtonDefaults.buttonColors(containerColor = BatteryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("开始监测 (容量: ${ratedCapacity}mAh)")
                }
            } else {
                OutlinedButton(
                    onClick = { showCapacityDialog = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BatteryBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("设置电池容量后开始监测")
                }
            }
        }
    }

    if (showCapacityDialog) {
        AlertDialog(
            onDismissRequest = { showCapacityDialog = false },
            title = { Text("设置电池容量") },
            text = {
                OutlinedTextField(
                    value = inputCapacity,
                    onValueChange = { inputCapacity = it },
                    label = { Text("标称容量 (mAh)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        inputCapacity.toIntOrNull()?.let { capacity ->
                            if (capacity > 0) {
                                onStartCharging(capacity)
                                showCapacityDialog = false
                            }
                        }
                    }
                ) {
                    Text("开始监测")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCapacityDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ChargingSessionCard(
    session: com.batteryhealth.app.data.model.ChargingSession,
    onEndCharging: () -> Unit
) {
    val isFullyCharged = session.currentPercent >= 100f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullyCharged) BatteryGreenDim else BatteryGreenDim
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFullyCharged) Icons.Default.BatteryFull else Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = BatteryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFullyCharged) "已充满" else "正在充电",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatteryGreen
                    )
                }

                if (!isFullyCharged) {
                    // 充电动画指示器
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = BatteryGreen,
                        strokeWidth = 2.dp,
                        trackColor = Color.Transparent
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BatteryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 充电进度
            LinearProgressIndicator(
                progress = { session.currentPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = BatteryGreen,
                trackColor = Color.Transparent
            )

            // 详细数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SessionDataItem(
                    label = "已充",
                    value = "${session.chargedPercent.toInt()}%",
                    imageVector = Icons.Default.TrendingUp
                )
                SessionDataItem(
                    label = "时长",
                    value = session.chargingDurationText,
                    imageVector = Icons.Default.AccessTime
                )
                SessionDataItem(
                    label = "已充入",
                    value = "${session.chargedAmount}mAh",
                    imageVector = Icons.Default.BatteryStd
                )
            }

            // 平均功率
            if (session.averagePower > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "平均功率",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", session.averagePower)}W",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            // 预计剩余时间
            session.estimatedRemainingText?.let { remaining ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "预计充满",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    Text(
                        text = remaining,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatteryBlue
                    )
                }
            }

            // 结束按钮
            Button(
                onClick = onEndCharging,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Surface,
                    contentColor = OnSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("结束监测")
            }
        }
    }
}

@Composable
fun CompletedSessionCard(
    session: com.batteryhealth.app.data.model.ChargingSession,
    onSaveSession: () -> Unit,
    onClearSession: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = BatteryGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "充电完成",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = BatteryGreen
                    )
                }
            }

            // 详细数据
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SessionDataItem(
                    label = "起始",
                    value = "${session.startPercent.toInt()}%",
                    imageVector = Icons.Default.Battery1Bar
                )
                SessionDataItem(
                    label = "结束",
                    value = "${session.currentPercent.toInt()}%",
                    imageVector = Icons.Default.BatteryFull
                )
                SessionDataItem(
                    label = "总充入",
                    value = "${session.chargedAmount}mAh",
                    imageVector = Icons.Default.BatteryStd
                )
            }

            // 充电时长
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "充电时长",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
                Text(
                    text = session.chargingDurationText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

            // 平均功率
            if (session.averagePower > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Power,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "平均功率",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                    Text(
                        text = "${String.format("%.1f", session.averagePower)}W",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onClearSession,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = OnSurfaceVariant
                    )
                ) {
                    Text("清除")
                }
                Button(
                    onClick = onSaveSession,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = BatteryBlue)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("保存记录")
                }
            }
        }
    }
}

@Composable
fun SessionDataItem(
    label: String,
    value: String,
    imageVector: ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = OnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
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
fun AutoRecordSwitchCard(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoMode,
                    contentDescription = null,
                    tint = BatteryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "自动保存充电记录",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface
                    )
                    Text(
                        text = "充电完成后自动保存（需充电量≥5%）",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = BatteryBlue,
                    checkedTrackColor = BatteryBlueDim,
                    uncheckedThumbColor = OnSurfaceVariant,
                    uncheckedTrackColor = Surface3
                )
            )
        }
    }
}
