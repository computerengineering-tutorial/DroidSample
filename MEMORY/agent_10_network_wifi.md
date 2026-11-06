# Subagent 10: Network, Wi-Fi & Internet Connectivity Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android network monitoring, Wi-Fi inspection, traffic statistics, socket benchmarking, and IP routing APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/network/`
  - `NetworkMonitor.kt` (`ConnectivityManager`, `NetworkCallback`, capabilities, transport types)
  - `WifiInfoManager.kt` (`WifiManager`, `WifiInfo`, SSID, BSSID, RSSI, Wi-Fi 6/7 standards)
  - `TrafficStatsHelper.kt` (`TrafficStats` total and per-UID Rx/Tx bytes and packets)
  - `NetworkInterfaceInspector.kt` (`NetworkInterface` enumeration, IPv4/IPv6, MAC, MTU)
  - `NetworkDiagnosticsHelper.kt` (DNS lookup, TCP socket reachability, ping latency benchmark)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/network/`
  - `NetworkScreen.kt` (Parent dashboard)
  - `NetworkDashboardView.kt` (Active network status, transport badge, link speed, validated internet indicator)
  - `WifiDetailsView.kt` (Wi-Fi frequency 2.4/5/6GHz, channel width, signal dBm meter, standard)
  - `TrafficStatsView.kt` (Real-time speed odometer, cumulative data usage counter)
  - `NetworkInterfacesView.kt` (List all network adapters: wlan0, rmnet0, tun0 with IP addresses)
  - `PingBenchmarkView.kt` (Interactive DNS & latency test with round-trip time graph)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/network/`
  - `TrafficFormatTest.kt` (Data byte formatter tests: KB, MB, GB, TB)
  - `IpAddressTest.kt` (IPv4 and IPv6 subnet/validation tests)

## Detailed Implementation Checklist

### 1. ConnectivityManager & Network Callbacks
- [ ] Register `ConnectivityManager.NetworkCallback` with `NetworkRequest.Builder`:
  - `addCapability(NET_CAPABILITY_INTERNET)`.
  - `addTransportType(TRANSPORT_WIFI)`.
  - `addTransportType(TRANSPORT_CELLULAR)`.
  - `addTransportType(TRANSPORT_ETHERNET)`.
  - `addTransportType(TRANSPORT_VPN)`.
- [ ] Observe lifecycle:
  - `onAvailable(network)`
  - `onLost(network)`
  - `onCapabilitiesChanged(network, capabilities)`
  - `onLinkPropertiesChanged(network, linkProperties)`
- [ ] Inspect `NetworkCapabilities`:
  - `hasCapability(NET_CAPABILITY_VALIDATED)` (actual internet reachability).
  - `hasCapability(NET_CAPABILITY_NOT_METERED)` (unmetered Wi-Fi vs metered cellular).
  - `hasCapability(NET_CAPABILITY_NOT_VPN)`.
  - Downstream bandwidth (`getLinkDownstreamBandwidthKbps()`) and upstream bandwidth.

### 2. Wi-Fi Inspection (`WifiManager` & `WifiInfo`)
- [ ] Check Wi-Fi state (`isWifiEnabled`).
- [ ] Retrieve `WifiInfo` via `NetworkCapabilities.transportInfo` or `wifiManager.connectionInfo`:
  - SSID (handling `<unknown ssid>` when location permission is absent).
  - BSSID / Access Point MAC.
  - RSSI in dBm and signal level (0 - 4 bars via `WifiManager.calculateSignalLevel`).
  - Link speed in Mbps.
  - Frequency band: 2.4 GHz, 5 GHz, 6 GHz (Wi-Fi 6E/7).
  - Wi-Fi Standard: `WIFI_STANDARD_11N` (Wi-Fi 4), `11AC` (Wi-Fi 5), `11AX` (Wi-Fi 6), `11BE` (Wi-Fi 7).

### 3. Data Traffic Statistics (`TrafficStats`)
- [ ] Mobile data:
  - `TrafficStats.getMobileRxBytes()`, `TrafficStats.getMobileTxBytes()`.
  - `TrafficStats.getMobileRxPackets()`, `TrafficStats.getMobileTxPackets()`.
- [ ] Total system data:
  - `TrafficStats.getTotalRxBytes()`, `TrafficStats.getTotalTxBytes()`.
- [ ] App UID data:
  - `TrafficStats.getUidRxBytes(android.os.Process.myUid())`, `TrafficStats.getUidTxBytes(...)`.
- [ ] Real-time bandwidth calculation (diff bytes per second) to feed UI live speed indicator.

### 4. Network Interfaces & IP Configuration
- [ ] Enumerate `NetworkInterface.getNetworkInterfaces()`:
  - Interface name (`wlan0`, `rmnet_data0`, `tun0`, `dummy0`).
  - Display name, MTU (Maximum Transmission Unit).
  - Is Up, Is Loopback, Is Point-to-Point, Supports Multicast.
- [ ] Enumerate `InetAddress` for each interface:
  - IPv4 addresses (e.g. `192.168.1.100`).
  - IPv6 addresses (link-local, global).
  - Loopback detection.

### 5. DNS & Latency Benchmark
- [ ] DNS resolution test: resolve standard hostnames (`dns.google`, `cloudflare.com`) and measure resolution time in milliseconds.
- [ ] Socket connection benchmark: open TCP socket to port 80/443 and measure handshake latency.
- [ ] HTTP connectivity probe to detect captive portals.

### 6. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Connection Overview (Card with active transport badge [Wi-Fi/5G/Offline], IP, gateway, validated internet indicator).
  2. Wi-Fi Inspector (Signal meter bar, frequency band tag, Wi-Fi 6/7 badge, link speed).
  3. Live Traffic Meter (Speedometer dial showing current download/upload kB/s + total session usage).
  4. Network Interfaces (Card per adapter showing hardware name, MTU, IPv4/IPv6 addresses).
  5. Latency & Ping Lab (Interactive button to test DNS and socket ping with response time display).
- Manage runtime permissions (`ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` for SSID).

### 7. Tests
- Tests for data unit formatting (bytes to MB/GB), IP subnet calculator, and bandwidth rate estimators.
