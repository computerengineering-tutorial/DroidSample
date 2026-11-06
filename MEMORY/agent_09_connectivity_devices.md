# Subagent 09: Bluetooth, USB & Device Connectivity Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android Bluetooth Classic, BLE Scanning/GATT Client/Server, USB Host/Accessory, and Companion Device pairing APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/connectivity/`
  - `BluetoothClassicManager.kt` (`BluetoothAdapter`, discovery, paired devices, bonding receiver)
  - `BleScannerManager.kt` (`BluetoothLeScanner`, `ScanFilter`, `ScanSettings`, parse advertising record)
  - `BleGattClientManager.kt` (`BluetoothGatt`, connect, discover services, read/write/notify characteristics)
  - `BleGattServerManager.kt` (`BluetoothGattServer`, `BluetoothLeAdvertiser`, GATT service hosting)
  - `UsbHostManager.kt` (`UsbManager`, USB device enumeration, permissions, endpoints, bulk transfer)
  - `CompanionDeviceHelper.kt` (`CompanionDeviceManager` association workflow for wearables)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/connectivity/`
  - `ConnectivityScreen.kt` (Parent dashboard)
  - `BleScannerView.kt` (Real-time BLE peripheral scanner with RSSI signal meter and manufacturer data)
  - `GattExplorerView.kt` (Tree view of discovered GATT Services, Characteristics, and Descriptors)
  - `BluetoothClassicView.kt` (List paired devices, start discovery, trigger pairing)
  - `UsbDevicesView.kt` (List connected USB hardware, vendor/product ID, interfaces, endpoints)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/connectivity/`
  - `BleScanRecordTest.kt` (Advertising PDU parser for Service UUIDs and manufacturer data)
  - `GattUuidTest.kt` (Standard Bluetooth SIG 16-bit to 128-bit UUID expansion tests)

## Detailed Implementation Checklist

### 1. Bluetooth Classic Management
- [ ] Check Bluetooth hardware presence and adapter state (`bluetoothAdapter.isEnabled`).
- [ ] Query bonded/paired devices (`bluetoothAdapter.bondedDevices`).
- [ ] Device discovery: `startDiscovery()`, `cancelDiscovery()`.
- [ ] Broadcast receiver for `BluetoothDevice.ACTION_FOUND`, `ACTION_BOND_STATE_CHANGED`, `BluetoothAdapter.ACTION_STATE_CHANGED`.
- [ ] Inspect `BluetoothDevice`: name, MAC address, bond state (`BOND_NONE`, `BOND_BONDING`, `BOND_BONDED`), device type (`DEVICE_TYPE_CLASSIC`, `DEVICE_TYPE_LE`, `DEVICE_TYPE_DUAL`).

### 2. BLE Scanning (`BluetoothLeScanner`)
- [ ] Configure `ScanSettings`:
  - `SCAN_MODE_LOW_LATENCY`, `SCAN_MODE_BALANCED`, `SCAN_MODE_LOW_POWER`.
  - Callback type: `CALLBACK_TYPE_ALL_MATCHES`.
- [ ] Configure `ScanFilter`: by Service UUID, Device Name, Device Address, Manufacturer ID.
- [ ] `ScanCallback` implementation:
  - `onScanResult(callbackType, result)`.
  - Extract RSSI in dBm, timestamp.
  - Parse `ScanRecord`: advertised service UUIDs, TX power level, manufacturer specific data.

### 3. BLE GATT Client (`BluetoothGatt`)
- [ ] Connect to peripheral: `device.connectGatt(context, false, gattCallback, TRANSPORT_LE)`.
- [ ] Handle `BluetoothGattCallback`:
  - `onConnectionStateChange()` (`STATE_CONNECTED`, `STATE_DISCONNECTED`).
  - `onServicesDiscovered()`: enumerate all `BluetoothGattService` and `BluetoothGattCharacteristic`.
  - `onCharacteristicRead()`, `onCharacteristicWrite()`, `onCharacteristicChanged()` (notifications/indications).
- [ ] Enable characteristic notifications via `setCharacteristicNotification()` and Client Characteristic Configuration Descriptor (CCCD `0x2902`).

### 4. BLE GATT Server & Advertising
- [ ] Check advertising support: `bluetoothAdapter.isMultipleAdvertisementSupported()`.
- [ ] `BluetoothLeAdvertiser`:
  - `AdvertiseSettings` (mode, tx power level, connectable).
  - `AdvertiseData` (include device name, service UUIDs).
  - `AdvertiseCallback` (onStartSuccess, onStartFailure).
- [ ] `BluetoothGattServer`:
  - Open server via `bluetoothManager.openGattServer()`.
  - Add custom service with readable/writable characteristics.

### 5. USB Host & Accessory Management
- [ ] `UsbManager.getDeviceList()`: map of connected USB devices.
- [ ] For each `UsbDevice`:
  - Vendor ID (VID), Product ID (PID), Device Name, Manufacturer Name, Serial Number.
  - Device Class, Subclass, Protocol.
  - Interface count, endpoint count (Bulk IN/OUT, Interrupt IN/OUT).
- [ ] Check / request USB permission via `UsbManager.requestPermission()`.
- [ ] BroadcastReceiver for `ACTION_USB_DEVICE_ATTACHED` and `ACTION_USB_DEVICE_DETACHED`.

### 6. Companion Device Manager
- [ ] `CompanionDeviceManager` association request for fitness bands, smartwatches, headsets.
- [ ] Build `AssociationRequest` with `BluetoothDeviceFilter` or `BluetoothLeDeviceFilter`.
- [ ] Launch association picker dialog.

### 7. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. BLE Scanner (Search bar, real-time list sorted by RSSI, connect button, signal meter).
  2. GATT Tree Explorer (Collapsible tree of services, characteristics with Read/Write/Notify actions).
  3. Bluetooth Classic Hub (Paired devices card list, discovery progress bar, device details dialog).
  4. USB Hardware Inspector (List attached USB peripherals with complete endpoint technical specs).
- Manage runtime permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`, `ACCESS_FINE_LOCATION`).

### 8. Tests
- Tests for BLE advertising packet parsing, standard SIG UUID resolution, and GATT characteristic property flag decoding.
