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
    val dataPoints: List<ChargingDataPoint> = emptyList(), // 充电数据点列表
    // 10秒增量累加相关
    val accumulatedChargedMah: Float = 0f, // 累加充电量（mAh）
    val lastUpdateTimeForCalc: Long = startTime // 上次计算时刻（用于计算时间间隔）
) {
    /**
     * 获取已充电百分比
     */
    val chargedPercent: Float
        get() = (currentPercent - startPercent).coerceAtLeast(0f)

    /**
     * 根据充电增量反推电池实际容量（mAh）
     * 原理: 实际容量 = 充电增量(mAh) / 充电百分比
     * 只有在累加充电量 > 0 且充电百分比 > 0 时有效
     */
    val estimatedBatteryCapacity: Int
        get() {
            // 使用经过双轨验证的 chargedAmount
            val validatedChargedMah = chargedAmount.toFloat()
            if (validatedChargedMah <= 0f || chargedPercent <= 0f) return 0
            // 反推完整电池容量
            val rawCapacity = validatedChargedMah / (chargedPercent / 100f)
            // 限制在合理范围 [额定容量/2, 额定容量*1.5]
            val minCapacity = ratedCapacity / 2f
            val maxCapacity = ratedCapacity * 1.5f
            return if (ratedCapacity > 0) {
                rawCapacity.coerceIn(minCapacity, maxCapacity).toInt()
            } else {
                rawCapacity.toInt()
            }
        }

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
     * 双轨验证：功率积分法 vs 百分比法
     * 当功率积分结果明显偏高时（超出百分比法 30%），说明功率估计过高，自动回退
     */
    val chargedAmount: Int
        get() {
            if (accumulatedChargedMah <= 0f) return 0

            // 百分比法基准（已知标称容量时）
            if (ratedCapacity > 0) {
                val percentBased = calculateChargedAmountByPercent()
                if (percentBased > 0) {
                    // 如果功率积分结果比百分比法高 30% 以上，说明功率估计过高
                    val ratio = accumulatedChargedMah / percentBased
                    if (ratio > 1.3f) {
                        // 回退到百分比法
                        return percentBased
                    }
                }
            }

            // 正常情况：返回功率积分结果
            return accumulatedChargedMah.toInt()
        }

    /**
     * 计算单次采样的充电增量（mAh）
     * 用于10秒增量累加：电量 = 功率 × 时间 / 电压 × 效率
     * @param avgPower 本次时间段的平均功率（使用滚动平均，更准确）
     */
    private fun calculateChargedAmountIncrement(avgPower: Float, durationSeconds: Long): Float {
        if (avgPower <= 0f || durationSeconds <= 0) return 0f

        // 能量（Wh）= 功率（W）× 时间（h）
        val durationHours = durationSeconds / 3600f
        val energyWh = avgPower * durationHours

        // 平均电压（V）
        val avgVoltage = 4.0f

        // 充电效率（基于本次时间段的平均电量）
        val avgPercentInPeriod = (startPercent + currentPercent) / 2f
        val efficiency = getChargingEfficiencyForPeriod(avgPercentInPeriod)

        // 电量（mAh）= 能量（Wh）/ 电压（V）× 1000 × 效率
        return (energyWh / avgVoltage * 1000 * efficiency).toFloat().coerceAtLeast(0f)
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
     * 获取充电效率系数（用于滚动平均场景）
     * 仅基于电量区间，不考虑历史平均功率（因为功率已在滚动平均中平滑）
     */
    private fun getChargingEfficiencyForPeriod(avgPercent: Float): Double {
        return when {
            avgPercent > 80f -> 0.80  // 80-100%: 涓流充电，效率最低
            avgPercent > 60f -> 0.85  // 60-80%: 降速充电
            avgPercent > 40f -> 0.88  // 40-60%: 中速充电
            avgPercent > 20f -> 0.90  // 20-40%: 快速充电
            else -> 0.92              // 0-20%: 满功率充电，效率最高
        }
    }

    /**
     * 获取充电效率系数（用于百分比推算场景）
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
        val now = System.currentTimeMillis()
        // 结束前补上最后一小段的增量，使用最近采样滚动平均功率
        val finalDurationSeconds = ((now - lastUpdateTimeForCalc) / 1000L).coerceAtLeast(0L)
        val windowSize = 10
        val recentPowers = powerSamples.takeLast(windowSize)
        val rollingAvgPower = recentPowers.filter { it > 0f }.average().toFloat()
        val finalIncrement = calculateChargedAmountIncrement(rollingAvgPower, finalDurationSeconds)
        val finalAccumulated = accumulatedChargedMah + finalIncrement

        return copy(
            isRunning = false,
            endPercent = endPercent,
            currentPercent = endPercent,
            endTime = now,
            lastUpdateTime = now,
            accumulatedChargedMah = finalAccumulated
        )
    }

    /**
     * 更新会话状态
     * 每次更新时计算本次时间段的滚动平均功率，用于更准确的电量积分
     */
    fun updateSession(
        currentPercent: Float,
        currentPower: Float,
        voltage: Float = 0f,
        temperature: Float = 0f
    ): ChargingSession {
        val now = System.currentTimeMillis()
        val durationSeconds = ((now - lastUpdateTimeForCalc) / 1000L).coerceAtLeast(0L)

        // 滚动平均功率：最近 N 次采样的平均值（N=10）
        val windowSize = 10
        val recentPowers = (powerSamples + currentPower).takeLast(windowSize)
        val rollingAvgPower = recentPowers.filter { it > 0f }.average().toFloat()

        // 计算本次采样增加的充电量并累加（使用滚动平均功率，更准确）
        val incrementMah = calculateChargedAmountIncrement(rollingAvgPower, durationSeconds)
        val newAccumulated = accumulatedChargedMah + incrementMah

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
            lastUpdateTime = now,
            averagePower = newAvgPower,
            maxPower = maxOf(maxPower, currentPower),
            minPower = if (currentPower > 0) minOf(minPower, currentPower) else minPower,
            powerSamples = newPowerSamples.takeLast(100), // 只保留最近100个采样
            dataPoints = dataPoints + newDataPoint, // 添加新数据点
            // 增量累加
            accumulatedChargedMah = newAccumulated,
            lastUpdateTimeForCalc = now
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
            estimatedCapacity = estimatedBatteryCapacity,
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
