package com.example.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.R
import com.example.SmartDndApplication
import com.example.model.KillSessionLog
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class SmartDndTileService : TileService() {

    private val app by lazy { application as SmartDndApplication }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val dndController = app.dndController
        if (!dndController.hasDndPermission()) {
            val intent = dndController.getDndPermissionIntent()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // PendingIntent for Android 14+
                startActivityAndCollapse(intent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val isActive = dndController.isDndActive()
        if (isActive) {
            dndController.disableDnd()
            app.notificationHelper.cancelActiveNotification()
            updateTileState(false)
        } else {
            dndController.enableDnd()
            app.applicationScope.launch {
                val killResult = app.appKillerController.killBackgroundApps()
                app.database.smartDndDao().insertKillLog(
                    KillSessionLog(
                        appsKilledCount = killResult.killedCount,
                        freedMemoryMb = killResult.freedMemoryMb,
                        triggerSource = "TILE",
                        dndEnabled = true
                    )
                )
                app.notificationHelper.showActiveNotification(
                    freedMemoryMb = killResult.freedMemoryMb,
                    appsKilledCount = killResult.killedCount
                )
            }
            updateTileState(true)
        }
    }

    private fun updateTileState(overrideActive: Boolean? = null) {
        val tile = qsTile ?: return
        val isActive = overrideActive ?: app.dndController.isDndActive()

        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.contentDescription = if (isActive) "Smart DND Active" else "Smart DND Inactive"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "DND ON • Filtered" else "Normal"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_smart_dnd_1793105166534)
        tile.updateTile()
    }
}
