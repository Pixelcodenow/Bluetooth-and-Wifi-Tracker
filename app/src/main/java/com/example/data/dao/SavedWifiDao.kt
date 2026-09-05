package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.SavedWifiEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWifiDao {

    @Query("SELECT * FROM saved_wifi_networks ORDER BY isFavorite DESC, lastSeenTimestamp DESC")
    fun getAllSavedNetworks(): Flow<List<SavedWifiEntity>>

    @Query("SELECT * FROM saved_wifi_networks WHERE bssid = :bssid LIMIT 1")
    suspend fun getNetworkByBssid(bssid: String): SavedWifiEntity?

    @Query("SELECT * FROM saved_wifi_networks WHERE id = :id LIMIT 1")
    fun getNetworkById(id: Long): Flow<SavedWifiEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(network: SavedWifiEntity): Long

    @Update
    suspend fun updateNetwork(network: SavedWifiEntity)

    @Query("UPDATE saved_wifi_networks SET customName = :customName, isFavorite = :isFavorite WHERE bssid = :bssid")
    suspend fun updateCustomization(bssid: String, customName: String, isFavorite: Boolean)

    @Query("UPDATE saved_wifi_networks SET isFavorite = :isFavorite WHERE bssid = :bssid")
    suspend fun toggleFavorite(bssid: String, isFavorite: Boolean)

    @Query("UPDATE saved_wifi_networks SET lastSeenTimestamp = :timestamp, lastRssi = :rssi, lastLatitude = :lat, lastLongitude = :lng, lastLocationLabel = :label WHERE bssid = :bssid")
    suspend fun updateLastSeen(bssid: String, timestamp: Long, rssi: Int, lat: Double?, lng: Double?, label: String?)

    @Query("DELETE FROM saved_wifi_networks WHERE bssid = :bssid")
    suspend fun deleteByBssid(bssid: String)

    @Query("DELETE FROM saved_wifi_networks")
    suspend fun deleteAll()
}
