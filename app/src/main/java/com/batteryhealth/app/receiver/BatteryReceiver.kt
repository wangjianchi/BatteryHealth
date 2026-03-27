package com.batteryhealth.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log

/**
 * 电池状态监听器
 * 监听充电状态变化并通知应用更新
 */
class BatteryReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BatteryReceiver"
        const val ACTION_BATTERY_UPDATE = "com.batteryhealth.app.BATTERY_UPDATE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                handleBatteryChanged(context, intent)
            }
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d(TAG, "充电器已连接")
                notifyBatteryUpdate(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d(TAG, "充电器已断开")
                notifyBatteryUpdate(context)
            }
        }
    }

    private fun handleBatteryChanged(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (scale > 0) (level * 100 / scale.toFloat()) else -1f
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        // 保存当前电量状态到 SharedPreferences
        val prefs = context.getSharedPreferences("battery_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putFloat("last_battery_level", batteryPct)
            .putBoolean("is_charging", isCharging)
            .putLong("last_update", System.currentTimeMillis())
            .apply()

        // 发送广播通知UI更新
        notifyBatteryUpdate(context)

        Log.d(TAG, "电池状态: ${batteryPct.toInt()}%, 充电中: $isCharging")
    }

    private fun notifyBatteryUpdate(context: Context) {
        val updateIntent = Intent(ACTION_BATTERY_UPDATE)
        updateIntent.setPackage(context.packageName)
        context.sendBroadcast(updateIntent)
    }
}
