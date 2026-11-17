package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppWhitelistEntity
import com.example.model.DndSchedule
import com.example.model.KillSessionLog
import com.example.system.AppProcessInfo
import com.example.system.BatteryMetrics
import com.example.system.CloudSyncResult
import com.example.system.KillExecutionResult
import com.example.system.RamMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiNotificationMessage(
    val title: String,
    val message: String,
    val isSuccess: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SmartDndApplication
    private val dao = app.database.smartDndDao()
    private val dnd = app.dndController
    private val killer = app.appKillerController
    private val metrics = app.metricsProvider
    private val notifHelper = app.notificationHelper
    private val scheduleMgr = app.scheduleManager
    private val cloudSync = app.cloudSyncManager

    // UI States
    private val _isDndActive = MutableStateFlow(dnd.isDndActive())
    val isDndActive: StateFlow<Boolean> = _isDndActive.asStateFlow()

    private val _hasDndPermission = MutableStateFlow(dnd.hasDndPermission())
    val hasDndPermission: StateFlow<Boolean> = _hasDndPermission.asStateFlow()

    private val _isOptimizing = MutableStateFlow(false)
    val isOptimizing: StateFlow<Boolean> = _isOptimizing.asStateFlow()

    private val _lastKillResult = MutableStateFlow<KillExecutionResult?>(null)
    val lastKillResult: StateFlow<KillExecutionResult?> = _lastKillResult.asStateFlow()

    private val _ramMetrics = MutableStateFlow(metrics.getRamMetrics())
    val ramMetrics: StateFlow<RamMetrics> = _ramMetrics.asStateFlow()

    val batteryMetrics: StateFlow<BatteryMetrics> = metrics.getBatteryMetricsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BatteryMetrics())

    val whitelistedApps: StateFlow<List<AppWhitelistEntity>> = dao.getAllWhitelistedApps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentKillLogs: StateFlow<List<KillSessionLog>> = dao.getRecentKillLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalAppsKilled: StateFlow<Int?> = dao.getTotalAppsKilledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalFreedMemoryMb: StateFlow<Long?> = dao.getTotalFreedMemoryMbFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val schedules: StateFlow<List<DndSchedule>> = dao.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _scannedProcesses = MutableStateFlow<List<AppProcessInfo>>(emptyList())
    val scannedProcesses: StateFlow<List<AppProcessInfo>> = _scannedProcesses.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _messages = MutableSharedFlow<UiNotificationMessage>()
    val messages: SharedFlow<UiNotificationMessage> = _messages.asSharedFlow()

    init {
        refreshState()
        refreshRam()
        scanProcesses()
    }

    fun refreshState() {
        _hasDndPermission.value = dnd.hasDndPermission()
        _isDndActive.value = dnd.isDndActive()
    }

    fun refreshRam() {
        _ramMetrics.value = metrics.getRamMetrics()
    }

    /**
     * ⚡ 1-Tap Activation:
     * When ON: activates DND, kills background apps, silences notifications, shows success status.
     * When OFF: disables DND, returns to normal notification mode.
     */
    fun toggleSmartDnd() {
        _hasDndPermission.value = dnd.hasDndPermission()
        if (!_hasDndPermission.value) {
            viewModelScope.launch {
                _messages.emit(
                    UiNotificationMessage(
                        title = "Permission Required",
                        message = "Please grant Notification Policy Access in Settings to enable DND.",
                        isSuccess = false
                    )
                )
            }
            return
        }

        val currentlyActive = dnd.isDndActive()
        if (currentlyActive) {
            // TURN OFF
            dnd.disableDnd()
            notifHelper.cancelActiveNotification()
            _isDndActive.value = false
            viewModelScope.launch {
                _messages.emit(
                    UiNotificationMessage(
                        title = "Smart DND Deactivated",
                        message = "System returned to normal notification mode.",
                        isSuccess = true
                    )
                )
            }
        } else {
            // TURN ON + KILL BACKGROUND APPS
            _isOptimizing.value = true
            dnd.enableDnd()
            _isDndActive.value = true

            viewModelScope.launch(Dispatchers.IO) {
                val killResult = killer.killBackgroundApps()
                _lastKillResult.value = killResult

                // Record in Room Database
                dao.insertKillLog(
                    KillSessionLog(
                        appsKilledCount = killResult.killedCount,
                        freedMemoryMb = killResult.freedMemoryMb,
                        triggerSource = "ONE_TAP",
                        dndEnabled = true
                    )
                )

                // Update status notification
                notifHelper.showActiveNotification(
                    freedMemoryMb = killResult.freedMemoryMb,
                    appsKilledCount = killResult.killedCount
                )

                _ramMetrics.value = metrics.getRamMetrics()
                _isOptimizing.value = false

                _messages.emit(
                    UiNotificationMessage(
                        title = "🌙 Smart DND Activated",
                        message = "DND enabled. Terminated ${killResult.killedCount} background apps, freed ~${killResult.freedMemoryMb} MB RAM.",
                        isSuccess = true
                    )
                )
                scanProcesses()
            }
        }
    }

    /**
     * Trigger manual background app kill without changing DND state
     */
    fun runManualAppKill() {
        _isOptimizing.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val killResult = killer.killBackgroundApps()
            _lastKillResult.value = killResult

            dao.insertKillLog(
                KillSessionLog(
                    appsKilledCount = killResult.killedCount,
                    freedMemoryMb = killResult.freedMemoryMb,
                    triggerSource = "MANUAL",
                    dndEnabled = _isDndActive.value
                )
            )

            _ramMetrics.value = metrics.getRamMetrics()
            _isOptimizing.value = false

            _messages.emit(
                UiNotificationMessage(
                    title = "⚡ Background Apps Cleaned",
                    message = "Killed ${killResult.killedCount} background apps. Freed ~${killResult.freedMemoryMb} MB RAM.",
                    isSuccess = true
                )
            )
            scanProcesses()
        }
    }

    /**
     * Terminate an individual package
     */
    fun killIndividualApp(packageName: String, appName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = killer.killSpecificApp(packageName)
            if (success) {
                _ramMetrics.value = metrics.getRamMetrics()
                _messages.emit(
                    UiNotificationMessage(
                        title = "App Terminated",
                        message = "Terminated background process for $appName.",
                        isSuccess = true
                    )
                )
                scanProcesses()
            }
        }
    }

    /**
     * Scan apps and processes
     */
    fun scanProcesses() {
        _isScanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val list = killer.scanRunningAndInstalledApps()
            _scannedProcesses.value = list
            _isScanning.value = false
        }
    }

    /**
     * Toggle app whitelist state
     */
    fun toggleWhitelistApp(packageName: String, appName: String, isCurrentlyWhitelisted: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isCurrentlyWhitelisted) {
                dao.removeWhitelistApp(packageName)
            } else {
                dao.insertWhitelistApp(
                    AppWhitelistEntity(
                        packageName = packageName,
                        appName = appName,
                        isSystemApp = false
                    )
                )
            }
            scanProcesses()
        }
    }

    /**
     * Schedules
     */
    fun addSchedule(title: String, startHour: Int, startMinute: Int, endHour: Int, endMinute: Int, autoKill: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val schedule = DndSchedule(
                title = title,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                autoKillBackground = autoKill,
                isEnabled = true
            )
            val id = dao.insertSchedule(schedule)
            scheduleMgr.registerSchedule(schedule.copy(id = id))
            _messages.emit(
                UiNotificationMessage(
                    title = "Schedule Created",
                    message = "Auto DND scheduled from ${schedule.formatStartTime()} to ${schedule.formatEndTime()}.",
                    isSuccess = true
                )
            )
        }
    }

    fun toggleSchedule(schedule: DndSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = schedule.copy(isEnabled = !schedule.isEnabled)
            dao.updateSchedule(updated)
            if (updated.isEnabled) {
                scheduleMgr.registerSchedule(updated)
            } else {
                scheduleMgr.cancelSchedule(updated.id)
            }
        }
    }

    fun deleteSchedule(schedule: DndSchedule) {
        viewModelScope.launch(Dispatchers.IO) {
            scheduleMgr.cancelSchedule(schedule.id)
            dao.deleteSchedule(schedule)
        }
    }

    /**
     * Backup & Sync
     */
    suspend fun exportBackupJson(): String {
        return cloudSync.exportLocalBackupJson()
    }

    suspend fun importBackupJson(jsonString: String): Int {
        val count = cloudSync.importLocalBackupJson(jsonString)
        scanProcesses()
        return count
    }

    suspend fun syncCloud(): CloudSyncResult {
        return cloudSync.syncWithCloudBackend()
    }

    fun getDndPermissionIntent() = dnd.getDndPermissionIntent()
    fun getDndStatusString() = dnd.getCurrentFilterName()
}
