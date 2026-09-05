package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.UnknownTrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UnknownTrackerDao {

    @Query("SELECT * FROM unknown_trackers WHERE isDismissed = 0 ORDER BY lastSeenTimestamp DESC")
    fun getActiveUnknownTrackers(): Flow<List<UnknownTrackerEntity>>

    @Query("SELECT * FROM unknown_trackers WHERE macAddress = :mac LIMIT 1")
    suspend fun getUnknownByMac(mac: String): UnknownTrackerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tracker: UnknownTrackerEntity)

    @Update
    suspend fun update(tracker: UnknownTrackerEntity)

    @Query("UPDATE unknown_trackers SET isDismissed = 1 WHERE macAddress = :mac")
    suspend fun dismissAlert(mac: String)

    @Query("DELETE FROM unknown_trackers")
    suspend fun clearAll()
}
