package com.example.droidsample.connectivity

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class UsbEndpointDirection {
    IN,
    OUT;

    companion object {
        fun fromInt(direction: Int): UsbEndpointDirection = when (direction) {
            UsbConstants.USB_DIR_IN -> IN
            else -> OUT
        }
    }
}

enum class UsbEndpointKind {
    BULK,
    INTERRUPT,
    CONTROL,
    ISOCHRONOUS,
    UNKNOWN;

    companion object {
        fun fromInt(type: Int): UsbEndpointKind = when (type) {
            UsbConstants.USB_ENDPOINT_XFER_BULK -> BULK
            UsbConstants.USB_ENDPOINT_XFER_INT -> INTERRUPT
            UsbConstants.USB_ENDPOINT_XFER_CONTROL -> CONTROL
            UsbConstants.USB_ENDPOINT_XFER_ISOC -> ISOCHRONOUS
            else -> UNKNOWN
        }
    }
}

data class UsbEndpointSpec(
    val address: Int,
    val endpointNumber: Int,
    val direction: UsbEndpointDirection,
    val kind: UsbEndpointKind,
    val maxPacketSize: Int,
    val interval: Int
)

data class UsbInterfaceSpec(
    val id: Int,
    val name: String?,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val className: String,
    val endpoints: List<UsbEndpointSpec>
)

data class UsbDeviceItem(
    val deviceId: Int,
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val className: String,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val hasPermission: Boolean,
    val interfaces: List<UsbInterfaceSpec>,
    val rawDevice: UsbDevice
) {
    val vidHex: String = "0x%04X".format(vendorId)
    val pidHex: String = "0x%04X".format(productId)
}

class UsbHostManager(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.example.droidsample.USB_PERMISSION"

        fun resolveUsbClassName(usbClass: Int): String = when (usbClass) {
            UsbConstants.USB_CLASS_APP_SPEC -> "Application Specific"
            UsbConstants.USB_CLASS_AUDIO -> "Audio"
            UsbConstants.USB_CLASS_CDC_DATA -> "CDC Data"
            UsbConstants.USB_CLASS_COMM -> "Communication"
            UsbConstants.USB_CLASS_CONTENT_SEC -> "Content Security"
            UsbConstants.USB_CLASS_CSCID -> "Smart Card"
            UsbConstants.USB_CLASS_HID -> "Human Interface Device (HID)"
            UsbConstants.USB_CLASS_HUB -> "Hub"
            UsbConstants.USB_CLASS_MASS_STORAGE -> "Mass Storage"
            UsbConstants.USB_CLASS_MISC -> "Miscellaneous"
            UsbConstants.USB_CLASS_PHYSICA -> "Physical"
            UsbConstants.USB_CLASS_PRINTER -> "Printer"
            UsbConstants.USB_CLASS_STILL_IMAGE -> "Still Image"
            UsbConstants.USB_CLASS_VENDOR_SPEC -> "Vendor Specific"
            UsbConstants.USB_CLASS_VIDEO -> "Video"
            UsbConstants.USB_CLASS_WIRELESS_CONTROLLER -> "Wireless Controller"
            else -> "Unknown (Class 0x%02X)".format(usbClass)
        }
    }

    private val usbManager: UsbManager? =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    private val _devices = MutableStateFlow<List<UsbDeviceItem>>(emptyList())
    val devices: StateFlow<List<UsbDeviceItem>> = _devices.asStateFlow()

    private val _statusLogs = MutableStateFlow<List<String>>(emptyList())
    val statusLogs: StateFlow<List<String>> = _statusLogs.asStateFlow()

    private var isReceiverRegistered = false

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = getDeviceFromIntent(intent)
                    log("USB Device attached: ${device?.deviceName}")
                    refreshDevices()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device: UsbDevice? = getDeviceFromIntent(intent)
                    log("USB Device detached: ${device?.deviceName}")
                    refreshDevices()
                }

                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = getDeviceFromIntent(intent)
                        val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                        if (granted && device != null) {
                            log("USB Permission GRANTED for ${device.deviceName}")
                        } else {
                            log("USB Permission DENIED for ${device?.deviceName}")
                        }
                        refreshDevices()
                    }
                }
            }
        }
    }

    init {
        registerReceiver()
        refreshDevices()
    }

    fun registerReceiver() {
        if (isReceiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        isReceiverRegistered = true
    }

    fun unregisterReceiver() {
        if (!isReceiverRegistered) return
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: IllegalArgumentException) {
        } finally {
            isReceiverRegistered = false
        }
    }

    fun refreshDevices() {
        val manager = usbManager
        if (manager == null) {
            log("UsbManager is null on this device.")
            _devices.value = emptyList()
            return
        }

        val deviceMap = manager.deviceList
        val parsedList = deviceMap.values.map { usbDevice ->
            mapToDeviceItem(manager, usbDevice)
        }
        _devices.value = parsedList
        log("Enumerated ${parsedList.size} USB device(s).")
    }

    fun requestPermission(device: UsbDevice) {
        val manager = usbManager ?: return
        if (manager.hasPermission(device)) {
            log("Permission already held for ${device.deviceName}")
            refreshDevices()
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        manager.requestPermission(device, permissionIntent)
        log("Requested USB permission for ${device.deviceName}")
    }

    fun performBulkTransfer(
        device: UsbDevice,
        endpointAddress: Int,
        data: ByteArray,
        timeoutMillis: Int = 1000
    ): Result<Int> {
        val manager = usbManager ?: return Result.failure(IllegalStateException("UsbManager unavailable"))
        if (!manager.hasPermission(device)) {
            return Result.failure(SecurityException("USB Permission not granted for ${device.deviceName}"))
        }

        val connection: UsbDeviceConnection = manager.openDevice(device)
            ?: return Result.failure(IllegalStateException("Could not open UsbDeviceConnection"))

        try {
            // Find interface containing endpoint
            var targetInterface: UsbInterface? = null
            var targetEndpoint: UsbEndpoint? = null

            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                for (j in 0 until iface.endpointCount) {
                    val ep = iface.getEndpoint(j)
                    if (ep.address == endpointAddress) {
                        targetInterface = iface
                        targetEndpoint = ep
                        break
                    }
                }
                if (targetEndpoint != null) break
            }

            if (targetInterface == null || targetEndpoint == null) {
                return Result.failure(IllegalArgumentException("Endpoint 0x%02X not found".format(endpointAddress)))
            }

            val claimed = connection.claimInterface(targetInterface, true)
            if (!claimed) {
                return Result.failure(IllegalStateException("Failed to claim USB interface ${targetInterface.id}"))
            }

            return try {
                val transferred = connection.bulkTransfer(
                    targetEndpoint,
                    data,
                    data.size,
                    timeoutMillis
                )
                if (transferred >= 0) {
                    log("Bulk transfer success: $transferred bytes")
                    Result.success(transferred)
                } else {
                    Result.failure(IllegalStateException("Bulk transfer failed with return code $transferred"))
                }
            } finally {
                connection.releaseInterface(targetInterface)
            }
        } finally {
            connection.close()
        }
    }

    private fun mapToDeviceItem(manager: UsbManager, device: UsbDevice): UsbDeviceItem {
        val hasPerm = manager.hasPermission(device)
        val interfaces = mutableListOf<UsbInterfaceSpec>()

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val endpoints = mutableListOf<UsbEndpointSpec>()
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                endpoints.add(
                    UsbEndpointSpec(
                        address = ep.address,
                        endpointNumber = ep.endpointNumber,
                        direction = UsbEndpointDirection.fromInt(ep.direction),
                        kind = UsbEndpointKind.fromInt(ep.type),
                        maxPacketSize = ep.maxPacketSize,
                        interval = ep.interval
                    )
                )
            }
            interfaces.add(
                UsbInterfaceSpec(
                    id = iface.id,
                    name = iface.name,
                    interfaceClass = iface.interfaceClass,
                    interfaceSubclass = iface.interfaceSubclass,
                    interfaceProtocol = iface.interfaceProtocol,
                    className = resolveUsbClassName(iface.interfaceClass),
                    endpoints = endpoints
                )
            )
        }

        val manufacturer = if (hasPerm) {
            try { device.manufacturerName } catch (_: SecurityException) { null }
        } else null

        val product = if (hasPerm) {
            try { device.productName } catch (_: SecurityException) { null }
        } else null

        val serial = if (hasPerm) {
            try { device.serialNumber } catch (_: SecurityException) { null }
        } else null

        return UsbDeviceItem(
            deviceId = device.deviceId,
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            className = resolveUsbClassName(device.deviceClass),
            manufacturerName = manufacturer,
            productName = product,
            serialNumber = serial,
            hasPermission = hasPerm,
            interfaces = interfaces,
            rawDevice = device
        )
    }

    private fun getDeviceFromIntent(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun log(msg: String) {
        val entry = "[${System.currentTimeMillis() % 100000}] $msg"
        _statusLogs.update { (listOf(entry) + it).take(50) }
    }
}
