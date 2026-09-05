package com.example.data.repository

import com.example.data.dao.TrackerDao
import com.example.data.dao.TrackerEventDao
import com.example.data.dao.UnknownTrackerDao
import com.example.data.entity.TrackerEntity
import com.example.data.entity.TrackerEventEntity
import com.example.data.entity.UnknownTrackerEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TrackerRepository(
    private val trackerDao: TrackerDao,
    private val trackerEventDao: TrackerEventDao,
    private val unknownTrackerDao: UnknownTrackerDao
) {
    val allTrackers: Flow<List<TrackerEntity>> = trackerDao.getAllTrackers()
    val allEvents: Flow<List<TrackerEventEntity>> = trackerEventDao.getAllEvents()
    val unknownTrackers: Flow<List<UnknownTrackerEntity>> = unknownTrackerDao.getActiveUnknownTrackers()

    fun getTrackerById(id: Long): Flow<TrackerEntity?> = trackerDao.getTrackerById(id)

    fun getEventsForTracker(mac: String): Flow<List<TrackerEventEntity>> =
        trackerEventDao.getEventsForTracker(mac)

    suspend fun getTrackerByMac(mac: String): TrackerEntity? = withContext(Dispatchers.IO) {
        trackerDao.getTrackerByMac(mac)
    }

    suspend fun insertTracker(tracker: TrackerEntity): Long = withContext(Dispatchers.IO) {
        trackerDao.insertTracker(tracker)
    }

    suspend fun updateCustomization(id: Long, name: String, color: Long, icon: String) =
        withContext(Dispatchers.IO) {
            trackerDao.updateCustomization(id, name, color, icon)
        }

    suspend fun updateLastLocation(mac: String, timestamp: Long, lat: Double?, lng: Double?, label: String?) =
        withContext(Dispatchers.IO) {
            trackerDao.updateLastLocation(mac, timestamp, lat, lng, label)
        }

    suspend fun updateBatteryLevel(mac: String, battery: Int) = withContext(Dispatchers.IO) {
        trackerDao.updateBatteryLevel(mac, battery)
    }

    suspend fun updateRssi(mac: String, rssi: Int) = withContext(Dispatchers.IO) {
        trackerDao.updateRssi(mac, rssi)
    }

    suspend fun toggleFavorite(id: Long, current: Boolean) = withContext(Dispatchers.IO) {
        trackerDao.updateFavorite(id, !current)
    }

    suspend fun deleteTracker(id: Long, mac: String) = withContext(Dispatchers.IO) {
        trackerDao.deleteTrackerById(id)
        trackerEventDao.clearEventsForTracker(mac)
    }

    suspend fun logEvent(mac: String, type: String, details: String, lat: Double? = null, lng: Double? = null) =
        withContext(Dispatchers.IO) {
            trackerEventDao.insertEvent(
                TrackerEventEntity(
                    trackerMac = mac,
                    eventType = type,
                    details = details,
                    latitude = lat,
                    longitude = lng
                )
            )
        }

    suspend fun recordUnknownBeacon(mac: String, name: String, rssi: Int) = withContext(Dispatchers.IO) {
        // If this beacon is already one of our registered trackers, ignore
        if (trackerDao.getTrackerByMac(mac) != null) return@withContext

        val existing = unknownTrackerDao.getUnknownByMac(mac)
        val now = System.currentTimeMillis()
        if (existing != null) {
            val count = existing.detectionCount + 1
            // Determine risk level if seen frequently across time intervals
            val timeDiffMinutes = (now - existing.firstSeenTimestamp) / (1000 * 60)
            val risk = when {
                count >= 5 && timeDiffMinutes >= 15 -> "HIGH"
                count >= 3 && timeDiffMinutes >= 5 -> "MEDIUM"
                else -> "LOW"
            }
            unknownTrackerDao.update(
                existing.copy(
                    lastSeenTimestamp = now,
                    detectionCount = count,
                    highestRssi = maxOf(existing.highestRssi, rssi),
                    riskLevel = risk
                )
            )
        } else {
            unknownTrackerDao.insertOrUpdate(
                UnknownTrackerEntity(
                    macAddress = mac,
                    deviceName = name.ifBlank { "Unpaired Beacon" },
                    firstSeenTimestamp = now,
                    lastSeenTimestamp = now,
                    detectionCount = 1,
                    highestRssi = rssi,
                    riskLevel = "LOW"
                )
            )
        }
    }

    suspend fun dismissUnknownAlert(mac: String) = withContext(Dispatchers.IO) {
        unknownTrackerDao.dismissAlert(mac)
    }

    suspend fun clearAllLocationData() = withContext(Dispatchers.IO) {
        trackerDao.clearAllLocationData()
    }

    suspend fun clearHistoryLogs() = withContext(Dispatchers.IO) {
        trackerEventDao.clearAllEvents()
        unknownTrackerDao.clearAll()
    }
}
