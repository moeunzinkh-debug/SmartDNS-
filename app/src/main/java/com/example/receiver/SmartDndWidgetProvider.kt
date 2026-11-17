package com.example.receiver

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.SmartDndApplication
import com.example.model.KillSessionLog
import kotlinx.coroutines.launch

class SmartDndWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_DND = "com.example.ACTION_TOGGLE_DND"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val app = context.applicationContext as? SmartDndApplication
        val isActive = app?.dndController?.isDndActive() ?: false

        for (appWidgetId in appWidgetIds) {
            updateWidgetView(context, appWidgetManager, appWidgetId, isActive)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE_DND) {
            val app = context.applicationContext as? SmartDndApplication ?: return
            val dnd = app.dndController

            if (!dnd.hasDndPermission()) {
                val settingsIntent = dnd.getDndPermissionIntent()
                context.startActivity(settingsIntent)
                return
            }

            val currentlyActive = dnd.isDndActive()
            if (currentlyActive) {
                dnd.disableDnd()
                app.notificationHelper.cancelActiveNotification()
                broadcastWidgetUpdate(context, false)
            } else {
                dnd.enableDnd()
                app.applicationScope.launch {
                    val killResult = app.appKillerController.killBackgroundApps()
                    app.database.smartDndDao().insertKillLog(
                        KillSessionLog(
                            appsKilledCount = killResult.killedCount,
                            freedMemoryMb = killResult.freedMemoryMb,
                            triggerSource = "WIDGET",
                            dndEnabled = true
                        )
                    )
                    app.notificationHelper.showActiveNotification(
                        freedMemoryMb = killResult.freedMemoryMb,
                        appsKilledCount = killResult.killedCount
                    )
                    broadcastWidgetUpdate(context, true)
                }
            }
        }
    }

    private fun broadcastWidgetUpdate(context: Context, isActive: Boolean) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, SmartDndWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        for (id in ids) {
            updateWidgetView(context, appWidgetManager, id, isActive)
        }
    }

    private fun updateWidgetView(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        isActive: Boolean
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_smart_dnd)

        val appIntent = Intent(context, MainActivity::class.java)
        val pendingAppIntent = PendingIntent.getActivity(
            context,
            100,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingAppIntent)

        val toggleIntent = Intent(context, SmartDndWidgetProvider::class.java).apply {
            action = ACTION_TOGGLE_DND
        }
        val pendingToggle = PendingIntent.getBroadcast(
            context,
            200,
            toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_button, pendingToggle)

        if (isActive) {
            views.setTextViewText(R.id.widget_status, "🌙 ACTIVE • SILENCED")
            views.setTextColor(R.id.widget_status, Color.parseColor("#00E5FF"))
            views.setTextViewText(R.id.widget_button, "TURN OFF")
        } else {
            views.setTextViewText(R.id.widget_status, "NORMAL MODE")
            views.setTextColor(R.id.widget_status, Color.parseColor("#90A4AE"))
            views.setTextViewText(R.id.widget_button, "ACTIVATE")
        }

        appWidgetManager.updateAppWidget(widgetId, views)
    }
}
