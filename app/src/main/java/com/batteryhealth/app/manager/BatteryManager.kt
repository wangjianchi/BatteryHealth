package com.batteryhealth.app.manager

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * 电池管理器 - 获取实时电池信息
 * 支持双电芯检测和准确的功率计算
 */
class BatteryManager(private val context: Context) {

    companion object {
        private const val TAG = "BatteryManager"

        // 已知双电芯设备列表（品牌前缀）
        private val DUAL_BATTERY_BRANDS = listOf(
            " OnePlus", // 一加
            " OPLO", // OPPO高端机型
            " realme", // realme高端机型
            " vivo", // vivo高端机型
            " Xiaomi", // 小米高端机型
            " Redmi", // 红米高端机型
            " ASUS", // 华硕游戏手机
            " ROG", // ROG游戏手机
            " Black Shark", // 黑鲨游戏手机
            " Nubia", // 红魔游戏手机
            " Lenovo" // 联想游戏手机
        )
    }

    /**
     * 检测是否为双电芯设备
     */
    fun isDualBatteryDevice(): Boolean {
        // 方法1: 通过Build信息判断
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        val product = Build.PRODUCT

        Log.d(TAG, "检测设备: 制造商=$manufacturer, 型号=$model, 产品=$product")

        // 检查是否在已知双电芯品牌列表中
        if (DUAL_BATTERY_BRANDS.any { manufacturer.contains(it, ignoreCase = true) }) {
            // 进一步检查是否为高端机型（双电芯通常用在高端机型）
            val isHighEnd = when {
                product.contains("RD", ignoreCase = true) -> true // 一加高端
                product.contains("PF", ignoreCase = true) -> true // OPPO高端
                model.contains("Pro", ignoreCase = true) -> true // Pro版本
                model.contains("Ultra", ignoreCase = true) -> true // Ultra版本
                model.contains("Gaming", ignoreCase = true) -> true // 游戏手机
                model.contains("ROG", ignoreCase = true) -> true // ROG
                model.contains("Black Shark", ignoreCase = true) -> true // 黑鲨
                model.contains("Red Magic", ignoreCase = true) -> true // 红魔
                else -> false
            }

            if (isHighEnd) {
                Log.d(TAG, "检测到双电芯设备（基于型号）")
                return true
            }
        }

        // 方法2: 通过电池容量判断（超过5500mAh很可能是双电芯）
        val capacity = getRatedCapacity()
        if (capacity > 5500) {
            Log.d(TAG, "检测到双电芯设备（基于容量: ${capacity}mAh）")
            return true
        }

        // 方法3: 通过充电功率判断（支持60W以上很可能是双电芯）
        val maxChargePower = getMaxChargePower()
        if (maxChargePower > 60f) {
            Log.d(TAG, "检测到双电芯设备（基于充电功率: ${maxChargePower}W）")
            return true
        }

        Log.d(TAG, "单电芯设备")
        return false
    }

    /**
     * 获取设备支持的最大充电功率（W）
     */
    private fun getMaxChargePower(): Float {
        // 这里可以根据设备型号返回已知值
        // 暂时返回保守估计值
        return when {
            Build.MANUFACTURER.contains("OnePlus", ignoreCase = true) -> 80f
            Build.MANUFACTURER.contains("OPPO", ignoreCase = true) -> 80f
            Build.MANUFACTURER.contains("Xiaomi", ignoreCase = true) -> 120f
            Build.MANUFACTURER.contains("ASUS", ignoreCase = true) -> 65f
            else -> 33f
        }
    }

    /**
     * 获取当前电池信息
     */
    fun getBatteryInfo(): BatteryInfo {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryInfo()

        val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) // 毫伏
        val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) // 十分之一摄氏度

        val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()) else -1f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 检测是否为双电芯
        val isDualBattery = isDualBatteryDevice()

        // 计算充电功率 (W) - 考虑双电芯
        val chargingWatt = calculateChargingPower(batteryStatus, isCharging, isDualBattery)

        // 获取标称容量
        val ratedCapacity = getRatedCapacity()

        return BatteryInfo(
            level = level,
            scale = scale,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            status = status,
            plugged = plugged,
            voltage = voltage,
            temperature = temperature,
            chargingPower = chargingWatt,
            ratedCapacity = ratedCapacity,
            technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown",
            isDualBattery = isDualBattery
        )
    }

    /**
     * 计算充电功率（W）- 支持双电芯
     *
     * 改进算法：
     * 1. 优先使用实际电流电压计算
     * 2. 基于设备型号和充电状态智能估算
     * 3. 考虑快充分阶段充电策略
     */
    private fun calculateChargingPower(intent: Intent, isCharging: Boolean, isDualBattery: Boolean): Float {
        if (!isCharging) return 0f

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) / 1000f
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)

        // 方法1: 尝试获取实际电流数据
        var current = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                // 尝试多种电流字段
                current = Math.abs(intent.getIntExtra("current_avg", 0)) / 1000f
                if (current == 0f) current = Math.abs(intent.getIntExtra("current_now", 0)) / 1000f
                if (current == 0f) current = Math.abs(intent.getIntExtra("current_max", 0)) / 1000f

                // 双电芯调整：某些设备报告单电芯电流
                if (isDualBattery && current in 1f..5f) {
                    Log.d(TAG, "双电芯电流调整: ${current}A × 2 = ${current * 2}A")
                    current *= 2f
                }
            } catch (e: Exception) {
                Log.w(TAG, "无法读取电流: ${e.message}")
            }
        }

        // 如果获取到有效电流，直接计算功率
        if (current > 0.5f && voltage > 3f) {
            val power = current * voltage
            Log.d(TAG, "实测功率: ${current}A × ${voltage}V = ${power}W")
            return power
        }

        // 方法2: 基于设备型号和充电状态智能估算
        val estimatedPower = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> {
                estimateChargingPowerFromDevice(level, isDualBattery)
            }
            BatteryManager.BATTERY_PLUGGED_USB -> {
                // USB充电通常是慢充
                if (voltage >= 5f) 15f else 5f
            }
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> {
                if (isDualBattery) 50f else 15f
            }
            else -> 0f
        }

        Log.d(TAG, "估算功率: ${estimatedPower}W (双电芯=$isDualBattery, 电量=$level%)")
        return estimatedPower
    }

    /**
     * 基于设备型号估算充电功率
     * ��考：各大品牌手机的快充规格
     */
    private fun estimateChargingPowerFromDevice(batteryLevel: Int, isDualBattery: Boolean): Float {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()

        // 根据品牌和型号返回最大充电功率
        val maxPower = when {
            // 一加 OnePlus
            manufacturer.contains("oneplus") -> {
                when {
                    model.contains("ace") -> 150f // OnePlus Ace系列
                    model.contains("10t") -> 150f
                    model.contains("11") || model.contains("12") -> 100f
                    model.contains("9 pro") || model.contains("10 pro") -> 65f
                    else -> if (isDualBattery) 100f else 65f
                }
            }
            // OPPO
            manufacturer.contains("oppo") -> {
                when {
                    model.contains("find x") -> if (isDualBattery) 100f else 80f
                    model.contains("reno") -> 80f
                    else -> if (isDualBattery) 80f else 65f
                }
            }
            // 小米
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") -> {
                when {
                    model.contains("13 ultra") || model.contains("14") -> 120f
                    model.contains("13 pro") -> 120f
                    model.contains("k60") || model.contains("k70") -> 120f
                    model.contains("note") -> 67f
                    else -> if (isDualBattery) 120f else 67f
                }
            }
            // vivo
            manufacturer.contains("vivo") -> {
                when {
                    model.contains("x100") || model.contains("x90") -> 120f
                    model.contains("iqoo") -> 120f
                    else -> if (isDualBattery) 80f else 44f
                }
            }
            // realme
            manufacturer.contains("realme") -> {
                when {
                    model.contains("gt") -> 150f
                    else -> if (isDualBattery) 100f else 65f
                }
            }
            // 华硕/游戏手机
            manufacturer.contains("asus") || model.contains("rog") -> 65f
            manufacturer.contains("black shark") -> 120f
            manufacturer.contains("nubia") || model.contains("red magic") -> 120f

            // 默认值
            else -> if (isDualBattery) 65f else 25f
        }

        // 根据电量调整功率（快充分阶段策略）
        return when {
            batteryLevel < 50 -> maxPower // 0-50%: 满功率快充
            batteryLevel < 80 -> maxPower * 0.7f // 50-80%: 降低功率
            else -> maxPower * 0.4f // 80-100%: 涓流充电
        }
    }

    /**
     * 获取电池标称容量（mAh）
     * 尝试从多个来源读取真实容量
     */
    private fun getRatedCapacity(): Int {
        // 方法1: 尝试从系统属性读取（需要root或系统应用）
        try {
            // 尝试读取电池设计容量
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            if (batteryManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 某些设备支持从BatteryManager读取
                val capacityMicroAh = batteryManager.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (capacityMicroAh > 0) {
                    val capacityMah = (capacityMicroAh / 1000).toInt()
                    Log.d(TAG, "从系统读取容量: ${capacityMah}mAh")
                    return capacityMah
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从系统读取容量: ${e.message}")
        }

        // 方法2: 尝试从sysfs读取（某些设备支持）
        try {
            val files = listOf(
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/capacity",
                "/sys/class/power_supply/battery/energy_full"
            )

            for (file in files) {
                val content = java.io.File(file).readText().trim()
                val value = content.toIntOrNull()
                if (value != null && value > 100) {
                    // 可能是µAh，需要转换
                    val capacityMah = if (value > 10000) value / 1000 else value
                    Log.d(TAG, "从sysfs读取容量: ${capacityMah}mAh (文件: $file)")
                    return capacityMah
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "无法从sysfs读取容量: ${e.message}")
        }

        // 方法3: 基于设备型号数据库
        val capacityFromModel = getCapacityFromModel()
        if (capacityFromModel > 0) {
            Log.d(TAG, "从型号数据库读取容量: ${capacityFromModel}mAh")
            return capacityFromModel
        }

        // 返回0表示无法获取，需要用户手动输入
        Log.w(TAG, "无法自动获取容量，返回0")
        return 0
    }

    /**
     * 基于设备型号获取电池���量
     * 维护常见设备的电池容量数据库
     */
    private fun getCapacityFromModel(): Int {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val product = Build.PRODUCT.lowercase()

        return when {
            // 一加 OnePlus
            manufacturer.contains("oneplus") -> {
                when {
                    model.contains("ace 2") || model.contains("ace2") -> 5000
                    model.contains("ace 3") || model.contains("ace3") -> 5500
                    model.contains("10t") -> 4800
                    model.contains("11") -> 5000
                    model.contains("12") -> 5400
                    model.contains("10 pro") || model.contains("9 pro") -> 4500
                    model.contains("9") -> 4500
                    model.contains("8") -> 4300
                    product.contains("pje") -> 5000 // PJE110
                    else -> 4500
                }
            }
            // OPPO
            manufacturer.contains("oppo") -> {
                when {
                    model.contains("find x6") || model.contains("find x7") -> 5000
                    model.contains("find x5") -> 4800
                    model.contains("reno10") -> 4600
                    model.contains("reno") -> 4500
                    else -> 4500
                }
            }
            // 小米
            manufacturer.contains("xiaomi") -> {
                when {
                    model.contains("14 ultra") || model.contains("14ultra") -> 5300
                    model.contains("14 pro") || model.contains("14pro") -> 4880
                    model.contains("14") -> 4610
                    model.contains("13 ultra") || model.contains("13ultra") -> 5000
                    model.contains("13 pro") || model.contains("13pro") -> 4820
                    model.contains("13") -> 4500
                    model.contains("k70") -> 5000
                    model.contains("k60") -> 5500
                    else -> 4500
                }
            }
            // vivo
            manufacturer.contains("vivo") -> {
                when {
                    model.contains("x100") || model.contains("x90") -> 4870
                    model.contains("iqoo 12") -> 5000
                    model.contains("iqoo 11") -> 5000
                    else -> 4500
                }
            }
            // 华为
            manufacturer.contains("huawei") -> {
                when {
                    model.contains("mate 60") || model.contains("p60") -> 4820
                    model.contains("mate 50") -> 4750
                    else -> 4400
                }
            }
            // 三星
            manufacturer.contains("samsung") -> {
                when {
                    model.contains("s24 ultra") -> 5000
                    model.contains("s24+") -> 4900
                    model.contains("s24") -> 4000
                    model.contains("s23 ultra") -> 5000
                    model.contains("note") -> 4500
                    else -> 4000
                }
            }
            // 其他
            else -> 0 // 未知设备，返回0
        }
    }

    /**
     * 获取充电健康信息
     */
    fun getBatteryHealthInfo(): BatteryHealthInfo {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryHealthInfo()

        val health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

        return BatteryHealthInfo(
            health = health,
            healthString = when (health) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "良好"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "过热"
                BatteryManager.BATTERY_HEALTH_DEAD -> "损坏"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "过压"
                BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "未知故障"
                BatteryManager.BATTERY_HEALTH_COLD -> "过冷"
                else -> "未知"
            }
        )
    }

    /**
     * 估算本次充电已充入的电量（mAh）
     * @param startPercent 起始电量百分比
     * @param currentPercent 当前电量百分比
     * @param ratedCapacity 标称容量（mAh）
     */
    fun calculateChargedAmount(
        startPercent: Float,
        currentPercent: Float,
        ratedCapacity: Int
    ): Int {
        if (ratedCapacity <= 0) return 0

        val chargedPercent = max(0f, currentPercent - startPercent)
        val efficiency = getChargingEfficiency(startPercent, currentPercent)

        // 计算已充电量 = 充电百分比 × 标称容量 × 充电效率
        return ((chargedPercent / 100f) * ratedCapacity * efficiency).toInt()
    }

    /**
     * 根据充电区间获取充电效率系数
     */
    private fun getChargingEfficiency(startPercent: Float, endPercent: Float): Double {
        val avgPercent = (startPercent + endPercent) / 2f

        return when {
            avgPercent > 70f -> 0.85 // 高电量段效率低
            avgPercent < 20f -> 0.90 // 低电量段效率高
            else -> 0.88 // 中间段
        }
    }
}

/**
 * 电池信息数据类
 */
data class BatteryInfo(
    val level: Int = -1,
    val scale: Int = -1,
    val batteryPercent: Float = -1f,
    val isCharging: Boolean = false,
    val status: Int = BatteryManager.BATTERY_STATUS_UNKNOWN,
    val plugged: Int = -1,
    val voltage: Int = -1, // 毫伏
    val temperature: Int = -1, // 十分之一摄氏度
    val chargingPower: Float = 0f, // 瓦特
    val ratedCapacity: Int = 0, // mAh
    val technology: String = "Unknown",
    val isDualBattery: Boolean = false // 是否为双电芯设备
) {
    /** 获取电压（伏特） */
    val voltageV: Float
        get() = if (voltage > 0) voltage / 1000f else 0f

    /** 获取温度（摄氏度） */
    val temperatureC: Float
        get() = if (temperature > 0) temperature / 10f else 0f

    /** 获取充电状态描述 */
    val chargingStatusText: String
        get() = when {
            isCharging && batteryPercent >= 100f -> "已充满"
            isCharging -> "充电中${if (isDualBattery) "（双电芯）" else ""}"
            batteryPercent == 100f -> "已充满"
            else -> "未充电"
        }

    /** 获取充电类型描述 */
    val chargingTypeText: String
        get() = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "充电器"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
            else -> "未知"
        }

    /** 获取电芯类型描述 */
    val batteryTypeText: String
        get() = if (isDualBattery) "双电芯" else "单电芯"

    /** 获取充电功率描述 */
    val chargingPowerText: String
        get() = when {
            chargingPower >= 100f -> "超级快充${chargingPower.toInt()}W"
            chargingPower >= 60f -> "快充${chargingPower.toInt()}W"
            chargingPower >= 30f -> "快充${chargingPower.toInt()}W"
            chargingPower > 0f -> "${chargingPower.toInt()}W"
            else -> "--"
        }
}

/**
 * 电池健康信息数据类
 */
data class BatteryHealthInfo(
    val health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val healthString: String = "未知"
)
