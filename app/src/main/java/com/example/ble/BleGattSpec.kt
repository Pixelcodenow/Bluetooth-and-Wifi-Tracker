package com.example.ble

import java.util.UUID

object BleGattSpec {
    // Standard Bluetooth SIG GATT Services & Characteristics
    val UUID_SERVICE_GENERIC_ACCESS: UUID = UUID.fromString("00001800-0000-1000-8000-00805f9b34fb")
    val UUID_SERVICE_DEVICE_INFO: UUID = UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb")
    
    val UUID_SERVICE_BATTERY: UUID = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_BATTERY_LEVEL: UUID = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb")

    val UUID_SERVICE_IMMEDIATE_ALERT: UUID = UUID.fromString("00001802-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_ALERT_LEVEL: UUID = UUID.fromString("00002A06-0000-1000-8000-00805f9b34fb")

    val UUID_SERVICE_LINK_LOSS: UUID = UUID.fromString("00001803-0000-1000-8000-00805f9b34fb")
    val UUID_SERVICE_TX_POWER: UUID = UUID.fromString("00001804-0000-1000-8000-00805f9b34fb")
    val UUID_CHAR_TX_POWER_LEVEL: UUID = UUID.fromString("00002A07-0000-1000-8000-00805f9b34fb")

    // Custom Modular TrackPulse GATT Service for Custom Tracker Firmware
    val UUID_CUSTOM_TRACKER_SERVICE: UUID = UUID.fromString("a9b10001-e234-4b52-b8ec-694589d34e20")
    
    // TRACKER -> PHONE: Notification of physical button presses
    val UUID_CHAR_BUTTON_EVENT: UUID = UUID.fromString("a9b10002-e234-4b52-b8ec-694589d34e20")
    
    // PHONE -> TRACKER: Buzzer beep control command
    val UUID_CHAR_BUZZER_CONTROL: UUID = UUID.fromString("a9b10003-e234-4b52-b8ec-694589d34e20")
    
    // PHONE <-> TRACKER: Configuration (alert tone, volume, interval)
    val UUID_CHAR_DEVICE_CONFIG: UUID = UUID.fromString("a9b10004-e234-4b52-b8ec-694589d34e20")

    // Standard Client Characteristic Configuration Descriptor
    val UUID_DESCRIPTOR_CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Buzzer Control Commands (PHONE -> TRACKER)
    const val CMD_BUZZER_STOP: Byte = 0x00
    const val CMD_BUZZER_START_BEEP: Byte = 0x01
    const val CMD_BUZZER_CHIRP_IDENTIFY: Byte = 0x02
    const val CMD_GET_STATUS: Byte = 0x03

    // Button Events (TRACKER -> PHONE)
    const val EVENT_BUTTON_SINGLE_CLICK: Byte = 0x01
    const val EVENT_BUTTON_DOUBLE_CLICK: Byte = 0x02
    const val EVENT_BUTTON_LONG_PRESS: Byte = 0x03

    // Immediate Alert Levels (Standard SIG)
    const val ALERT_LEVEL_NO_ALERT: Byte = 0x00
    const val ALERT_LEVEL_MILD: Byte = 0x01
    const val ALERT_LEVEL_HIGH: Byte = 0x02
}
