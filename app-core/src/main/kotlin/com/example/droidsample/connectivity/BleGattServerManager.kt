package com.example.droidsample.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

class BleGattServerManager(private val context: Context) {

    companion object {
        // Sample custom echo & telemetry service
        val SERVICE_UUID: UUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val READ_WRITE_CHAR_UUID: UUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val leAdvertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var gattServer: BluetoothGattServer? = null
    private val connectedDevices = mutableSetOf<BluetoothDevice>()

    // Local in-memory characteristic state
    private val characteristicValues = mutableMapOf<UUID, ByteArray>()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _connectedDeviceCount = MutableStateFlow(0)
    val connectedDeviceCount: StateFlow<Int> = _connectedDeviceCount.asStateFlow()

    private val _serverLogs = MutableStateFlow<List<String>>(emptyList())
    val serverLogs: StateFlow<List<String>> = _serverLogs.asStateFlow()

    fun isAdvertisingSupported(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return adapter.isEnabled && (adapter.isMultipleAdvertisementSupported || leAdvertiser != null)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _isAdvertising.value = true
            log("BLE Advertising started successfully (TxPower=${settingsInEffect?.txPowerLevel})")
        }

        override fun onStartFailure(errorCode: Int) {
            _isAdvertising.value = false
            val error = when (errorCode) {
                ADVERTISE_FAILED_ALREADY_STARTED -> "Already started"
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Data too large"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Internal error"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many advertisers"
                else -> "Error code $errorCode"
            }
            log("BLE Advertising failed: $error")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val address = device?.address ?: "Unknown"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                device?.let { connectedDevices.add(it) }
                _connectedDeviceCount.value = connectedDevices.size
                log("Device connected: $address")
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                device?.let { connectedDevices.remove(it) }
                _connectedDeviceCount.value = connectedDevices.size
                log("Device disconnected: $address")
            }
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Service added: ${service?.uuid}")
            } else {
                log("Failed to add service: ${service?.uuid}, status: $status")
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            val server = gattServer ?: return
            val uuid = characteristic?.uuid
            val data = characteristicValues[uuid] ?: "Default GATT Value".toByteArray(Charsets.UTF_8)

            if (offset >= data.size) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                return
            }

            val slice = data.copyOfRange(offset, data.size)
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
            log("ReadRequest handled for $uuid, offset=$offset, returned ${slice.size} bytes")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val server = gattServer ?: return
            val uuid = characteristic?.uuid ?: return

            if (value != null) {
                characteristicValues[uuid] = value
                val str = String(value, Charsets.UTF_8)
                log("WriteRequest for $uuid: $str (${value.size} bytes)")
            }

            if (responseNeeded) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor?
        ) {
            val server = gattServer ?: return
            val descUuid = descriptor?.uuid
            val data = if (descUuid == CCCD_UUID) {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            } else {
                byteArrayOf()
            }
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data)
            log("DescriptorReadRequest handled for $descUuid")
        }

        @SuppressLint("MissingPermission")
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val server = gattServer ?: return
            log("DescriptorWriteRequest for ${descriptor?.uuid}")
            if (responseNeeded) {
                server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startServer(): Boolean {
        val manager = bluetoothManager ?: return false
        if (gattServer != null) return true

        return try {
            val server = manager.openGattServer(context, gattServerCallback)
            if (server != null) {
                gattServer = server
                setupDefaultServices(server)
                _isServerRunning.value = true
                log("GATT Server opened.")
                true
            } else {
                log("Unable to open GATT server.")
                false
            }
        } catch (e: SecurityException) {
            log("Missing BLUETOOTH_CONNECT for GATT server: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupDefaultServices(server: BluetoothGattServer) {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val readWriteChar = BluetoothGattCharacteristic(
            READ_WRITE_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        characteristicValues[READ_WRITE_CHAR_UUID] = "DroidSample GATT Server Initial Data".toByteArray(Charsets.UTF_8)

        val notifyChar = BluetoothGattCharacteristic(
            NOTIFY_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(
            CCCD_UUID,
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        notifyChar.addDescriptor(cccd)
        characteristicValues[NOTIFY_CHAR_UUID] = "Telemetry #0".toByteArray(Charsets.UTF_8)

        service.addCharacteristic(readWriteChar)
        service.addCharacteristic(notifyChar)

        server.addService(service)
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising(deviceName: String = "DroidSample-Host"): Boolean {
        val advertiser = leAdvertiser
        if (advertiser == null) {
            log("Advertiser not available")
            return false
        }
        if (_isAdvertising.value) return true

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        return try {
            advertiser.startAdvertising(settings, data, advertiseCallback)
            log("Requested startAdvertising...")
            true
        } catch (e: SecurityException) {
            log("Missing BLUETOOTH_ADVERTISE permission: ${e.message}")
            false
        } catch (e: Exception) {
            log("Error starting advertising: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        if (!_isAdvertising.value) return
        val advertiser = leAdvertiser ?: return
        try {
            advertiser.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        } finally {
            _isAdvertising.value = false
            log("Advertising stopped.")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendNotification(message: String): Int {
        val server = gattServer ?: return 0
        if (connectedDevices.isEmpty()) {
            log("Cannot send notification: no devices connected")
            return 0
        }

        val service = server.getService(SERVICE_UUID) ?: return 0
        val characteristic = service.getCharacteristic(NOTIFY_CHAR_UUID) ?: return 0

        val payload = message.toByteArray(Charsets.UTF_8)
        characteristicValues[NOTIFY_CHAR_UUID] = payload

        var successCount = 0
        for (device in connectedDevices) {
            val sent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                server.notifyCharacteristicChanged(device, characteristic, false, payload) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = payload
                @Suppress("DEPRECATION")
                server.notifyCharacteristicChanged(device, characteristic, false)
            }
            if (sent) successCount++
        }
        log("Notification sent to $successCount / ${connectedDevices.size} devices: $message")
        return successCount
    }

    @SuppressLint("MissingPermission")
    fun stopServer() {
        stopAdvertising()
        try {
            gattServer?.close()
        } catch (_: Exception) {}
        gattServer = null
        connectedDevices.clear()
        _isServerRunning.value = false
        _connectedDeviceCount.value = 0
        log("GATT Server closed.")
    }

    private fun log(msg: String) {
        val entry = "[${System.currentTimeMillis() % 100000}] $msg"
        _serverLogs.update { (listOf(entry) + it).take(100) }
    }
}
