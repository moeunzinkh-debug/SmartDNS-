package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dnd_schedules")
data class DndSchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val isEnabled: Boolean = true,
    val startHour: Int, // 0-23
    val startMinute: Int, // 0-59
    val endHour: Int,
    val endMinute: Int,
    val daysOfWeekMask: Int = 127, // bitmask 7 days (all days default)
    val autoKillBackground: Boolean = true
) {
    fun formatStartTime(): String {
        return String.format("%02d:%02d", startHour, startMinute)
    }

    fun formatEndTime(): String {
        return String.format("%02d:%02d", endHour, endMinute)
    }
}
