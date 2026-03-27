package com.batteryhealth.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "charging_records")
data class ChargingRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val ratedCapacity: Int,        // 标称容量 mAh
    val batteryBefore: Int,        // 充电前电量 %
    val batteryAfter: Int,         // 充电后电量 %
    val chargingMinutes: Int,      // 充电时长（分钟）
    val chargingWatt: Float? = null, // 充电功率（W，可选）
    val note: String = "",         // 备注
    val estimatedCapacity: Int,    // 估算容量 mAh
    val healthPercent: Int,       // 健康度 %
    val dataPointsJson: String = "[]", // 充电数据点JSON
    val hasDetailedData: Boolean = false // 是否有详细数据
)

data class HealthGrade(
    val label: String,
    val color: Long,
    val emoji: String,
    val advice: String
)

fun getHealthGrade(healthPercent: Int): HealthGrade {
    return when {
        healthPercent >= 90 -> HealthGrade(
            label = "优秀",
            color = 0xFF00C896,
            emoji = "🌟",
            advice = "电池状态极佳，保持当前使用习惯即可。建议保持电量在 20%–80% 之间充电。"
        )
        healthPercent >= 80 -> HealthGrade(
            label = "良好",
            color = 0xFF4CAF50,
            emoji = "✅",
            advice = "电池状态良好，续航接近新机。减少超快充频率，适当使用慢充保护电池。"
        )
        healthPercent >= 70 -> HealthGrade(
            label = "一般",
            color = 0xFFFFB300,
            emoji = "⚠️",
            advice = "电池开始老化，续航可能缩短 20–30%。建议开启省电模式，减少后台应用活动。"
        )
        healthPercent >= 60 -> HealthGrade(
            label = "较差",
            color = 0xFFFF7043,
            emoji = "🔶",
            advice = "电池老化明显，建议携带充电宝备用。可联系官方售后评估更换电池。"
        )
        else -> HealthGrade(
            label = "严重老化",
            color = 0xFFE53935,
            emoji = "🔴",
            advice = "电池严重老化，强烈建议更换电池！老化电池存在异常发热或膨胀风险。"
        )
    }
}
