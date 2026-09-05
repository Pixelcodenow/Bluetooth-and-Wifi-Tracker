package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.TrackerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackerDao {

    @Query("SELECT * FROM trackers ORDER BY isFavorite DESC, lastSeenTimestamp DESC")
    fun getAllTrackers(): Flow<List<TrackerEntity>>

    @Query("SELECT * FROM trackers WHERE macAddress = :mac LIMIT 1")
    suspend fun getTrackerByMac(mac: String): TrackerEntity?

    @Query("SELECT * FROM trackers WHERE id = :id LIMIT 1")
    fun getTrackerById(id: Long): Flow<TrackerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracker(tracker: TrackerEntity): Long

    @Update
    suspend fun updateTracker(tracker: TrackerEntity)

    @Query("UPDATE trackers SET customName = :name, colorArgb = :color, iconType = :icon WHERE id = :id")
    suspend fun updateCustomization(id: Long, name: String, color: Long, icon: String)

    @Query("UPDATE trackers SET lastSeenTimestamp = :timestamp, lastLatitude = :lat, lastLongitude = :lng, lastLocationLabel = :label WHERE macAddress = :mac")
    suspend fun updateLastLocation(mac: String, timestamp: Long, lat: Double?, lng: Double?, label: String?)

    @Query("UPDATE trackers SET batteryLevel = :battery WHERE macAddress = :mac")
    suspend fun updateBatteryLevel(mac: String, battery: Int)

    @Query("UPDATE trackers SET signalRssi = :rssi WHERE macAddress = :mac")
    suspend fun updateRssi(mac: String, rssi: Int)

    @Query("UPDATE trackers SET isFavorite = :favorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM trackers WHERE id = :id")
    suspend fun deleteTrackerById(id: Long)

    @Query("DELETE FROM trackers WHERE macAddress = :mac")
    suspend fun deleteTrackerByMac(mac: String)

    @Query("UPDATE trackers SET lastLatitude = NULL, lastLongitude = NULL, lastLocationLabel = NULL")
    suspend fun clearAllLocationData()
}
