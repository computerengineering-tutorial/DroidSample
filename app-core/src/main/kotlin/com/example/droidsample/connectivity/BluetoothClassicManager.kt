package com.example.droidsample.connectivity

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class BluetoothAdapterStatus {
    UNAVAILABLE,
    OFF,
    TURNING_ON,
    ON,
    TURNING_OFF
}

enum class DeviceBondStatus {
    NONE,
    BONDING,
    BONDED;

    companion object {
        fun fromInt(state: Int): DeviceBondStatus = when (state) {
            BluetoothDevice.BOND_BONDED -> BONDED
            BluetoothDevice.BOND_BONDING -> BONDING
            else -> NONE
        }
    }
}

enum class BluetoothDeviceKind {
    CLASSIC,
    LE,
    DUAL,
    UNKNOWN;

    companion object {
        fun fromInt(type: Int): BluetoothDeviceKind = when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> CLASSIC
            BluetoothDevice.DEVICE_TYPE_LE -> LE
            BluetoothDevice.DEVICE_TYPE_DUAL -> DUAL
            else -> UNKNOWN
        }
    }
}

data class ClassicDeviceInfo(
    val name: String,
    val address: String,
    val bondState: DeviceBondStatus,
    val deviceKind: BluetoothDeviceKind,
    val majorClass: String,
    val rssi: Short? = null,
    val rawDevice: BluetoothDevice? = null
)

class BluetoothClassicManager(private val context: Context) {

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _adapterStatus = MutableStateFlow(resolveAdapterStatus())
    val adapterStatus: StateFlow<BluetoothAdapterStatus> = _adapterStatus.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<ClassicDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<ClassicDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<ClassicDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<ClassicDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var isReceiverRegistered = false

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    _adapterStatus.value = when (state) {
                        BluetoothAdapter.STATE_OFF -> BluetoothAdapterStatus.OFF
                        BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterStatus.TURNING_ON
                        BluetoothAdapter.STATE_ON -> {
                            refreshPairedDevices()
                            BluetoothAdapterStatus.ON
                        }
                        BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterStatus.TURNING_OFF
                        else -> BluetoothAdapterStatus.UNAVAILABLE
                    }
                }

                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    _isDiscovering.value = true
                }

                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isDiscovering.value = false
                }

                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).let {
                        if (it == Short.MIN_VALUE) null else it
                    }

                    device?.let { handleDiscoveredDevice(it, rssi) }
                }

                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                    device?.let { handleBondStateChanged(it, bondState) }
                }
            }
        }
    }

    init {
        registerReceiver()
        refreshPairedDevices()
    }

    fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(bluetoothReceiver, filter)
        }
        isReceiverRegistered = true
    }

    fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isReceiverRegistered = false
        }
    }

    private fun resolveAdapterStatus(): BluetoothAdapterStatus {
        val adapter = bluetoothAdapter ?: return BluetoothAdapterStatus.UNAVAILABLE
        return if (adapter.isEnabled) BluetoothAdapterStatus.ON else BluetoothAdapterStatus.OFF
    }

    @SuppressLint("MissingPermission")
    fun refreshPairedDevices() {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) {
            _pairedDevices.value = emptyList()
            return
        }
        try {
            val bonded = adapter.bondedDevices ?: emptySet()
            _pairedDevices.value = bonded.map { it.toClassicDeviceInfo() }
        } catch (e: SecurityException) {
            _lastError.value = "Missing BLUETOOTH_CONNECT permission: ${e.message}"
        }
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            _lastError.value = "Bluetooth is not enabled"
            return false
        }
        _discoveredDevices.value = emptyList()
        try {
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }
            return adapter.startDiscovery()
        } catch (e: SecurityException) {
            _lastError.value = "Missing BLUETOOTH_SCAN permission: ${e.message}"
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun cancelDiscovery(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        try {
            return adapter.cancelDiscovery()
        } catch (e: SecurityException) {
            _lastError.value = "Missing BLUETOOTH_SCAN permission: ${e.message}"
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDevice(device: BluetoothDevice): Boolean {
        try {
            return device.createBond()
        } catch (e: SecurityException) {
            _lastError.value = "Missing BLUETOOTH_CONNECT permission: ${e.message}"
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun pairDeviceByAddress(address: String): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return try {
            val device = adapter.getRemoteDevice(address)
            pairDevice(device)
        } catch (e: Exception) {
            _lastError.value = "Unable to pair with $address: ${e.message}"
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDiscoveredDevice(device: BluetoothDevice, rssi: Short?) {
        val info = device.toClassicDeviceInfo(rssi)
        _discoveredDevices.update { current ->
            val index = current.indexOfFirst { it.address == info.address }
            if (index >= 0) {
                current.toMutableList().apply { set(index, info) }
            } else {
                current + info
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBondStateChanged(device: BluetoothDevice, bondStateInt: Int) {
        val bondStatus = DeviceBondStatus.fromInt(bondStateInt)
        refreshPairedDevices()
        _discoveredDevices.update { current ->
            current.map {
                if (it.address == device.address) it.copy(bondState = bondStatus) else it
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toClassicDeviceInfo(rssi: Short? = null): ClassicDeviceInfo {
        val deviceName = try {
            this.name ?: "Unknown Device"
        } catch (_: SecurityException) {
            "Restricted Name"
        }
        val macAddress = this.address ?: "00:00:00:00:00:00"
        val bond = DeviceBondStatus.fromInt(this.bondState)
        val kind = BluetoothDeviceKind.fromInt(this.type)
        val majorClass = try {
            formatBluetoothClass(this.bluetoothClass)
        } catch (_: SecurityException) {
            "Unknown Class"
        }

        return ClassicDeviceInfo(
            name = deviceName,
            address = macAddress,
            bondState = bond,
            deviceKind = kind,
            majorClass = majorClass,
            rssi = rssi,
            rawDevice = this
        )
    }

    private fun formatBluetoothClass(btClass: BluetoothClass?): String {
        btClass ?: return "Unknown"
        return when (btClass.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> "Phone"
            BluetoothClass.Device.Major.COMPUTER -> "Computer"
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio / Video"
            BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
            BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            BluetoothClass.Device.Major.HEALTH -> "Health Device"
            BluetoothClass.Device.Major.NETWORKING -> "Networking"
            BluetoothClass.Device.Major.TOY -> "Toy"
            else -> "Misc (${btClass.deviceClass})"
        }
    }
}
