package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.SmartDndApplication
import com.example.model.KillSessionLog
import kotlinx.coroutines.launch

class ScheduleAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SCHEDULE_TRIGGER = "com.example.ACTION_SCHEDULE_TRIGGER"
        const val EXTRA_IS_START = "extra_is_start"
        const val EXTRA_AUTO_KILL = "extra_auto_kill"
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val isStart = intent.getBooleanExtra(EXTRA_IS_START, true)
        val autoKill = intent.getBooleanExtra(EXTRA_AUTO_KILL, true)
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)

        Log.i("ScheduleAlarmReceiver", "Alarm fired! isStart=$isStart, autoKill=$autoKill, scheduleId=$scheduleId")

        val app = context.applicationContext as? SmartDndApplication ?: return
        val dnd = app.dndController

        if (isStart) {
            dnd.enableDnd()
            if (autoKill) {
                app.applicationScope.launch {
                    val result = app.appKillerController.killBackgroundApps()
                    app.database.smartDndDao().insertKillLog(
                        KillSessionLog(
                            appsKilledCount = result.killedCount,
                            freedMemoryMb = result.freedMemoryMb,
                            triggerSource = "SCHEDULE",
                            dndEnabled = true
                        )
                    )
                    app.notificationHelper.showActiveNotification(
                        freedMemoryMb = result.freedMemoryMb,
                        appsKilledCount = result.killedCount
                    )
                }
            } else {
                app.notificationHelper.showActiveNotification(0, 0)
            }
        } else {
            dnd.disableDnd()
            app.notificationHelper.cancelActiveNotification()
        }
    }
}
