package com.batteryhealth.app.data.model

/**
 * 充电数据点
 * 用于记录充电过程中的实时数据
 */
data class ChargingDataPoint(
    val timestamp: Long = System.currentTimeMillis(),
    val batteryPercent: Float,      // 当前电量 %
    val chargingPower: Float,       // 当前充电功率 W
    val voltage: Float,             // 当前电压 V
    val temperature: Float         // 当前温度 °C
) {
    /**
     * 获取格式化的时间文本
     */
    fun getTimeText(): String {
        val diffMs = timestamp - System.currentTimeMillis()
        val diffSeconds = kotlin.math.abs(diffMs / 1000)
        val diffMinutes = diffSeconds / 60
        val hours = (diffMinutes / 60).toInt()
        val mins = (diffMinutes % 60).toInt()
        val secs = (diffSeconds % 60).toInt()
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
