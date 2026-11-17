package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "kill_logs")
data class KillSessionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val appsKilledCount: Int,
    val freedMemoryMb: Long,
    val triggerSource: String, // "ONE_TAP", "MANUAL", "SCHEDULE", "TILE", "WIDGET"
    val dndEnabled: Boolean
)
