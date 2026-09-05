package com.example.ble

import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    SEARCHING,
    CONNECTING,
    CONNECTED,
    DISCONNECTING
}

enum class BluetoothDeviceType(val label: String) {
    BLE("Bluetooth Low Energy"),
    CLASSIC("Bluetooth Classic"),
    DUAL("Dual Mode"),
    UNKNOWN("Bluetooth Device")
}

enum class DeviceConnectionStatus(val label: String) {
    CONNECTED("Connected"),
    PAIRED("Paired"),
    NEARBY("Not Connected"),
    UNKNOWN("Unknown")
}

enum class DeviceLifecycleState(
    val label: String,
    val badgeColorArgb: Long
) {
    ACTIVE("Active", 0xFF10B981),               // Seen in last 2 seconds
    RECENTLY_SEEN("Recently Seen", 0xFFEAB308), // Not seen for 2 - 5 seconds
    OUT_OF_RANGE("Out of Range", 0xFFF97316),   // Not seen for 5 - 15 seconds
    LOST("Lost", 0xFF64748B)                    // Not seen for > 15 seconds
}

enum class ProximityIndicator(
    val label: String,
    val filledBlocks: Int,
    val blocksString: String,
    val minSmoothedRssi: Int,
    val colorArgb: Long,
    val description: String
) {
    VERY_CLOSE("VERY CLOSE", 10, "██████████", -50, 0xFF10B981, "Immediate proximity (< 1 m)"),
    CLOSE("CLOSE", 8, "████████░░", -65, 0xFF22C55E, "Close proximity (~1 - 3 m)"),
    NEARBY("NEARBY", 6, "██████░░░░", -75, 0xFFEAB308, "In the room (~3 - 7 m)"),
    FAR("FAR", 3, "███░░░░░░░", -85, 0xFFF97316, "Weak signal (~7 - 15 m)"),
    VERY_FAR("VERY FAR", 1, "█░░░░░░░░░", -120, 0xFFEF4444, "Fringe detection (> 15 m)"),
    SIGNAL_LOST("SIGNAL LOST", 0, "░░░░░░░░░░", -999, 0xFF64748B, "No signal detected");

    companion object {
        fun fromSmoothedRssi(smoothedRssi: Int, isLost: Boolean = false): ProximityIndicator {
            if (isLost || smoothedRssi == 0 || smoothedRssi <= -100) return SIGNAL_LOST
            return when {
                smoothedRssi >= -50 -> VERY_CLOSE
                smoothedRssi >= -65 -> CLOSE
                smoothedRssi >= -75 -> NEARBY
                smoothedRssi >= -85 -> FAR
                else -> VERY_FAR
            }
        }
    }
}

enum class SignalTrend(
    val label: String,
    val iconText: String,
    val description: String,
    val colorArgb: Long
) {
    GETTING_CLOSER("Getting closer", "↑", "Signal getting stronger", 0xFF10B981),
    MOVING_FARTHER("Moving farther away", "↓", "Signal getting weaker", 0xFFEF4444),
    STABLE("Distance appears stable", "→", "Signal stable", 0xFF38BDF8),
    SIGNAL_LOST("Signal lost", "⚠️", "Move back toward last stronger signal", 0xFFF59E0B)
}

enum class SignalStrengthTier(
    val label: String,
    val iconEmoji: String,
    val colorArgb: Long,
    val minRssi: Int,
    val approximateDistanceText: String
) {
    VERY_STRONG("Very Strong", "🟢", 0xFF10B981, -50, "Within 1 meter"),
    STRONG("Strong", "🟢", 0xFF22C55E, -65, "Around 1 - 3 meters"),
    MEDIUM("Medium", "🟡", 0xFFEAB308, -75, "Around 3 - 7 meters"),
    WEAK("Weak", "🟠", 0xFFF97316, -85, "Around 7 - 15 meters"),
    VERY_WEAK("Very Weak", "🔴", 0xFFEF4444, -120, "More than 15 meters (Fringe)"),
    SIGNAL_LOST("Signal Lost", "⚪", 0xFF94A3B8, -999, "No signal detected");

    companion object {
        fun fromRssi(rssi: Int): SignalStrengthTier {
            return when {
                rssi == 0 || rssi <= -120 -> SIGNAL_LOST
                rssi >= -50 -> VERY_STRONG
                rssi >= -65 -> STRONG
                rssi >= -75 -> MEDIUM
                rssi >= -85 -> WEAK
                else -> VERY_WEAK
            }
        }
    }
}

enum class ProximityLevel(val label: String, val description: String) {
    VERY_CLOSE("Very Close", "Within arm's reach (< 1.5 m)"),
    CLOSE("Close", "In the same room (~1.5 - 4 m)"),
    NEARBY("Nearby", "Nearby area (~4 - 10 m)"),
    FAR_AWAY("Far Away", "Weak signal (> 10 m)"),
    SIGNAL_LOST("Signal Lost", "No Bluetooth signal detected")
}

data class DiscoveredBleDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val isCustomTrackerCompatible: Boolean = false,
    val isSimulated: Boolean = false
) {
    val displayName: String
        get() = name.ifBlank { "BLE Device (${address.takeLast(5)})" }
}

data class GattCharacteristicInfo(
    val uuid: String,
    val name: String,
    val properties: List<String>,
    val valueHex: String? = null,
    val valueString: String? = null
)

data class GattServiceInfo(
    val uuid: String,
    val name: String,
    val characteristics: List<GattCharacteristicInfo>
)

data class DiscoveredBluetoothDevice(
    val address: String,
    val name: String,
    val rssi: Int,
    val smoothedRssi: Int = rssi,
    val lifecycleState: DeviceLifecycleState = DeviceLifecycleState.ACTIVE,
    val deviceType: BluetoothDeviceType = BluetoothDeviceType.BLE,
    val connectionStatus: DeviceConnectionStatus = DeviceConnectionStatus.NEARBY,
    val manufacturerId: Int? = null,
    val manufacturerName: String? = null,
    val manufacturerDataHex: String? = null,
    val serviceUuids: List<String> = emptyList(),
    val txPowerLevel: Int? = null,
    val batteryLevel: Int? = null,
    val firstSeenTimestamp: Long = System.currentTimeMillis(),
    val lastSeenTimestamp: Long = System.currentTimeMillis(),
    val lastKnownLatitude: Double? = null,
    val lastKnownLongitude: Double? = null,
    val lastKnownLocationLabel: String? = null,
    val lastKnownLocationTimestamp: Long? = null,
    val rssiHistory: List<Int> = emptyList(),
    val smoothedRssiHistory: List<Int> = emptyList(),
    val isUserTracker: Boolean = false,
    val isCustomTrackerCompatible: Boolean = false,
    val gattServices: List<GattServiceInfo> = emptyList(),
    val isGattConnecting: Boolean = false,
    val isGattConnected: Boolean = false
) {
    val displayName: String
        get() = name.ifBlank {
            // Priority resolution: if manufacturer known, describe as e.g. "Apple Accessory"
            if (!manufacturerName.isNullOrBlank()) {
                "$manufacturerName Device"
            } else {
                "Unknown BLE Device"
            }
        }

    val isNamed: Boolean
        get() = name.isNotBlank() && !name.equals("Unknown BLE Device", ignoreCase = true)

    val signalTier: SignalStrengthTier
        get() = SignalStrengthTier.fromRssi(if (smoothedRssi != 0) smoothedRssi else rssi)

    val proximityIndicator: ProximityIndicator
        get() = ProximityIndicator.fromSmoothedRssi(
            smoothedRssi = if (smoothedRssi != 0) smoothedRssi else rssi,
            isLost = lifecycleState == DeviceLifecycleState.LOST
        )

    val lastSeenAgoText: String
        get() {
            val diffSec = ((System.currentTimeMillis() - lastSeenTimestamp) / 1000).coerceAtLeast(0)
            return when {
                diffSec < 2 -> "Just now"
                diffSec < 60 -> "$diffSec sec ago"
                else -> "${diffSec / 60}m ago"
            }
        }

    val categoryLabel: String
        get() {
            val lower = (name + " " + (manufacturerName ?: "")).lowercase()
            return when {
                lower.contains("pc") || lower.contains("macbook") || lower.contains("laptop") || lower.contains("desktop") || lower.contains("windows") || lower.contains("computer") -> "Computer / PC"
                lower.contains("watch") || lower.contains("band") || lower.contains("fitbit") || lower.contains("garmin") -> "Smartwatch / Wearable"
                lower.contains("headphone") || lower.contains("airpod") || lower.contains("earbud") || lower.contains("buds") || lower.contains("wh-1000") || lower.contains("bose") -> "Headphones / Audio"
                lower.contains("speaker") || lower.contains("jbl") || lower.contains("soundbar") -> "Speaker"
                lower.contains("phone") || lower.contains("galaxy") || lower.contains("iphone") || lower.contains("pixel") -> "Phone / Tablet"
                lower.contains("tag") || lower.contains("tile") || lower.contains("tracker") || lower.contains("chipolo") || isCustomTrackerCompatible -> "Smart Tracker"
                else -> "Bluetooth Peripheral"
            }
        }
}

data class TrackerLiveStatus(
    val macAddress: String,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val rssi: Int = 0,
    val batteryLevel: Int? = null,
    val isBeeping: Boolean = false,
    val lastRssiTimestamp: Long = System.currentTimeMillis()
) {
    val proximity: ProximityLevel
        get() = when {
            connectionState != ConnectionState.CONNECTED -> ProximityLevel.SIGNAL_LOST
            rssi == 0 -> ProximityLevel.SIGNAL_LOST
            rssi >= -60 -> ProximityLevel.VERY_CLOSE
            rssi >= -75 -> ProximityLevel.CLOSE
            rssi >= -88 -> ProximityLevel.NEARBY
            else -> ProximityLevel.FAR_AWAY
        }

    val estimatedDistanceMeters: Double?
        get() = if (rssi != 0 && connectionState == ConnectionState.CONNECTED) {
            Math.pow(10.0, (-59.0 - rssi) / (10.0 * 2.0))
        } else null
}

object BluetoothCompanyLookup {
    private val companies = mapOf(
        0x004C to "Apple, Inc.",
        0x0006 to "Microsoft",
        0x00E0 to "Google",
        0x0075 to "Samsung Electronics",
        0x000A to "Qualcomm Technologies",
        0x000F to "Broadcom",
        0x0059 to "Nordic Semiconductor",
        0x0087 to "Garmin International",
        0x009E to "Bose Corporation",
        0x0157 to "Anhui Huami (Amazfit)",
        0x02D0 to "Tile, Inc.",
        0x0822 to "Chipolo d.o.o.",
        0x038F to "Xiaomi Inc.",
        0x002D to "Sony Corporation",
        0x0002 to "Intel Corp.",
        0x01DA to "Logitech Europe",
        0x0131 to "Cypress Semiconductor",
        0x0171 to "Amazon.com Services",
        0x027D to "Huawei Technologies",
        0x00D2 to "Dialog Semiconductor",
        0x000D to "Texas Instruments",
        0x0030 to "STMicroelectronics",
        0x0044 to "Dell Inc.",
        0x0056 to "HP Inc.",
        0x0061 to "Lenovo",
        0x012D to "Sennheiser electronic",
        0x00B6 to "Jabra (GN Audio)",
        0x02B0 to "Anker Innovations",
        0x0104 to "Realtek Semiconductor",
        0x0211 to "Espressif Systems",
        0x0399 to "OnePlus Technology",
        0x008A to "Jawbone",
        0x00E4 to "Beats Electronics",
        0x0057 to "Harman International (JBL)"
    )

    fun getCompanyName(companyId: Int): String? = companies[companyId]
}

object BluetoothServiceLookup {
    private val services = mapOf(
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "1802" to "Immediate Alert",
        "1803" to "Link Loss",
        "1804" to "Tx Power",
        "180A" to "Device Information",
        "180F" to "Battery Service",
        "180D" to "Heart Rate",
        "1812" to "Human Interface Device",
        "1819" to "Location & Navigation",
        "181C" to "User Data",
        "FEAA" to "Google Eddystone Beacon",
        "FD6F" to "Exposure Notification",
        "FEF3" to "Google Fast Pair",
        "FEED" to "Tile Service",
        "FEE0" to "Mi Band Service"
    )

    fun getServiceName(uuidStr: String): String {
        val clean = uuidStr.uppercase().replace("-", "")
        for ((key, name) in services) {
            if (clean.contains(key)) return name
        }
        if (clean.contains("A9B10001")) return "TrackPulse Tracker Service"
        return "Service (${uuidStr.take(8)}...)"
    }

    fun getCharacteristicName(uuidStr: String): String {
        val clean = uuidStr.uppercase().replace("-", "")
        return when {
            clean.contains("2A19") -> "Battery Level"
            clean.contains("2A06") -> "Alert Level"
            clean.contains("2A00") -> "Device Name"
            clean.contains("2A01") -> "Appearance"
            clean.contains("2A24") -> "Model Number"
            clean.contains("2A25") -> "Serial Number"
            clean.contains("2A26") -> "Firmware Revision"
            clean.contains("2A27") -> "Hardware Revision"
            clean.contains("2A28") -> "Software Revision"
            clean.contains("2A29") -> "Manufacturer Name"
            clean.contains("A9B10002") -> "Physical Button Event"
            clean.contains("A9B10003") -> "Buzzer Control Command"
            clean.contains("A9B10004") -> "Tracker Configuration"
            else -> "Characteristic (${uuidStr.take(8)}...)"
        }
    }
}
