package com.example

import android.app.Application
import com.example.data.SmartDndDatabase
import com.example.model.AppWhitelistEntity
import com.example.system.AppKillerController
import com.example.system.CloudSyncManager
import com.example.system.DndController
import com.example.system.ScheduleManager
import com.example.system.SmartNotificationHelper
import com.example.system.SystemMetricsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmartDndApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { SmartDndDatabase.getInstance(this) }
    val dndController by lazy { DndController(this) }
    val appKillerController by lazy { AppKillerController(this, database.smartDndDao()) }
    val metricsProvider by lazy { SystemMetricsProvider(this) }
    val notificationHelper by lazy { SmartNotificationHelper(this) }
    val scheduleManager by lazy { ScheduleManager(this) }
    val cloudSyncManager by lazy { CloudSyncManager(this, database.smartDndDao()) }

    override fun onCreate() {
        super.onCreate()
        seedDefaultWhitelistIfEmpty()
    }

    private fun seedDefaultWhitelistIfEmpty() {
        applicationScope.launch {
            val count = database.smartDndDao().getWhitelistedPackageNames().size
            if (count == 0) {
                // Populate default recommended whitelist for smooth UX
                val defaultProtected = listOf(
                    AppWhitelistEntity("com.google.android.dialer", "Phone", true),
                    AppWhitelistEntity("com.google.android.apps.messaging", "Messages", true),
                    AppWhitelistEntity("com.whatsapp", "WhatsApp", false),
                    AppWhitelistEntity("org.telegram.messenger", "Telegram", false),
                    AppWhitelistEntity("com.google.android.deskclock", "Clock & Alarms", true),
                    AppWhitelistEntity("com.spotify.music", "Spotify", false),
                    AppWhitelistEntity("com.google.android.apps.maps", "Google Maps", true)
                )
                database.smartDndDao().insertWhitelistApps(defaultProtected)
            }
        }
    }
}
