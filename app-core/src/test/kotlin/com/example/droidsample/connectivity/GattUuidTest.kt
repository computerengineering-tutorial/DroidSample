package com.example.droidsample.connectivity

import android.bluetooth.BluetoothGattCharacteristic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class GattUuidTest {

    @Test
    fun testBluetoothSig16BitExpansion() {
        // Heart Rate Service: 0x180D
        val heartRateUuid = BleScanRecordParser.from16BitUuid(0x180D)
        assertEquals(UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"), heartRateUuid)

        // Battery Service: 0x180F
        val batteryUuid = BleScanRecordParser.from16BitUuid(0x180F)
        assertEquals(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"), batteryUuid)

        // Device Information Service: 0x180A
        val devInfoUuid = BleScanRecordParser.from16BitUuid(0x180A)
        assertEquals(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), devInfoUuid)

        // Client Characteristic Configuration Descriptor (CCCD): 0x2902
        val cccdUuid = BleScanRecordParser.from16BitUuid(0x2902)
        assertEquals(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), cccdUuid)
    }

    @Test
    fun testCharacteristicPropertyDecoding() {
        // Read only
        val readProps = CharacteristicProperty.decodeProperties(BluetoothGattCharacteristic.PROPERTY_READ)
        assertEquals(listOf(CharacteristicProperty.READ), readProps)

        // Read + Write + Notify
        val combinedMask = BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY

        val combinedProps = CharacteristicProperty.decodeProperties(combinedMask)
        assertTrue(combinedProps.contains(CharacteristicProperty.READ))
        assertTrue(combinedProps.contains(CharacteristicProperty.WRITE))
        assertTrue(combinedProps.contains(CharacteristicProperty.NOTIFY))
        assertFalse(combinedProps.contains(CharacteristicProperty.INDICATE))
        assertFalse(combinedProps.contains(CharacteristicProperty.BROADCAST))

        // All flags
        val allMask = BluetoothGattCharacteristic.PROPERTY_BROADCAST or
                BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                BluetoothGattCharacteristic.PROPERTY_INDICATE or
                BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE

        val allProps = CharacteristicProperty.decodeProperties(allMask)
        assertEquals(CharacteristicProperty.entries.size, allProps.size)
    }

    @Test
    fun testCccdConstantMatchesExpansion() {
        assertEquals(
            BleGattClientManager.CCCD_UUID,
            BleScanRecordParser.from16BitUuid(0x2902)
        )
    }
}
