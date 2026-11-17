package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.AppWhitelistEntity
import com.example.model.DndSchedule
import com.example.model.KillSessionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface SmartDndDao {
    // Whitelist
    @Query("SELECT * FROM app_whitelist ORDER BY appName ASC")
    fun getAllWhitelistedApps(): Flow<List<AppWhitelistEntity>>

    @Query("SELECT packageName FROM app_whitelist")
    suspend fun getWhitelistedPackageNames(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM app_whitelist WHERE packageName = :packageName LIMIT 1)")
    suspend fun isAppWhitelisted(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistApp(app: AppWhitelistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistApps(apps: List<AppWhitelistEntity>)

    @Query("DELETE FROM app_whitelist WHERE packageName = :packageName")
    suspend fun removeWhitelistApp(packageName: String)

    @Query("DELETE FROM app_whitelist")
    suspend fun clearWhitelist()

    // Logs
    @Query("SELECT * FROM kill_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentKillLogs(): Flow<List<KillSessionLog>>

    @Query("SELECT SUM(appsKilledCount) FROM kill_logs")
    fun getTotalAppsKilledFlow(): Flow<Int?>

    @Query("SELECT SUM(freedMemoryMb) FROM kill_logs")
    fun getTotalFreedMemoryMbFlow(): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKillLog(log: KillSessionLog): Long

    @Query("DELETE FROM kill_logs")
    suspend fun clearLogs()

    // Schedules
    @Query("SELECT * FROM dnd_schedules ORDER BY id ASC")
    fun getAllSchedules(): Flow<List<DndSchedule>>

    @Query("SELECT * FROM dnd_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<DndSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: DndSchedule): Long

    @Update
    suspend fun updateSchedule(schedule: DndSchedule)

    @Delete
    suspend fun deleteSchedule(schedule: DndSchedule)
}
