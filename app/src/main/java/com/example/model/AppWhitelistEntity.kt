package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_whitelist")
data class AppWhitelistEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
