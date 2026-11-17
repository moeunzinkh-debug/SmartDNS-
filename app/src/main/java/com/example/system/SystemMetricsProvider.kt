package com.example.system

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class BatteryMetrics(
    val levelPercentage: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 28.0f,
    val health: String = "Good",
    val powerSaveMode: Boolean = false
)

data class RamMetrics(
    val totalMemoryMb: Long = 0,
    val availableMemoryMb: Long = 0,
    val usedMemoryMb: Long = 0,
    val usedPercentage: Int = 0,
    val isLowMemory: Boolean = false
)

class SystemMetricsProvider(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Reads current RAM metrics from ActivityManager.MemoryInfo
     */
    fun getRamMetrics(): RamMetrics {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMb = memoryInfo.totalMem / (1024 * 1024)
        val availMb = memoryInfo.availMem / (1024 * 1024)
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val usedPct = if (totalMb > 0) ((usedMb.toDouble() / totalMb.toDouble()) * 100).toInt() else 0

        return RamMetrics(
            totalMemoryMb = totalMb,
            availableMemoryMb = availMb,
            usedMemoryMb = usedMb,
            usedPercentage = usedPct.coerceIn(0, 100),
            isLowMemory = memoryInfo.lowMemory
        )
    }

    /**
     * Flow of live battery metrics
     */
    fun getBatteryMetricsFlow(): Flow<BatteryMetrics> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
                val healthCode = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)

                val percentage = if (level >= 0 && scale > 0) {
                    ((level.toFloat() / scale.toFloat()) * 100).toInt()
                } else 100

                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val health = when (healthCode) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    else -> "Normal"
                }

                trySend(
                    BatteryMetrics(
                        levelPercentage = percentage,
                        isCharging = isCharging,
                        temperatureCelsius = temp,
                        health = health,
                        powerSaveMode = false
                    )
                )
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val initialIntent = context.registerReceiver(receiver, filter)
        if (initialIntent != null) {
            receiver.onReceive(context, initialIntent)
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
        }
    }
}
