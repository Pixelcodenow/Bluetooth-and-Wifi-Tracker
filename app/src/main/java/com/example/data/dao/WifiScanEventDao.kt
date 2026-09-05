package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.WifiScanEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WifiScanEventDao {

    @Query("SELECT * FROM wifi_scan_events ORDER BY timestamp DESC LIMIT 200")
    fun getRecentScanEvents(): Flow<List<WifiScanEventEntity>>

    @Query("SELECT * FROM wifi_scan_events WHERE bssid = :bssid ORDER BY timestamp DESC LIMIT 50")
    fun getEventsForBssid(bssid: String): Flow<List<WifiScanEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: WifiScanEventEntity): Long

    @Query("DELETE FROM wifi_scan_events WHERE timestamp < :cutoff")
    suspend fun pruneOldEvents(cutoff: Long)

    @Query("DELETE FROM wifi_scan_events")
    suspend fun clearAll()
}
