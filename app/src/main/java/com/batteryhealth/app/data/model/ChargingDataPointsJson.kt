package com.batteryhealth.app.data.model

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 充电数据点JSON序列化工具
 */
object ChargingDataPointsJson {
    private val gson = Gson()

    /**
     * 将数据点列表转换为JSON字符串
     */
    fun toJson(dataPoints: List<ChargingDataPoint>): String {
        return gson.toJson(dataPoints)
    }

    /**
     * 从JSON字符串解析数据点列表
     */
    fun fromJson(json: String): List<ChargingDataPoint> {
        return try {
            val type = object : TypeToken<List<ChargingDataPoint>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
