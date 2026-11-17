package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.AppWhitelistEntity
import com.example.model.DndSchedule
import com.example.model.KillSessionLog

@Database(
    entities = [
        AppWhitelistEntity::class,
        KillSessionLog::class,
        DndSchedule::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SmartDndDatabase : RoomDatabase() {
    abstract fun smartDndDao(): SmartDndDao

    companion object {
        @Volatile
        private var INSTANCE: SmartDndDatabase? = null

        fun getInstance(context: Context): SmartDndDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartDndDatabase::class.java,
                    "smart_dnd.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
