package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.SmartDndApplication
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed, restoring enabled Smart DND schedules")
            val app = context.applicationContext as? SmartDndApplication ?: return
            app.applicationScope.launch {
                val schedules = app.database.smartDndDao().getEnabledSchedules()
                for (schedule in schedules) {
                    app.scheduleManager.registerSchedule(schedule)
                }
            }
        }
    }
}
