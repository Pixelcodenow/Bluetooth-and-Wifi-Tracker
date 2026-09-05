package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.TrackerEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerEventDao {

    @Query("SELECT * FROM tracker_events WHERE trackerMac = :trackerMac ORDER BY timestamp DESC LIMIT 100")
    fun getEventsForTracker(trackerMac: String): Flow<List<TrackerEventEntity>>

    @Query("SELECT * FROM tracker_events ORDER BY timestamp DESC LIMIT 200")
    fun getAllEvents(): Flow<List<TrackerEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: TrackerEventEntity): Long

    @Query("DELETE FROM tracker_events WHERE trackerMac = :trackerMac")
    suspend fun clearEventsForTracker(trackerMac: String)

    @Query("DELETE FROM tracker_events")
    suspend fun clearAllEvents()
}
