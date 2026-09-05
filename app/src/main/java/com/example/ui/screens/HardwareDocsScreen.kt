package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ble.BleGattSpec

private const val ESP32_ARDUINO_FIRMWARE = """
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

// Pins for ESP32-C3
#define BUTTON_PIN 9
#define BUZZER_PIN 10

// GATT UUIDs matching the Android Application
#define SERVICE_UUID        "a9b10001-e234-4b52-b8ec-694589d34e20"
#define CHAR_BUTTON_UUID    "a9b10002-e234-4b52-b8ec-694589d34e20"
#define CHAR_BUZZER_UUID    "a9b10003-e234-4b52-b8ec-694589d34e20"
#define CHAR_BATTERY_UUID   "00002a19-0000-1000-8000-00805f9b34fb"

BLEServer* pServer = NULL;
BLECharacteristic* pButtonChar = NULL;
BLECharacteristic* pBuzzerChar = NULL;
bool deviceConnected = false;
bool lastButtonState = HIGH;

class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer* pServer) {
      deviceConnected = true;
    };
    void onDisconnect(BLEServer* pServer) {
      deviceConnected = false;
      pServer->getAdvertising()->start();
    }
};

class BuzzerCallbacks: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pCharacteristic) {
      uint8_t* data = pCharacteristic->getData();
      if (pCharacteristic->getLength() > 0) {
        if (data[0] == 0x01) {
          // Buzzer ON (Find Tracker command)
          tone(BUZZER_PIN, 2600);
        } else {
          // Buzzer OFF
          noTone(BUZZER_PIN);
        }
      }
    }
};

void setup() {
  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  pinMode(BUZZER_PIN, OUTPUT);

  BLEDevice::init("PulseTag-ESP32");
  pServer = BLEDevice::createServer();
  pServer->setCallbacks(new ServerCallbacks());

  BLEService *pService = pServer->createService(SERVICE_UUID);

  // Button Notify Characteristic
  pButtonChar = pService->createCharacteristic(
                      CHAR_BUTTON_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_NOTIFY
                    );
  pButtonChar->addDescriptor(new BLE2902());

  // Buzzer Write Characteristic
  pBuzzerChar = pService->createCharacteristic(
                      CHAR_BUZZER_UUID,
                      BLECharacteristic::PROPERTY_READ |
                      BLECharacteristic::PROPERTY_WRITE |
                      BLECharacteristic::PROPERTY_WRITE_NR
                    );
  pBuzzerChar->setCallbacks(new BuzzerCallbacks());

  pService->start();
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SERVICE_UUID);
  pAdvertising->setScanResponse(true);
  pAdvertising->start();
  Serial.println("PulseTag BLE Advertising Started!");
}

void loop() {
  bool buttonState = digitalRead(BUTTON_PIN);
  // Detect button press (Active Low with pullup)
  if (buttonState == LOW && lastButtonState == HIGH) {
    if (deviceConnected) {
      uint8_t payload[] = { 0x01 }; // Click Event
      pButtonChar->setValue(payload, 1);
      pButtonChar->notify();
      Serial.println("Button Click Sent to Phone -> Find Phone Triggered!");
    }
    delay(250); // Debounce
  }
  lastButtonState = buttonState;
  delay(20);
}
"""

@Composable
fun HardwareDocsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DeveloperBoard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Platform Architecture & Documentation",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Technical comparison between Android BLE scanning and iOS Core Bluetooth, Bluetooth protocol classifications, RSSI physics, and DIY hardware firmware specifications.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Section 1: Android BLE vs iOS Core Bluetooth
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Android BLE vs iOS Core Bluetooth Differences",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    ComparisonRow(
                        category = "Device Identifiers",
                        androidText = "Android exposes real or randomized MAC addresses (e.g. AA:BB:CC:DD:EE:FF) directly through ScanResult.device.address.",
                        iosText = "iOS Core Bluetooth completely hides hardware MAC addresses for anti-tracking privacy, assigning an ephemeral random NSUUID per app."
                    )

                    ComparisonRow(
                        category = "Background Scanning",
                        androidText = "Supports background scanning via Foreground Services with connectedDevice type and ScanFilters without aggressive throttling.",
                        iosText = "Apple iOS requires explicit service UUID filters in CBCentralManagerScanOptionAllowDuplicatesKey, and stops duplicate advertising reports in the background."
                    )

                    ComparisonRow(
                        category = "GATT Discovery",
                        androidText = "Directly call gatt.discoverServices() and iterate services. Properties (READ, WRITE, NOTIFY) are returned in integer bitmasks.",
                        iosText = "Asynchronous peripheral.discoverServices() and peripheral.discoverCharacteristics()."
                    )
                }
            }
        }

        // Section 2: Bluetooth Protocol Classes & Limitations
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Waves, contentDescription = null, tint = Color(0xFF00C9FF))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BLE vs Classic Bluetooth vs Paired Devices",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Understanding the 3 distinct Bluetooth channels:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )

                    ProtocolItem(
                        title = "1. BLE Advertising Discovery (Real Scan)",
                        desc = "BLE peripherals broadcast periodic 31-byte advertisement packets on channels 37, 38, and 39. Any active BLE scanner detects them without requiring prior pairing or active connection."
                    )

                    ProtocolItem(
                        title = "2. Classic Bluetooth (BR/EDR)",
                        desc = "Classic audio headsets, car kits, and legacy speakers communicate over Bluetooth Classic. They only appear in Bluetooth inquiry if placed explicitly into 'Pairing/Discovery Mode'. Once bonded, they are retrieved from Android's bondedDevices API."
                    )

                    ProtocolItem(
                        title = "3. Paired vs Connected Devices",
                        desc = "Paired (Bonded) means encryption keys have been exchanged and stored by Android OS. Connected means an active radio link or GATT connection currently exists."
                    )
                }
            }
        }

        // Section 3: RSSI Signal Physics & Limitations
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "RSSI Physics & Distance Estimation",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "Why RSSI is an approximate proximity metric rather than an exact physical distance:",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
                    )

                    Text(
                        text = "• Radio Multipath Reflections: 2.4 GHz microwave radio signals bounce off concrete walls, metal furniture, and ceilings, causing constructive and destructive interference.\n\n• Human Body Attenuation: Human tissue is composed primarily of water, which absorbs 2.4 GHz radio waves. Holding your phone or standing between devices can drop RSSI by 10 to 15 dBm instantly.\n\n• Antenna Orientation: Polarization mismatch between tracker and phone antennas causes signal variations up to 8 dBm.\n\n• Approximate Tiers: Therefore, signal strength is classified into proximity tiers (Very Strong, Strong, Medium, Weak, Very Weak) rather than exact laser millimeters.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        // Section 4: Security & Permissions
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF10B981))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Android Permissions Model",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Text(
                        text = "• BLUETOOTH_SCAN: Required on Android 12+ (API 31+) to invoke BluetoothLeScanner.\n• BLUETOOTH_CONNECT: Required to query bonded devices and connect to GATT servers.\n• ACCESS_FINE_LOCATION: Required for beacon detection and recording location when tracker disconnects.\n• POST_NOTIFICATIONS: Required for 'Find My Phone' foreground alarms and background anti-stalking alerts.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }

        // Section 5: Bill of Materials (BOM) for Physical Tracker
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Hardware, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DIY Tracker Bill of Materials (BOM)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    BomRow("1. Microcontroller", "ESP32-C3 SuperMini, Seeed Xiao ESP32, or Nordic nRF52840")
                    BomRow("2. Buzzer", "3.3V Active Piezo Buzzer (2.6 kHz chirp) on GPIO 10")
                    BomRow("3. Button", "Tactile Momentary Pushbutton on GPIO 9 (Active Low with pullup)")
                    BomRow("4. Power Supply", "CR2032 3V Lithium Coin Cell & Holder, or 100mAh LiPo")
                    BomRow("5. Enclosure", "Keychain 3D printed ABS/PLA enclosure with lanyard loop")
                }
            }
        }

        // Section 6: GATT Profile Specifications
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "BLE GATT Profile Specification",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    GattSpecItem(
                        name = "Service UUID",
                        uuid = BleGattSpec.UUID_CUSTOM_TRACKER_SERVICE.toString(),
                        desc = "Primary BLE service advertised by the physical tracker"
                    )
                    GattSpecItem(
                        name = "Button Characteristic",
                        uuid = BleGattSpec.UUID_CHAR_BUTTON_EVENT.toString(),
                        desc = "Properties: READ | NOTIFY (Payload: 0x01 Click -> triggers phone alarm)"
                    )
                    GattSpecItem(
                        name = "Buzzer Characteristic",
                        uuid = BleGattSpec.UUID_CHAR_BUZZER_CONTROL.toString(),
                        desc = "Properties: READ | WRITE (Payload: 0x01 Start Beep, 0x00 Silence Beep)"
                    )
                    GattSpecItem(
                        name = "Battery Service",
                        uuid = BleGattSpec.UUID_CHAR_BATTERY_LEVEL.toString(),
                        desc = "Standard SIG Battery Level: READ | NOTIFY (Payload: 0-100%)"
                    )
                }
            }
        }

        // Section 7: Arduino ESP32 Firmware Code Box
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Code, contentDescription = null, tint = Color(0xFF00C9FF))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ESP32 Arduino Firmware (C++)",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("PulseTag Firmware", ESP32_ARDUINO_FIRMWARE.trimIndent())
                                clipboard.setPrimaryClip(clip)
                                Toast.makeText(context, "Firmware copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("copy_firmware_button")
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        color = Color(0xFF070C18)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(12.dp)
                        ) {
                            Text(
                                text = ESP32_ARDUINO_FIRMWARE.trimIndent(),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComparisonRow(category: String, androidText: String, iosText: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "• Android: $androidText",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "• iOS: $iosText",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}

@Composable
fun ProtocolItem(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        )
    }
}

@Composable
fun BomRow(title: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GattSpecItem(name: String, uuid: String, desc: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        )
        Text(
            text = uuid,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = desc,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
