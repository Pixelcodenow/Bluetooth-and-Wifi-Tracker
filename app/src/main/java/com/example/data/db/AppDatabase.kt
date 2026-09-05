package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.TrackerDao
import com.example.data.dao.TrackerEventDao
import com.example.data.dao.UnknownTrackerDao
import com.example.data.entity.TrackerEntity
import com.example.data.entity.TrackerEventEntity
import com.example.data.entity.UnknownTrackerEntity

@Database(
    entities = [
        TrackerEntity::class,
        TrackerEventEntity::class,
        UnknownTrackerEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackerDao(): TrackerDao
    abstract fun trackerEventDao(): TrackerEventDao
    abstract fun unknownTrackerDao(): UnknownTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ble_tracker_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
