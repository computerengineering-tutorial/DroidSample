package com.example.droidsample.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

enum class GattConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY
}

enum class CharacteristicProperty(val flag: Int, val label: String) {
    BROADCAST(BluetoothGattCharacteristic.PROPERTY_BROADCAST, "Broadcast"),
    READ(BluetoothGattCharacteristic.PROPERTY_READ, "Read"),
    WRITE_NO_RESPONSE(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE, "Write No Resp"),
    WRITE(BluetoothGattCharacteristic.PROPERTY_WRITE, "Write"),
    NOTIFY(BluetoothGattCharacteristic.PROPERTY_NOTIFY, "Notify"),
    INDICATE(BluetoothGattCharacteristic.PROPERTY_INDICATE, "Indicate"),
    SIGNED_WRITE(BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE, "Signed Write");

    companion object {
        fun decodeProperties(properties: Int): List<CharacteristicProperty> {
            return entries.filter { (properties and it.flag) != 0 }
        }
    }
}

data class GattCharacteristicInfo(
    val uuid: UUID,
    val properties: List<CharacteristicProperty>,
    val permissions: Int,
    val descriptors: List<UUID>,
    val lastValue: ByteArray? = null,
    val isNotifying: Boolean = false,
    val lastUpdatedMillis: Long = 0L
) {
    val hexValue: String
        get() = lastValue?.joinToString(" ") { "%02X".format(it) } ?: "(empty)"

    val asciiValue: String
        get() = lastValue?.let { bytes ->
            String(bytes.map { if (it in 32..126) it.toInt().toChar() else '.' }.toCharArray())
        } ?: ""

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GattCharacteristicInfo
        if (uuid != other.uuid) return false
        if (properties != other.properties) return false
        if (permissions != other.permissions) return false
        if (descriptors != other.descriptors) return false
        if (lastValue != null) {
            if (other.lastValue == null) return false
            if (!lastValue.contentEquals(other.lastValue)) return false
        } else if (other.lastValue != null) return false
        if (isNotifying != other.isNotifying) return false
        return lastUpdatedMillis == other.lastUpdatedMillis
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + properties.hashCode()
        result = 31 * result + permissions
        result = 31 * result + descriptors.hashCode()
        result = 31 * result + (lastValue?.contentHashCode() ?: 0)
        result = 31 * result + isNotifying.hashCode()
        result = 31 * result + lastUpdatedMillis.hashCode()
        return result
    }
}

data class GattServiceInfo(
    val uuid: UUID,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>
)

class BleGattClientManager(private val context: Context) {

    companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null

    private val _connectionState = MutableStateFlow(GattConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GattConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _connectedDeviceAddress = MutableStateFlow<String?>(null)
    val connectedDeviceAddress: StateFlow<String?> = _connectedDeviceAddress.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            log("ConnectionStateChange: status=$status, newState=$newState")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        _connectionState.value = GattConnectionState.CONNECTED
                        log("Connected. Discovering services...")
                        _connectionState.value = GattConnectionState.DISCOVERING_SERVICES
                        gatt?.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        _connectionState.value = GattConnectionState.DISCONNECTED
                        _services.value = emptyList()
                        close()
                        log("Disconnected successfully.")
                    }
                }
            } else {
                _connectionState.value = GattConnectionState.DISCONNECTED
                _services.value = emptyList()
                close()
                log("Connection error: status=$status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                log("Services discovered: ${gatt.services.size} services found")
                val parsedServices = gatt.services.map { service ->
                    GattServiceInfo(
                        uuid = service.uuid,
                        isPrimary = service.type == BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics = service.characteristics.map { char ->
                            GattCharacteristicInfo(
                                uuid = char.uuid,
                                properties = CharacteristicProperty.decodeProperties(char.properties),
                                permissions = char.permissions,
                                descriptors = char.descriptors.map { it.uuid }
                            )
                        }
                    )
                }
                _services.value = parsedServices
                _connectionState.value = GattConnectionState.READY
            } else {
                log("Service discovery failed with status: $status")
            }
        }

        // Android 13+ (API 33)
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            handleCharacteristicRead(characteristic, value, status)
        }

        // Legacy pre-API 33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic != null) {
                handleCharacteristicRead(characteristic, characteristic.value ?: byteArrayOf(), status)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val uuid = characteristic?.uuid
            if (status == BluetoothGatt.GATT_SUCCESS) {
                log("Write SUCCESS for char: $uuid")
            } else {
                log("Write FAILED for char: $uuid with status: $status")
            }
        }

        // Android 13+ (API 33)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicChanged(characteristic, value)
        }

        // Legacy pre-API 33
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU && characteristic != null) {
                handleCharacteristicChanged(characteristic, characteristic.value ?: byteArrayOf())
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            log("Descriptor write status: $status on ${descriptor?.uuid}")
        }
    }

    private fun handleCharacteristicRead(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        status: Int
    ) {
        val uuid = characteristic.uuid
        if (status == BluetoothGatt.GATT_SUCCESS) {
            log("Read SUCCESS for $uuid [${value.size} bytes]")
            updateCharacteristicValue(uuid, value)
        } else {
            log("Read FAILED for $uuid status: $status")
        }
    }

    private fun handleCharacteristicChanged(
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ) {
        val uuid = characteristic.uuid
        log("Notification from $uuid: ${value.joinToString(" ") { "%02X".format(it) }}")
        updateCharacteristicValue(uuid, value)
    }

    private fun updateCharacteristicValue(charUuid: UUID, value: ByteArray) {
        val now = System.currentTimeMillis()
        _services.update { currentServices ->
            currentServices.map { svc ->
                svc.copy(
                    characteristics = svc.characteristics.map { ch ->
                        if (ch.uuid == charUuid) {
                            ch.copy(lastValue = value, lastUpdatedMillis = now)
                        } else ch
                    }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice, autoConnect: Boolean = false): Boolean {
        close()
        _connectionState.value = GattConnectionState.CONNECTING
        _connectedDeviceName.value = try { device.name ?: "Unknown" } catch (_: SecurityException) { "Protected" }
        _connectedDeviceAddress.value = device.address

        log("Connecting to ${device.address}...")
        return try {
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, autoConnect, gattCallback)
            }
            true
        } catch (e: SecurityException) {
            log("SecurityException on connect: ${e.message}")
            _connectionState.value = GattConnectionState.DISCONNECTED
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.let { gatt ->
            log("Disconnecting...")
            try {
                gatt.disconnect()
            } catch (e: SecurityException) {
                log("Error on disconnect: ${e.message}")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        try {
            bluetoothGatt?.close()
        } catch (_: Exception) {}
        bluetoothGatt = null
    }

    @SuppressLint("MissingPermission")
    fun readCharacteristic(serviceUuid: UUID, charUuid: UUID): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(charUuid) ?: return false
        log("Reading characteristic $charUuid...")
        return try {
            gatt.readCharacteristic(char)
        } catch (e: SecurityException) {
            log("Read characteristic failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        data: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(charUuid) ?: return false

        log("Writing ${data.size} bytes to $charUuid...")
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeCharacteristic(char, data, writeType)
                result == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                char.value = data
                char.writeType = writeType
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
        } catch (e: SecurityException) {
            log("Write characteristic failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun setNotification(serviceUuid: UUID, charUuid: UUID, enable: Boolean): Boolean {
        val gatt = bluetoothGatt ?: return false
        val service = gatt.getService(serviceUuid) ?: return false
        val char = service.getCharacteristic(charUuid) ?: return false

        log("Setting notification=$enable on $charUuid")
        val notificationSet = try {
            gatt.setCharacteristicNotification(char, enable)
        } catch (e: SecurityException) {
            log("setCharacteristicNotification failed: ${e.message}")
            return false
        }

        if (!notificationSet) return false

        val descriptor = char.getDescriptor(CCCD_UUID)
        if (descriptor == null) {
            log("CCCD descriptor (0x2902) not found on $charUuid")
            return false
        }

        val properties = char.properties
        val isIndicate = (properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
        val descriptorValue = when {
            !enable -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            isIndicate -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, descriptorValue) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = descriptorValue
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        } catch (e: SecurityException) {
            log("writeDescriptor failed: ${e.message}")
            false
        } finally {
            _services.update { currentServices ->
                currentServices.map { svc ->
                    svc.copy(
                        characteristics = svc.characteristics.map { ch ->
                            if (ch.uuid == charUuid) ch.copy(isNotifying = enable) else ch
                        }
                    )
                }
            }
        }
    }

    private fun log(message: String) {
        val entry = "[${System.currentTimeMillis() % 100000}] $message"
        _logMessages.update { (listOf(entry) + it).take(100) }
    }
}
