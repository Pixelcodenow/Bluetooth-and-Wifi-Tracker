package com.example.data.repository

import com.example.data.dao.SavedWifiDao
import com.example.data.dao.WifiScanEventDao
import com.example.data.entity.SavedWifiEntity
import com.example.data.entity.WifiScanEventEntity
import com.example.wifi.WifiNetworkModel
import kotlinx.coroutines.flow.Flow

class WifiRepository(
    private val savedWifiDao: SavedWifiDao,
    private val wifiScanEventDao: WifiScanEventDao
) {
    val allSavedNetworks: Flow<List<SavedWifiEntity>> = savedWifiDao.getAllSavedNetworks()
    val recentScanEvents: Flow<List<WifiScanEventEntity>> = wifiScanEventDao.getRecentScanEvents()

    fun getEventsForBssid(bssid: String): Flow<List<WifiScanEventEntity>> {
        return wifiScanEventDao.getEventsForBssid(bssid)
    }

    suspend fun saveNetwork(network: WifiNetworkModel, isFavorite: Boolean = true) {
        val existing = savedWifiDao.getNetworkByBssid(network.bssid)
        val entity = SavedWifiEntity(
            id = existing?.id ?: 0L,
            bssid = network.bssid,
            ssid = network.ssid,
            customName = existing?.customName ?: network.customName,
            security = network.securityType.label,
            frequency = network.frequency,
            band = network.band,
            channel = network.channel,
            isFavorite = isFavorite,
            firstSeenTimestamp = existing?.firstSeenTimestamp ?: network.firstSeenTimestamp,
            lastSeenTimestamp = network.lastSeenTimestamp,
            lastRssi = network.smoothedRssi,
            lastLatitude = network.latitude,
            lastLongitude = network.longitude,
            lastLocationLabel = network.locationLabel
        )
        savedWifiDao.insertOrUpdate(entity)
    }

    suspend fun toggleFavorite(bssid: String, isFavorite: Boolean) {
        savedWifiDao.toggleFavorite(bssid, isFavorite)
    }

    suspend fun updateCustomName(bssid: String, customName: String, isFavorite: Boolean) {
        savedWifiDao.updateCustomization(bssid, customName, isFavorite)
    }

    suspend fun deleteSavedNetwork(bssid: String) {
        savedWifiDao.deleteByBssid(bssid)
    }

    suspend fun recordScanEvent(network: WifiNetworkModel) {
        if (network.bssid.isBlank()) return
        val event = WifiScanEventEntity(
            bssid = network.bssid,
            ssid = network.ssid,
            rssi = network.smoothedRssi,
            frequency = network.frequency,
            channel = network.channel,
            band = network.band,
            security = network.securityType.label,
            timestamp = System.currentTimeMillis(),
            latitude = network.latitude,
            longitude = network.longitude,
            locationLabel = network.locationLabel
        )
        wifiScanEventDao.insertEvent(event)
    }

    suspend fun clearHistory() {
        wifiScanEventDao.clearAll()
    }
}
