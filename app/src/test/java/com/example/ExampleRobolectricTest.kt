package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ble.BluetoothCompanyLookup
import com.example.ble.BluetoothServiceLookup
import com.example.ble.ConnectionState
import com.example.ble.ProximityLevel
import com.example.ble.SignalStrengthTier
import com.example.ble.TrackerLiveStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Bluetooth Tracker", appName)
  }

  @Test
  fun `verify proximity level calculation from RSSI`() {
    val closeTracker = TrackerLiveStatus("MAC1", ConnectionState.CONNECTED, rssi = -50)
    assertEquals(ProximityLevel.VERY_CLOSE, closeTracker.proximity)

    val mediumTracker = TrackerLiveStatus("MAC2", ConnectionState.CONNECTED, rssi = -65)
    assertEquals(ProximityLevel.CLOSE, mediumTracker.proximity)

    val nearbyTracker = TrackerLiveStatus("MAC3", ConnectionState.CONNECTED, rssi = -78)
    assertEquals(ProximityLevel.NEARBY, nearbyTracker.proximity)

    val farTracker = TrackerLiveStatus("MAC4", ConnectionState.CONNECTED, rssi = -90)
    assertEquals(ProximityLevel.FAR_AWAY, farTracker.proximity)

    val disconnectedTracker = TrackerLiveStatus("MAC5", ConnectionState.DISCONNECTED, rssi = -50)
    assertEquals(ProximityLevel.SIGNAL_LOST, disconnectedTracker.proximity)
  }

  @Test
  fun `verify signal strength tier calculation`() {
    assertEquals(SignalStrengthTier.VERY_STRONG, SignalStrengthTier.fromRssi(-45))
    assertEquals(SignalStrengthTier.STRONG, SignalStrengthTier.fromRssi(-60))
    assertEquals(SignalStrengthTier.MEDIUM, SignalStrengthTier.fromRssi(-70))
    assertEquals(SignalStrengthTier.WEAK, SignalStrengthTier.fromRssi(-80))
    assertEquals(SignalStrengthTier.VERY_WEAK, SignalStrengthTier.fromRssi(-90))
    assertEquals(SignalStrengthTier.SIGNAL_LOST, SignalStrengthTier.fromRssi(0))
  }

  @Test
  fun `verify company and service lookup`() {
    assertEquals("Apple, Inc.", BluetoothCompanyLookup.getCompanyName(0x004C))
    assertEquals("Samsung Electronics", BluetoothCompanyLookup.getCompanyName(0x0075))
    assertEquals("Google", BluetoothCompanyLookup.getCompanyName(0x00E0))

    assertEquals("Battery Service", BluetoothServiceLookup.getServiceName("0000180F-0000-1000-8000-00805f9b34fb"))
    assertEquals("Immediate Alert", BluetoothServiceLookup.getServiceName("00001802-0000-1000-8000-00805f9b34fb"))
  }

  @Test
  fun `verify 10 block proximity indicator calculation`() {
    val veryClose = com.example.ble.ProximityIndicator.fromSmoothedRssi(-45)
    assertEquals(com.example.ble.ProximityIndicator.VERY_CLOSE, veryClose)
    assertEquals(10, veryClose.filledBlocks)
    assertEquals("██████████", veryClose.blocksString)

    val close = com.example.ble.ProximityIndicator.fromSmoothedRssi(-60)
    assertEquals(com.example.ble.ProximityIndicator.CLOSE, close)
    assertEquals(8, close.filledBlocks)

    val nearby = com.example.ble.ProximityIndicator.fromSmoothedRssi(-70)
    assertEquals(com.example.ble.ProximityIndicator.NEARBY, nearby)
    assertEquals(6, nearby.filledBlocks)

    val far = com.example.ble.ProximityIndicator.fromSmoothedRssi(-80)
    assertEquals(com.example.ble.ProximityIndicator.FAR, far)
    assertEquals(3, far.filledBlocks)

    val veryFar = com.example.ble.ProximityIndicator.fromSmoothedRssi(-95)
    assertEquals(com.example.ble.ProximityIndicator.VERY_FAR, veryFar)
    assertEquals(1, veryFar.filledBlocks)

    val lost = com.example.ble.ProximityIndicator.fromSmoothedRssi(-45, isLost = true)
    assertEquals(com.example.ble.ProximityIndicator.SIGNAL_LOST, lost)
    assertEquals(0, lost.filledBlocks)
  }

  @Test
  fun `verify device lifecycle states`() {
    assertEquals("Active", com.example.ble.DeviceLifecycleState.ACTIVE.label)
    assertEquals("Lost", com.example.ble.DeviceLifecycleState.LOST.label)
    assertEquals("Getting closer", com.example.ble.SignalTrend.GETTING_CLOSER.label)
    assertEquals("Moving farther away", com.example.ble.SignalTrend.MOVING_FARTHER.label)
  }
}
