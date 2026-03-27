package com.batteryhealth.app.data.repository

import com.batteryhealth.app.data.model.ChargingRecord
import kotlinx.coroutines.flow.Flow

class BatteryRepository(private val dao: ChargingRecordDao) {

    val allRecordsDesc: Flow<List<ChargingRecord>> = dao.getAllRecords()
    val allRecordsAsc: Flow<List<ChargingRecord>> = dao.getAllRecordsAsc()

    suspend fun insertRecord(record: ChargingRecord) = dao.insertRecord(record)
    suspend fun deleteRecord(record: ChargingRecord) = dao.deleteRecord(record)
    suspend fun deleteAllRecords() = dao.deleteAllRecords()

    /**
     * 计算估算容量（mAh）
     *
     * 新算法（更准确）：
     * 实际容量 = 充入电量 ÷ 充电百分比
     *
     * 其中充入电量优先使用功率积分法计算
     */
    fun calculateEstimatedCapacity(
        ratedCapacity: Int,
        batteryBefore: Int,
        batteryAfter: Int,
        chargingMinutes: Int,
        chargingWatt: Float?
    ): Int {
        // 充电百分比
        val chargedPercent = (batteryAfter - batteryBefore).toFloat()
        if (chargedPercent <= 0) return 0

        // 计算充入电量（mAh）
        val chargedCapacity = if (chargingWatt != null && chargingWatt > 0f) {
            // 优先使用功率积分法（更准确）
            calculateChargedCapacityByPower(chargingWatt, chargingMinutes, batteryBefore, batteryAfter)
        } else {
            // 备用：百分比法
            val efficiency = getChargingEfficiency(batteryBefore.toFloat(), batteryAfter.toFloat())
            (chargedPercent / 100f * ratedCapacity * efficiency).toInt()
        }

        if (chargedCapacity <= 0) return 0

        // 实际容量 = 充入电量 ÷ 充电百分比
        val actualCapacity = (chargedCapacity / (chargedPercent / 100f)).toInt()

        // 限制在合理范围内（标称容量的50%~150%）
        return actualCapacity.coerceIn(ratedCapacity / 2, ratedCapacity * 3 / 2)
    }

    /**
     * 基于功率积分计算充入电量
     * 原理：电量 = 功率 × 时间 / 电压 × 效率
     */
    private fun calculateChargedCapacityByPower(
        chargingWatt: Float,
        chargingMinutes: Int,
        batteryBefore: Int,
        batteryAfter: Int
    ): Int {
        // 充电时长（小时）
        val durationHours = chargingMinutes / 60f

        // 能量（Wh）= 功率（W）× 时间（h）
        val energyWh = chargingWatt * durationHours

        // 平均电压（V）- 锂电池典型值
        val avgVoltage = 4.0f

        // 充电效率
        val efficiency = getChargingEfficiency(batteryBefore.toFloat(), batteryAfter.toFloat())

        // 电量（mAh）= 能量（Wh）/ 电压（V）× 1000 × 效率
        val capacityMah = (energyWh / avgVoltage * 1000 * efficiency).toInt()

        return capacityMah.coerceAtLeast(0)
    }

    /**
     * 获取充电效率系数（改进版）
     * 基于电量区间和充电特性
     */
    private fun getChargingEfficiency(startPercent: Float, endPercent: Float): Double {
        val avgPercent = (startPercent + endPercent) / 2f

        return when {
            avgPercent > 80f -> 0.80  // 80-100%: 涓流充电，效率最低
            avgPercent > 60f -> 0.85  // 60-80%: 降速充电
            avgPercent > 40f -> 0.88  // 40-60%: 中速充电
            avgPercent > 20f -> 0.90  // 20-40%: 快速充电
            else -> 0.92              // 0-20%: 满功率充电，效率最高
        }
    }

    /**
     * 计算健康度百分比
     */
    fun calculateHealthPercent(estimatedCapacity: Int, ratedCapacity: Int): Int {
        return ((estimatedCapacity.toFloat() / ratedCapacity.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
    }

    /**
     * 加权平均估算容量（越新的记录权重越高）
     */
    fun calculateWeightedAverageCapacity(records: List<ChargingRecord>): Int? {
        if (records.isEmpty()) return null
        var totalWeight = 0f
        var weightedSum = 0f
        records.forEachIndexed { i, r ->
            val w = ((i + 1) * (i + 1)).toFloat()
            weightedSum += r.estimatedCapacity * w
            totalWeight += w
        }
        return (weightedSum / totalWeight).toInt()
    }
}
