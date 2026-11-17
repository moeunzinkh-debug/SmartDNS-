package com.example.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.model.DndSchedule
import com.example.receiver.ScheduleAlarmReceiver
import java.util.Calendar

class ScheduleManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun registerSchedule(schedule: DndSchedule) {
        if (!schedule.isEnabled) {
            cancelSchedule(schedule.id)
            return
        }

        scheduleAlarm(schedule.id, schedule.startHour, schedule.startMinute, isStart = true, autoKill = schedule.autoKillBackground)
        scheduleAlarm(schedule.id + 10000, schedule.endHour, schedule.endMinute, isStart = false, autoKill = false)
    }

    private fun scheduleAlarm(
        requestId: Long,
        hour: Int,
        minute: Int,
        isStart: Boolean,
        autoKill: Boolean
    ) {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRIGGER
            putExtra(ScheduleAlarmReceiver.EXTRA_IS_START, isStart)
            putExtra(ScheduleAlarmReceiver.EXTRA_AUTO_KILL, autoKill)
            putExtra(ScheduleAlarmReceiver.EXTRA_SCHEDULE_ID, requestId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            // If time already passed today, set for next occurrence
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d("ScheduleManager", "Scheduled alarm for $hour:$minute (start=$isStart)")
        } catch (e: Exception) {
            Log.e("ScheduleManager", "Failed to schedule alarm", e)
        }
    }

    fun cancelSchedule(scheduleId: Long) {
        val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
            action = ScheduleAlarmReceiver.ACTION_SCHEDULE_TRIGGER
        }
        val startPending = PendingIntent.getBroadcast(
            context,
            scheduleId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endPending = PendingIntent.getBroadcast(
            context,
            (scheduleId + 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(startPending)
        alarmManager.cancel(endPending)
    }
}
