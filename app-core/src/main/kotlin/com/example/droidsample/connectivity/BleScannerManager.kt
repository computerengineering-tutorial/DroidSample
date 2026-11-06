package com.example.droidsample.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

data class BleParsedScanRecord(
    val advertiseFlags: Int = -1,
    val serviceUuids: List<UUID> = emptyList(),
    val serviceData: Map<UUID, ByteArray> = emptyMap(),
    val manufacturerSpecificData: Map<Int, ByteArray> = emptyMap(),
    val txPowerLevel: Int? = null,
    val deviceName: String? = null
)

object BleScanRecordParser {
    private const val BASE_UUID_FORMAT = "%08x-0000-1000-8000-00805f9b34fb"

    fun parseFromBytes(bytes: ByteArray?): BleParsedScanRecord {
        if (bytes == null || bytes.isEmpty()) return BleParsedScanRecord()

        var advertiseFlags = -1
        val serviceUuids = mutableListOf<UUID>()
        val serviceData = mutableMapOf<UUID, ByteArray>()
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        var txPowerLevel: Int? = null
        var localName: String? = null

        var index = 0
        while (index < bytes.size) {
            val length = bytes[index++].toInt() and 0xFF
            if (length == 0) break
            if (index + length > bytes.size) break

            val type = bytes[index++].toInt() and 0xFF
            val dataLength = length - 1

            when (type) {
                0x01 -> { // Flags
                    if (dataLength >= 1) {
                        advertiseFlags = bytes[index].toInt() and 0xFF
                    }
                }
                0x02, 0x03 -> { // Incomplete / Complete 16-bit Service UUIDs
                    parseUuids(bytes, index, dataLength, 2, serviceUuids)
                }
                0x04, 0x05 -> { // Incomplete / Complete 32-bit Service UUIDs
                    parseUuids(bytes, index, dataLength, 4, serviceUuids)
                }
                0x06, 0x07 -> { // Incomplete / Complete 128-bit Service UUIDs
                    parseUuids(bytes, index, dataLength, 16, serviceUuids)
                }
                0x08, 0x09 -> { // Shortened / Complete Local Name
                    localName = String(bytes, index, dataLength, Charsets.UTF_8)
                }
                0x0A -> { // TX Power Level
                    if (dataLength >= 1) {
                        txPowerLevel = bytes[index].toInt()
                    }
                }
                0x16 -> { // Service Data - 16 bit UUID
                    if (dataLength >= 2) {
                        val uuid16 = (bytes[index].toInt() and 0xFF) or ((bytes[index + 1].toInt() and 0xFF) shl 8)
                        val uuid = from16BitUuid(uuid16)
                        val payload = bytes.copyOfRange(index + 2, index + dataLength)
                        serviceData[uuid] = payload
                    }
                }
                0xFF -> { // Manufacturer Specific Data
                    if (dataLength >= 2) {
                        val companyId = (bytes[index].toInt() and 0xFF) or
                                ((bytes[index + 1].toInt() and 0xFF) shl 8)
                        val data = bytes.copyOfRange(index + 2, index + dataLength)
                        manufacturerData[companyId] = data
                    }
                }
            }
            index += dataLength
        }

        return BleParsedScanRecord(
            advertiseFlags = advertiseFlags,
            serviceUuids = serviceUuids,
            serviceData = serviceData,
            manufacturerSpecificData = manufacturerData,
            txPowerLevel = txPowerLevel,
            deviceName = localName
        )
    }

    fun from16BitUuid(value: Int): UUID {
        return UUID.fromString(String.format(BASE_UUID_FORMAT, value and 0xFFFF))
    }

    private fun parseUuids(
        data: ByteArray,
        offset: Int,
        len: Int,
        uuidSize: Int,
        outList: MutableList<UUID>
    ) {
        var current = offset
        val end = offset + len
        while (current + uuidSize <= end) {
            when (uuidSize) {
                2 -> {
                    val uuid16 = (data[current].toInt() and 0xFF) or
                            ((data[current + 1].toInt() and 0xFF) shl 8)
                    outList.add(from16BitUuid(uuid16))
                }
                4 -> {
                    val uuid32 = (data[current].toLong() and 0xFF) or
                            ((data[current + 1].toLong() and 0xFF) shl 8) or
                            ((data[current + 2].toLong() and 0xFF) shl 16) or
                            ((data[current + 3].toLong() and 0xFF) shl 24)
                    val uuidStr = String.format("%08x-0000-1000-8000-00805f9b34fb", uuid32)
                    outList.add(UUID.fromString(uuidStr))
                }
                16 -> {
                    val bb = ByteBuffer.wrap(data, current, 16).order(ByteOrder.LITTLE_ENDIAN)
                    val lsb = bb.long
                    val msb = bb.long
                    outList.add(UUID(msb, lsb))
                }
            }
            current += uuidSize
        }
    }
}

data class BleDiscoveredDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val timestampNanos: Long,
    val parsedRecord: BleParsedScanRecord
)

enum class BleScanMode(val androidMode: Int) {
    LOW_LATENCY(ScanSettings.SCAN_MODE_LOW_LATENCY),
    BALANCED(ScanSettings.SCAN_MODE_BALANCED),
    LOW_POWER(ScanSettings.SCAN_MODE_LOW_POWER)
}

class BleScannerManager(context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bleScanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDiscoveredDevice>>(emptyList())
    val scanResults: StateFlow<List<BleDiscoveredDevice>> = _scanResults.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val rawBytes = result.scanRecord?.bytes
            val parsedRecord = if (rawBytes != null) {
                BleScanRecordParser.parseFromBytes(rawBytes)
            } else {
                convertNativeScanRecord(result.scanRecord)
            }

            val deviceName = result.device?.name
                ?: parsedRecord.deviceName
                ?: "Unknown BLE Device"

            val item = BleDiscoveredDevice(
                device = result.device,
                name = deviceName,
                address = result.device.address ?: "00:00:00:00:00:00",
                rssi = result.rssi,
                timestampNanos = result.timestampNanos,
                parsedRecord = parsedRecord
            )

            _scanResults.update { list ->
                val existingIndex = list.indexOfFirst { it.address == item.address }
                if (existingIndex >= 0) {
                    list.toMutableList().apply { set(existingIndex, item) }
                } else {
                    list + item
                }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results?.forEach { onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, it) }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
            _errorMessage.value = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "Application registration failed"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE Scan unsupported on this device"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal BLE scan error"
                else -> "BLE Scan failed with code: $errorCode"
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(
        mode: BleScanMode = BleScanMode.BALANCED,
        serviceUuidFilter: UUID? = null,
        nameFilter: String? = null,
        addressFilter: String? = null,
        manufacturerIdFilter: Int? = null
    ): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _errorMessage.value = "Bluetooth is not enabled"
            return false
        }
        val scanner = bleScanner
        if (scanner == null) {
            _errorMessage.value = "BLE Scanner not available"
            return false
        }

        _errorMessage.value = null
        val filters = mutableListOf<ScanFilter>()
        val filterBuilder = ScanFilter.Builder()
        var hasFilter = false

        if (serviceUuidFilter != null) {
            filterBuilder.setServiceUuid(ParcelUuid(serviceUuidFilter))
            hasFilter = true
        }
        if (!nameFilter.isNullOrBlank()) {
            filterBuilder.setDeviceName(nameFilter)
            hasFilter = true
        }
        if (!addressFilter.isNullOrBlank() && BluetoothAdapter.checkBluetoothAddress(addressFilter)) {
            filterBuilder.setDeviceAddress(addressFilter)
            hasFilter = true
        }
        if (manufacturerIdFilter != null) {
            filterBuilder.setManufacturerData(manufacturerIdFilter, byteArrayOf())
            hasFilter = true
        }
        if (hasFilter) {
            filters.add(filterBuilder.build())
        }

        val settings = ScanSettings.Builder()
            .setScanMode(mode.androidMode)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        return try {
            scanner.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            true
        } catch (e: SecurityException) {
            _errorMessage.value = "Missing BLUETOOTH_SCAN permission: ${e.message}"
            false
        } catch (e: Exception) {
            _errorMessage.value = "Failed to start BLE scan: ${e.message}"
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        val scanner = bleScanner ?: return
        try {
            scanner.stopScan(scanCallback)
        } catch (_: Exception) {
        } finally {
            _isScanning.value = false
        }
    }

    fun clearResults() {
        _scanResults.value = emptyList()
    }

    private fun convertNativeScanRecord(record: ScanRecord?): BleParsedScanRecord {
        record ?: return BleParsedScanRecord()
        val uuids = record.serviceUuids?.map { it.uuid } ?: emptyList()
        val manufacturerData = mutableMapOf<Int, ByteArray>()
        val recordManufacturerData = record.manufacturerSpecificData
        if (recordManufacturerData != null) {
            for (i in 0 until recordManufacturerData.size()) {
                val key = recordManufacturerData.keyAt(i)
                val value = recordManufacturerData.valueAt(i)
                if (value != null) {
                    manufacturerData[key] = value
                }
            }
        }

        val serviceDataMap = mutableMapOf<UUID, ByteArray>()
        record.serviceData.forEach { (parcelUuid, bytes) ->
            if (parcelUuid != null && bytes != null) {
                serviceDataMap[parcelUuid.uuid] = bytes
            }
        }

        return BleParsedScanRecord(
            advertiseFlags = record.advertiseFlags,
            serviceUuids = uuids,
            serviceData = serviceDataMap,
            manufacturerSpecificData = manufacturerData,
            txPowerLevel = record.txPowerLevel,
            deviceName = record.deviceName
        )
    }
}
