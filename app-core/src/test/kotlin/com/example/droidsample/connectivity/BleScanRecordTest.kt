package com.example.droidsample.connectivity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class BleScanRecordTest {

    @Test
    fun testParseFlagsAndLocalName() {
        // Advertising payload:
        // [0] length=0x02, [1] type=0x01 (Flags), [2] flags=0x06 (LE General Discoverable + BR/EDR Not Supported)
        // [3] length=0x08, [4] type=0x09 (Complete Local Name), [5..11] "MyDevice" (8 bytes total with type) -> length=0x09
        val payload = byteArrayOf(
            0x02, 0x01.toByte(), 0x06.toByte(), // Flags: 0x06
            0x09, 0x09.toByte(), // Complete Name length 9 (1 byte type + 8 bytes text)
            'M'.code.toByte(), 'y'.code.toByte(), 'D'.code.toByte(), 'e'.code.toByte(),
            'v'.code.toByte(), 'i'.code.toByte(), 'c'.code.toByte(), 'e'.code.toByte()
        )

        val record = BleScanRecordParser.parseFromBytes(payload)
        assertEquals(0x06, record.advertiseFlags)
        assertEquals("MyDevice", record.deviceName)
    }

    @Test
    fun testParse16BitServiceUuids() {
        // Advertising payload:
        // length=0x05, type=0x03 (Complete list of 16-bit UUIDs)
        // UUID 0x180D (Heart Rate) -> little endian: 0x0D, 0x18
        // UUID 0x180F (Battery)    -> little endian: 0x0F, 0x18
        val payload = byteArrayOf(
            0x05, 0x03.toByte(),
            0x0D.toByte(), 0x18.toByte(),
            0x0F.toByte(), 0x18.toByte()
        )

        val record = BleScanRecordParser.parseFromBytes(payload)
        assertEquals(2, record.serviceUuids.size)

        val expectedHeartRate = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val expectedBattery = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")

        assertTrue(record.serviceUuids.contains(expectedHeartRate))
        assertTrue(record.serviceUuids.contains(expectedBattery))
    }

    @Test
    fun testParseManufacturerSpecificData() {
        // Company ID: 0x004C (Apple) -> little endian: 0x4C, 0x00
        // Data: 0x02, 0x15 (iBeacon prefix) + 4 bytes telemetry
        val payload = byteArrayOf(
            0x07, 0xFF.toByte(),
            0x4C.toByte(), 0x00.toByte(),
            0x02.toByte(), 0x15.toByte(), 0xAA.toByte(), 0xBB.toByte()
        )

        val record = BleScanRecordParser.parseFromBytes(payload)
        assertEquals(1, record.manufacturerSpecificData.size)
        assertTrue(record.manufacturerSpecificData.containsKey(0x004C))

        val data = record.manufacturerSpecificData[0x004C]
        assertNotNull(data)
        assertArrayEquals(byteArrayOf(0x02.toByte(), 0x15.toByte(), 0xAA.toByte(), 0xBB.toByte()), data)
    }

    @Test
    fun testParseTxPowerLevel() {
        // Tx Power AD type 0x0A: -12 dBm (in two's complement: 0xF4)
        val payload = byteArrayOf(
            0x02, 0x0A.toByte(), 0xF4.toByte()
        )

        val record = BleScanRecordParser.parseFromBytes(payload)
        assertEquals(-12, record.txPowerLevel)
    }

    @Test
    fun testEmptyOrCorruptPayload() {
        val emptyRecord = BleScanRecordParser.parseFromBytes(byteArrayOf())
        assertEquals(-1, emptyRecord.advertiseFlags)
        assertTrue(emptyRecord.serviceUuids.isEmpty())
        assertTrue(emptyRecord.manufacturerSpecificData.isEmpty())

        val corruptPayload = byteArrayOf(0x10, 0x01) // Claims 16 bytes length but truncated
        val corruptRecord = BleScanRecordParser.parseFromBytes(corruptPayload)
        assertTrue(corruptRecord.serviceUuids.isEmpty())
    }
}
