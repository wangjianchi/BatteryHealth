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
        val plugged = batteryStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) // 毫伏
        val temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) // 十分之一摄氏度

        val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()) else -1f
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 检测是否为双电芯
        val isDualBattery = isDualBatteryDevice()

        // 计算充电功率 (W) - 考虑双电芯
        val chargingWatt = calculateChargingPower(batteryStatus, isCharging, isDualBattery)

        // 获取标称容量（优先使用设计容量）
        val ratedCapacity = getRatedCapacity()
        val designCapacity = getDesignCapacity()
        val systemHealth = getSystemHealthPercent()

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
            designCapacity = designCapacity,
            systemHealthPercent = systemHealth,
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
            batteryLevel < 95 -> maxPower * 0.3f // 80-95%: 涓流初期，估算值应更低
            else -> maxPower * 0.15f // 95-100%: 接近充满，实际功率极低
        }
    }

    /**
     * 获取电池设计容量（mAh）
     * 这是电池出厂时的标称容量，用于计算健康度
     * 优先级：sysfs > 系统API > 0
     */
    fun getDesignCapacity(): Int {
        // 方法1: 优先从 sysfs 读取（最可靠）
        try {
            val files = listOf(
                "/sys/class/power_supply/battery/charge_full_design",
                "/sys/class/power_supply/bms/charge_full_design",
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/battery/energy_full"
            )

            for (file in files) {
                try {
                    val content = java.io.File(file).readText().trim()
                    val value = content.toLongOrNull() ?: continue

                    val capacityMah = when {
                        value > 10000000 -> (value / 1000).toInt()
                        value > 10000 -> (value / 1000).toInt()
                        value > 100 -> value.toInt()  // 可能已经是 μAh
                        else -> continue
                    }

                    if (capacityMah in 2000..8000) {
                        Log.d(TAG, "从sysfs读取设计容量: ${capacityMah}mAh (来源: $file)")
                        return capacityMah
                    }
                } catch (e: Exception) {
                    // 跳过
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "sysfs读取失败: ${e.message}")
        }

        // 方法2: 从系统API读取
        try {
            val batteryManagerSvc = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            if (batteryManagerSvc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // 方式A: BATTERY_PROPERTY_CHARGE_COUNTER_DESIGN
                try {
                    val field = android.os.BatteryManager::class.java.getField("BATTERY_PROPERTY_CHARGE_COUNTER_DESIGN")
                    val propertyId = field.getInt(null)
                    val capacityMicroAh = batteryManagerSvc.getLongProperty(propertyId)
                    if (capacityMicroAh > 0) {
                        val capacityMah = (capacityMicroAh / 1000).toInt()
                        if (capacityMah in 2000..8000) {
                            Log.d(TAG, "从系统API读取设计容量: ${capacityMah}mAh")
                            return capacityMah
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CHARGE_COUNTER_DESIGN 不可用: ${e.message}")
                }

                // 方式B: ENERGY_COUNTER (nWh)
                try {
                    val capacityNWattH = batteryManagerSvc.getLongProperty(android.os.BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER)
                    if (capacityNWattH > 0) {
                        val capacityMah = (capacityNWattH / 1000000f / 3.8f).toInt()
                        if (capacityMah in 2000..8000) {
                            Log.d(TAG, "从ENERGY_COUNTER读取设计容量: ${capacityMah}mAh")
                            return capacityMah
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ENERGY_COUNTER 不可用: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "系统API读取失败: ${e.message}")
        }

        Log.w(TAG, "无法读取设计容量，返回0")
        return 0
    }

    /**
     * 获取电池标称容量（mAh）
     * 优先使用设计容量，其次型号数据库（出厂标称），最后 PowerProfile（可能是当前容量）
     */
    private fun getRatedCapacity(): Int {
        // 方法1: 直接复用设计容量读取（sysfs 或系统API）
        val designCapacity = getDesignCapacity()
        if (designCapacity > 0) {
            return designCapacity
        }

        // 方法2: 已知设备的型号数据库（出厂标称容量，最可靠）
        val capacityFromModel = getCapacityFromModel()
        if (capacityFromModel > 0) {
            Log.d(TAG, "从型号数据库读取标称容量: ${capacityFromModel}mAh")
            return capacityFromModel
        }

        // 方法3: PowerProfile（可能返回当前最大容量，不是出厂标称）
        val powerProfileCapacity = getCapacityFromPowerProfile()
        if (powerProfileCapacity > 0) {
            Log.d(TAG, "从 PowerProfile 读取容量: ${powerProfileCapacity}mAh")
            return powerProfileCapacity
        }

        Log.w(TAG, "无法获取标称容量，返回0")
        return 0
    }

    /**
     * 通过 PowerProfile 获取电池容量
     * 这是 Android 系统获取电池容量的标准方法
     */
    private fun getCapacityFromPowerProfile(): Int {
        try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)

            // 调用 getBatteryCapacity 方法
            val method = powerProfileClass.getMethod("getBatteryCapacity")
            val capacity = method.invoke(powerProfile) as Double

            if (capacity > 0) {
                Log.d(TAG, "PowerProfile 电池容量: ${capacity}mAh")
                // PowerProfile 返回的是 mAh
                return capacity.toInt()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PowerProfile 获取失败: ${e.message}")
        }
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
     * 计算系统健康度百分比
     * 基于当前实际容量与设计容量的比值
     */
    fun getSystemHealthPercent(): Int {
        val design = getDesignCapacity()
        if (design <= 0) return -1

        // 尝试读取当前实际容量（通过 charge_full 或其他途径）
        val currentCapacity = getCurrentCapacity()
        if (currentCapacity > 0) {
            return ((currentCapacity.toFloat() / design.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }

        return -1
    }

    /**
     * 计算所有5种健康度估算方案
     * @param records 充电历史记录（用于功率积分、百分比法、历史加权）
     */
    fun calculateAllHealthEstimations(
        records: List<HealthRecordInput> = emptyList()
    ): HealthEstimationResult {
        // 读取容量数据：优先 sysfs，回退到型号数据库
        val designCapacity = getDesignCapacity()           // sysfs 设计容量
        val currentCapacity = getCurrentCapacity()         // sysfs 当前容量
        val ratedCapacityFallback = getCapacityFromModel() // 型号数据库标称容量

        // 决定基准容量（用于计算健康度的分母）
        // 优先用 designCapacity（有 sysfs），回退到型号数据库标称容量
        val baseCapacity = if (designCapacity > 0) designCapacity else ratedCapacityFallback

        // 方案1：系统设计容量（直接读取内核，最准确）
        val systemDesign = if (designCapacity > 0 && currentCapacity > 0) {
            HealthEstimation(
                value = (currentCapacity * 100 / designCapacity).coerceIn(0, 100),
                capacity = currentCapacity,
                label = "系统设计容量",
                reliability = 1
            )
        } else if (currentCapacity > 0 && ratedCapacityFallback > 0) {
            // sysfs 设计容量读不到但当前容量能读到，用型号数据库作基准
            HealthEstimation(
                value = (currentCapacity * 100 / ratedCapacityFallback).coerceIn(0, 100),
                capacity = currentCapacity,
                label = "系统设计容量",
                reliability = 2
            )
        } else {
            HealthEstimation(value = -1, capacity = 0, label = "系统设计容量", reliability = 1)
        }

        // 方案2：系统当前容量（直接读取 charge_full）
        val systemCurrent = if (currentCapacity > 0) {
            val base = if (designCapacity > 0) designCapacity else ratedCapacityFallback
            if (base > 0) {
                HealthEstimation(
                    value = (currentCapacity * 100 / base).coerceIn(0, 100),
                    capacity = currentCapacity,
                    label = "系统当前容量",
                    reliability = 1
                )
            } else {
                HealthEstimation(value = -1, capacity = 0, label = "系统当前容量", reliability = 1)
            }
        } else {
            HealthEstimation(value = -1, capacity = 0, label = "系统当前容量", reliability = 1)
        }

        // 方案3：功率积分法（使用最近的充电记录）
        val powerBased = if (records.isNotEmpty()) {
            val latestRecord = records.last()
            if (latestRecord.chargedMinutes > 0 && latestRecord.chargedPercent > 0f) {
                val powerBasedCapacity = calculatePowerBasedCapacity(
                    latestRecord.chargedMinutes,
                    latestRecord.chargedPercent,
                    latestRecord.chargingWatt
                )
                if (powerBasedCapacity > 0 && baseCapacity > 0) {
                    HealthEstimation(
                        value = (powerBasedCapacity * 100 / baseCapacity).coerceIn(0, 100),
                        capacity = powerBasedCapacity,
                        label = "功率积分法",
                        reliability = 2
                    )
                } else if (powerBasedCapacity > 0 && latestRecord.ratedCapacity > 0) {
                    // 没有基准容量时，用记录中的标称容量
                    HealthEstimation(
                        value = (powerBasedCapacity * 100 / latestRecord.ratedCapacity).coerceIn(0, 100),
                        capacity = powerBasedCapacity,
                        label = "功率积分法",
                        reliability = 3
                    )
                } else {
                    HealthEstimation(value = -1, capacity = 0, label = "功率积分法", reliability = 2)
                }
            } else {
                HealthEstimation(value = -1, capacity = 0, label = "功率积分法", reliability = 2)
            }
        } else {
            HealthEstimation(value = -1, capacity = 0, label = "功率积分法", reliability = 2)
        }

        // 方案4：百分比法（使用最近的充电记录）
        val percentBased = if (records.isNotEmpty()) {
            val latestRecord = records.last()
            if (latestRecord.ratedCapacity > 0 && latestRecord.chargedPercent > 0f) {
                val efficiency = getChargingEfficiencyForPercent(latestRecord.chargedPercent)
                val percentBasedCapacity = (latestRecord.chargedPercent / 100f * latestRecord.ratedCapacity / efficiency).toInt()
                if (percentBasedCapacity > 0 && baseCapacity > 0) {
                    HealthEstimation(
                        value = (percentBasedCapacity * 100 / baseCapacity).coerceIn(0, 100),
                        capacity = percentBasedCapacity,
                        label = "百分比法",
                        reliability = 3
                    )
                } else {
                    HealthEstimation(value = -1, capacity = 0, label = "百分比法", reliability = 3)
                }
            } else {
                HealthEstimation(value = -1, capacity = 0, label = "百分比法", reliability = 3)
            }
        } else {
            HealthEstimation(value = -1, capacity = 0, label = "百分比法", reliability = 3)
        }

        // 方案5：历史加权平均
        val historyWeighted = if (records.size >= 2) {
            val weightedAvg = calculateWeightedAverage(records)
            if (weightedAvg > 0 && baseCapacity > 0) {
                HealthEstimation(
                    value = (weightedAvg * 100 / baseCapacity).coerceIn(0, 100),
                    capacity = weightedAvg,
                    label = "历史加权法",
                    reliability = 2
                )
            } else if (weightedAvg > 0) {
                HealthEstimation(value = -1, capacity = weightedAvg, label = "历史加权法", reliability = 2)
            } else {
                HealthEstimation(value = -1, capacity = 0, label = "历史加权法", reliability = 2)
            }
        } else if (records.size == 1) {
            // 单条记录直接用功率积分结果
            powerBased
        } else {
            HealthEstimation(value = -1, capacity = 0, label = "历史加权法", reliability = 2)
        }

        return HealthEstimationResult(
            systemDesign = systemDesign,
            systemCurrent = systemCurrent,
            powerIntegral = powerBased,
            percentBased = percentBased,
            historyWeighted = historyWeighted
        )
    }

    /**
     * 功率积分法计算电池容量
     */
    private fun calculatePowerBasedCapacity(
        chargedMinutes: Int,
        chargedPercent: Float,
        chargingWatt: Float?
    ): Int {
        if (chargedPercent <= 0f) return 0

        val chargedCapacity = if (chargingWatt != null && chargingWatt > 0f) {
            val durationHours = chargedMinutes / 60f
            val energyWh = chargingWatt * durationHours
            val avgVoltage = 4.0f
            val efficiency = getChargingEfficiencyForPercent(chargedPercent)
            (energyWh / avgVoltage * 1000 * efficiency).toInt()
        } else {
            // 无功率数据时用百分比法
            return 0
        }

        if (chargedCapacity <= 0) return 0
        return (chargedCapacity / (chargedPercent / 100f)).toInt().coerceAtLeast(0)
    }

    /**
     * 根据充电百分比获取效率系数
     */
    private fun getChargingEfficiencyForPercent(chargedPercent: Float): Double {
        return when {
            chargedPercent > 80f -> 0.80
            chargedPercent > 60f -> 0.85
            chargedPercent > 40f -> 0.88
            chargedPercent > 20f -> 0.90
            else -> 0.92
        }
    }

    /**
     * 历史加权平均计算容量
     */
    private fun calculateWeightedAverage(records: List<HealthRecordInput>): Int {
        var totalWeight = 0f
        var weightedSum = 0f
        records.forEachIndexed { i, r ->
            val w = ((i + 1) * (i + 1)).toFloat()
            val cap = calculatePowerBasedCapacity(r.chargedMinutes, r.chargedPercent, r.chargingWatt)
            if (cap > 0) {
                weightedSum += cap * w
                totalWeight += w
            }
        }
        return if (totalWeight > 0f) (weightedSum / totalWeight).toInt() else 0
    }

    /**
     * 获取电池当前实际容量（mAh）
     * 尝试从 sysfs 读取 charge_full（电池当前最大容量，随老化变化）
     */
    private fun getCurrentCapacity(): Int {
        try {
            val files = listOf(
                "/sys/class/power_supply/battery/charge_full",
                "/sys/class/power_supply/bms/charge_full",
                "/sys/class/power_supply/battery/capacity"
            )

            for (file in files) {
                try {
                    val content = java.io.File(file).readText().trim()
                    val value = content.toLongOrNull() ?: continue

                    val capacityMah = when {
                        value > 10000000 -> (value / 1000).toInt()
                        value > 10000 -> (value / 1000).toInt()
                        value > 100 -> value.toInt()
                        else -> continue
                    }

                    if (capacityMah in 1000..8000) {
                        Log.d(TAG, "从sysfs读取当前容量: ${capacityMah}mAh (来源: $file)")
                        return capacityMah
                    }
                } catch (e: Exception) {
                    // 跳过
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取当前容量失败: ${e.message}")
        }

        // 备选：从系统API读取当前容量
        try {
            val batteryManagerSvc = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            if (batteryManagerSvc != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // BATTERY_PROPERTY_CHARGE_COUNTER 是当前容量
                try {
                    val field = android.os.BatteryManager::class.java.getField("BATTERY_PROPERTY_CHARGE_COUNTER")
                    val propertyId = field.getInt(null)
                    val capacityMicroAh = batteryManagerSvc.getLongProperty(propertyId)
                    if (capacityMicroAh > 0) {
                        val capacityMah = (capacityMicroAh / 1000).toInt()
                        if (capacityMah in 1000..8000) {
                            Log.d(TAG, "从系统API读取当前容量: ${capacityMah}mAh")
                            return capacityMah
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "CHARGE_COUNTER 不可用: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "系统API读取当前容量失败: ${e.message}")
        }

        return 0
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
    val plugged: Int = 0,
    val voltage: Int = -1, // 毫伏
    val temperature: Int = -1, // 十分之一摄氏度
    val chargingPower: Float = 0f, // 瓦特
    val ratedCapacity: Int = 0, // mAh（标称容量，等于设计容量）
    val designCapacity: Int = 0, // mAh（设计容量）
    val systemHealthPercent: Int = -1, // 系统健康度百分比（-1表示无法读取）
    val technology: String = "Unknown",
    val isDualBattery: Boolean = false // 是否为双电芯设备
) {
    /** 获取电压（伏特） */
    val voltageV: Float
        get() = if (voltage > 0) voltage / 1000f else 0f

    /** 获取温度（摄氏度） */
    val temperatureC: Float
        get() = if (temperature > 0) temperature / 10f else 0f

    /** 充电器是否连接（plugged != 0 表示连接充电器） */
    val isChargerConnected: Boolean
        get() = plugged != 0

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
 * 单种健康度估算结果
 */
data class HealthEstimation(
    val value: Int,      // 健康度百分比，-1表示无法计算
    val capacity: Int,   // 估算的容量mAh，0表示无法计算
    val label: String,   // 显示名称
    val reliability: Int // 可靠性 1-3，1最高
)

/**
 * 电池健康度估算结果（包含5种方案）
 */
data class HealthEstimationResult(
    val systemDesign: HealthEstimation,   // 方案1：系统设计容量
    val systemCurrent: HealthEstimation,  // 方案2：系统当前容量
    val powerIntegral: HealthEstimation,  // 方案3：功率积分法
    val percentBased: HealthEstimation,   // 方案4：百分比法
    val historyWeighted: HealthEstimation // 方案5：历史加权法
) {
    /** 获取所有有效估算值的平均值 */
    val average: Int?
        get() {
            val valid = listOf(systemDesign, systemCurrent, powerIntegral, percentBased, historyWeighted)
                .filter { it.value > 0 }
            if (valid.isEmpty()) return null
            return valid.sumOf { it.value } / valid.size
        }

    /** 获取所有有效估算值中的中位数 */
    val median: Int?
        get() {
            val valid = listOf(systemDesign, systemCurrent, powerIntegral, percentBased, historyWeighted)
                .filter { it.value > 0 }
                .map { it.value }
                .sorted()
            if (valid.isEmpty()) return null
            return if (valid.size % 2 == 0) {
                (valid[valid.size / 2 - 1] + valid[valid.size / 2]) / 2
            } else {
                valid[valid.size / 2]
            }
        }
}

/**
 * 用于健康度估算的充电记录输入
 */
data class HealthRecordInput(
    val ratedCapacity: Int,      // 标称容量 mAh
    val chargedPercent: Float,   // 充电百分比
    val chargedMinutes: Int,     // 充电时长（分钟）
    val chargingWatt: Float?     // 充电功率 W
)

/**
 * 电池健康信息数据类
 */
data class BatteryHealthInfo(
    val health: Int = BatteryManager.BATTERY_HEALTH_UNKNOWN,
    val healthString: String = "未知"
)
