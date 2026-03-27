package com.batteryhealth.app.data.model

/**
 * 当前充电会话数据
 * 用于跟踪一次完整的充电过程
 */
data class ChargingSession(
    val sessionId: Long = System.currentTimeMillis(),
    val startTime: Long = System.currentTimeMillis(),
    val startPercent: Float,
    val ratedCapacity: Int,
    val isRunning: Boolean = true,
    val lastUpdateTime: Long = System.currentTimeMillis(),
    val currentPercent: Float = startPercent,
    val endPercent: Float? = null,
    val endTime: Long? = null,
    val averagePower: Float = 0f, // 平均充电功率（W）
    val maxPower: Float = 0f, // 最大充电功率（W）
    val minPower: Float = Float.MAX_VALUE, // 最小充电功率（W）
    val powerSamples: List<Float> = emptyList(), // 功率采样记录
    val dataPoints: List<ChargingDataPoint> = emptyList() // 充电数据点列表
) {
    /**
     * 获取已充电百分比
     */
    val chargedPercent: Float
        get() = (currentPercent - startPercent).coerceAtLeast(0f)

    /**
     * 获取已充电时长（分钟）
     */
    val chargingDurationMinutes: Long
        get() = if (isRunning) {
            ((System.currentTimeMillis() - startTime) / 60000L)
        } else {
            ((endTime ?: startTime) - startTime) / 60000L
        }

    /**
     * 获取已充电时长（格式化字符串）
     */
    val chargingDurationText: String
        get() {
            val minutes = chargingDurationMinutes
            val hours = minutes / 60
            val mins = minutes % 60
            return when {
                hours > 0 -> "${hours}小时${mins}分钟"
                mins > 0 -> "${mins}分钟"
                else -> "不到1分钟"
            }
        }

    /**
     * 获取已充电量（mAh）
     *
     * 优先使用功率积分法，更准确
     * 回退到百分比法（需要标称容量）
     */
    val chargedAmount: Int
        get() {
            // 方法1: 基于功率积分（更准确，推荐）
            if (averagePower > 0 && powerSamples.isNotEmpty()) {
                return calculateChargedAmountByPower()
            }

            // 方法2: 基于百分比和容量（需要手动输入容量）
            if (ratedCapacity > 0) {
                return calculateChargedAmountByPercent()
            }

            return 0
        }

    /**
     * 方法1: 基于功率积分计算充电量
     * 原理: 电量 = 功率 × 时间 / 电压 × 效率
     */
    private fun calculateChargedAmountByPower(): Int {
        if (powerSamples.isEmpty() || averagePower <= 0) return 0

        // 充电时长（小时）
        val durationHours = chargingDurationMinutes / 60f

        // 能量（Wh）= 功率（W）× 时间（h）
        val energyWh = averagePower * durationHours

        // 平均电压（V），通常在3.7-4.4V之间
        val avgVoltage = 4.0f // 使用典型值

        // 充电效率（考虑充电损耗）
        val efficiency = getChargingEfficiency(startPercent, currentPercent)

        // 电量（mAh）= 能量（Wh）/ 电压（V）× 1000 × 效率
        val capacityMah = (energyWh / avgVoltage * 1000 * efficiency).toInt()

        return capacityMah.coerceAtLeast(0)
    }

    /**
     * 方法2: 基于百分比和标称容量计算
     * 原理: 电量 = 百分比 × 容量 × 效率
     */
    private fun calculateChargedAmountByPercent(): Int {
        if (ratedCapacity <= 0) return 0

        val efficiency = getChargingEfficiency(startPercent, currentPercent)
        return ((chargedPercent / 100f) * ratedCapacity * efficiency).toInt()
    }

    /**
     * 估算剩余充电时间（分钟）
     * 基于当前充电速度估算
     */
    val estimatedRemainingMinutes: Long?
        get() {
            if (!isRunning || averagePower <= 0f || ratedCapacity <= 0) return null

            val remainingPercent = 100f - currentPercent
            if (remainingPercent <= 0) return 0

            // 根据历史平均速度估算
            val percentPerMinute = chargedPercent / chargingDurationMinutes.coerceAtLeast(1)
            if (percentPerMinute <= 0) return null

            return (remainingPercent / percentPerMinute).toLong()
        }

    /**
     * 估算剩余充电时间（格式化字符串）
     */
    val estimatedRemainingText: String?
        get() {
            val minutes = estimatedRemainingMinutes ?: return null
            val hours = minutes / 60
            val mins = minutes % 60
            return when {
                hours > 0 -> "约${hours}小时${mins}分钟"
                mins > 0 -> "约${mins}分钟"
                else -> "即将充满"
            }
        }

    /**
     * 获取充电效率系数
     *
     * 考虑因素：
     * 1. 电量区间（高电量效率低，低电量效率高）
     * 2. 充电功率（快充效率略低于慢充）
     * 3. 温度影响（简化处理）
     */
    private fun getChargingEfficiency(startPercent: Float, endPercent: Float): Double {
        val avgPercent = (startPercent + endPercent) / 2f

        // 基础效率（基于电量区间）
        val baseEfficiency = when {
            avgPercent > 80f -> 0.80  // 80-100%: 涓流充电，效率最低
            avgPercent > 60f -> 0.85  // 60-80%: 降速充电
            avgPercent > 40f -> 0.88  // 40-60%: 中速充电
            avgPercent > 20f -> 0.90  // 20-40%: 快速充电
            else -> 0.92              // 0-20%: 满功率充电，效率最高
        }

        // 充电功率影响（快充损耗略大）
        val powerFactor = when {
            averagePower > 100f -> 0.95  // 超级快充（100W+）
            averagePower > 65f -> 0.97   // 快充（65-100W）
            averagePower > 30f -> 0.98   // 普通快充（30-65W）
            else -> 1.0                  // 慢充（<30W）
        }

        val finalEfficiency = baseEfficiency * powerFactor

        return finalEfficiency.coerceIn(0.75, 0.95)
    }

    /**
     * 创建已结束的会话
     */
    fun endSession(endPercent: Float): ChargingSession {
        return copy(
            isRunning = false,
            endPercent = endPercent,
            currentPercent = endPercent,
            endTime = System.currentTimeMillis(),
            lastUpdateTime = System.currentTimeMillis()
        )
    }

    /**
     * 更新会话状态
     */
    fun updateSession(
        currentPercent: Float,
        currentPower: Float,
        voltage: Float = 0f,
        temperature: Float = 0f
    ): ChargingSession {
        val newPowerSamples = powerSamples + currentPower
        val newAvgPower = if (newPowerSamples.isNotEmpty()) {
            newPowerSamples.average().toFloat()
        } else averagePower

        // 创建新的数据点
        val newDataPoint = ChargingDataPoint(
            batteryPercent = currentPercent,
            chargingPower = currentPower,
            voltage = voltage,
            temperature = temperature
        )

        return copy(
            currentPercent = currentPercent,
            lastUpdateTime = System.currentTimeMillis(),
            averagePower = newAvgPower,
            maxPower = maxOf(maxPower, currentPower),
            minPower = if (currentPower > 0) minOf(minPower, currentPower) else minPower,
            powerSamples = newPowerSamples.takeLast(100), // 只保留最近100个采样
            dataPoints = dataPoints + newDataPoint // 添加新数据点
        )
    }

    /**
     * 转换为充电记录（用于保存到数据库）
     */
    fun toChargingRecord(): ChargingRecord? {
        if (isRunning) return null // 正在充电的会话不能转换

        // 将数据点转换为JSON
        val dataPointsJson = ChargingDataPointsJson.toJson(dataPoints)

        return ChargingRecord(
            timestamp = startTime,
            ratedCapacity = ratedCapacity,
            batteryBefore = startPercent.toInt(),
            batteryAfter = (endPercent ?: currentPercent).toInt(),
            chargingMinutes = chargingDurationMinutes.toInt(),
            chargingWatt = if (averagePower > 0) averagePower else null,
            note = "自动记录充电会话",
            estimatedCapacity = chargedAmount + ((startPercent / 100f) * ratedCapacity).toInt(),
            healthPercent = 0, // 这个需要在Repository中计算
            dataPointsJson = dataPointsJson,
            hasDetailedData = dataPoints.isNotEmpty()
        )
    }
}

/**
 * 充电会话状态
 */
sealed class ChargingSessionState {
    /** 未开始充电 */
    data object NotCharging : ChargingSessionState()

    /** 正在充电 */
    data class Charging(val session: ChargingSession) : ChargingSessionState()

    /** 充电完成 */
    data class Completed(val session: ChargingSession) : ChargingSessionState()
}
