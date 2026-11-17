package com.example.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class DndController(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Checks if user has granted Do Not Disturb policy access.
     */
    fun hasDndPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
    }

    /**
     * Intent to open system settings where user grants DND permission.
     */
    fun getDndPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Returns true if Do Not Disturb is currently active.
     */
    fun isDndActive(): Boolean {
        if (!hasDndPermission()) return false
        val filter = notificationManager.currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    /**
     * Enables Do Not Disturb mode.
     * @param filter NotificationManager interruption filter (e.g., PRIORITY, NONE, ALARMS)
     * @return true if successfully set
     */
    fun enableDnd(
        filter: Int = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    ): Boolean {
        if (!hasDndPermission()) {
            Log.w("DndController", "Cannot enable DND: Notification policy access not granted")
            return false
        }
        return try {
            notificationManager.setInterruptionFilter(filter)
            true
        } catch (e: Exception) {
            Log.e("DndController", "Error setting interruption filter", e)
            false
        }
    }

    /**
     * Disables Do Not Disturb mode and returns to normal notifications.
     */
    fun disableDnd(): Boolean {
        if (!hasDndPermission()) {
            Log.w("DndController", "Cannot disable DND: Notification policy access not granted")
            return false
        }
        return try {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            true
        } catch (e: Exception) {
            Log.e("DndController", "Error disabling interruption filter", e)
            false
        }
    }

    /**
     * Gets description of current DND state.
     */
    fun getCurrentFilterName(): String {
        if (!hasDndPermission()) return "Permission Required"
        return when (notificationManager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "Normal (Off)"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Priority Only (DND ON)"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Total Silence (DND ON)"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Alarms Only (DND ON)"
            else -> "Inactive"
        }
    }
}
