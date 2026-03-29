package com.batteryhealth.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batteryhealth.app.receiver.BatteryReceiver
import com.batteryhealth.app.ui.screens.*
import com.batteryhealth.app.ui.theme.*
import com.batteryhealth.app.viewmodel.BatteryViewModel

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "概览", Icons.Default.Home)
    object Monitor : Screen("monitor", "监测", Icons.Default.BatteryChargingFull)
    object History : Screen("history", "历史", Icons.Default.History)
    object Trend : Screen("trend", "趋势", Icons.Default.ShowChart)
}

class MainActivity : ComponentActivity() {
    private lateinit var batteryReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 注册电池状态接收器
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == BatteryReceiver.ACTION_BATTERY_UPDATE) {
                    // 通知ViewModel更新电池信息
                }
            }
        }

        setContent {
            BatteryHealthTheme {
                BatteryApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 注册电池广播
        registerReceiver(
            batteryReceiver,
            IntentFilter(BatteryReceiver.ACTION_BATTERY_UPDATE),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onPause() {
        super.onPause()
        // 取消注册电池广播
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // 忽略未注册的异常
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryApp() {
    val viewModel: BatteryViewModel = viewModel()
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Monitor) }
    val homeState by viewModel.homeState.collectAsState()
    val monitorState by viewModel.monitorState.collectAsState()

    // 历史详情页面状态
    var selectedRecord by remember { mutableStateOf<com.batteryhealth.app.data.model.ChargingRecord?>(null) }

    val screens = listOf(Screen.Home, Screen.Monitor, Screen.History, Screen.Trend)

    // 定时更新电池信息
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5000) // 每5秒更新一次
            viewModel.updateBatteryInfo()
        }
    }

    // 初始加载电池信息
    LaunchedEffect(Unit) {
        viewModel.updateBatteryInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentScreen.label.let {
                                when (currentScreen) {
                                    Screen.Home -> "电池健康检测"
                                    Screen.Monitor -> "充电监测"
                                    Screen.History -> "充电历史"
                                    Screen.Trend -> "健康趋势"
                                }
                            },
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        if (currentScreen == Screen.Home && homeState.healthPercent != null) {
                            Text(
                                "已监测 ${homeState.monitoringDays} 天 · ${homeState.recordCount} 次记录",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Battery icon with health color
                    val healthColor = homeState.healthPercent?.let {
                        when {
                            it >= 80 -> BatteryGreen
                            it >= 60 -> BatteryYellow
                            else -> BatteryRed
                        }
                    } ?: OnSurfaceVariant

                    Icon(
                        imageVector = when {
                            (homeState.healthPercent ?: 100) >= 80 -> Icons.Default.BatteryFull
                            (homeState.healthPercent ?: 100) >= 50 -> Icons.Default.Battery3Bar
                            else -> Icons.Default.Battery1Bar
                        },
                        contentDescription = null,
                        tint = healthColor,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(26.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Background,
                    titleContentColor = OnSurface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Surface,
                tonalElevation = 0.dp
            ) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.label,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(screen.label, fontSize = 11.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BatteryPurple,
                            selectedTextColor = BatteryPurple,
                            unselectedIconColor = OnSurfaceVariant,
                            unselectedTextColor = OnSurfaceVariant,
                            indicatorColor = BatteryPurple.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        },
        containerColor = Background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            // 如果有选中的记录，显示详情页面
            selectedRecord?.let { record ->
                HistoryDetailScreen(
                    record = record,
                    onBack = { selectedRecord = null }
                )
            } ?: when (currentScreen) {
                Screen.Home -> HomeScreen(homeState)
                Screen.Monitor -> ChargingMonitorScreen(
                    monitorState = monitorState,
                    onStartCharging = { capacity ->
                        viewModel.startChargingSession(capacity)
                    },
                    onEndCharging = {
                        viewModel.endChargingSession()
                    },
                    onSaveSession = {
                        viewModel.saveChargingSession()
                    },
                    onClearSession = {
                        viewModel.clearCompletedSession()
                    },
                    onToggleAutoRecord = { enabled ->
                        viewModel.toggleAutoRecord(enabled)
                    }
                )
                Screen.History -> HistoryScreen(
                    viewModel = viewModel,
                    onRecordClick = { record ->
                        selectedRecord = record
                    }
                )
                Screen.Trend -> TrendScreen(viewModel)
            }
        }
    }
}
