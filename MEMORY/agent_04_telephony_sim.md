# Subagent 04: Telephony, SIM & Operator Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android telephony, multi-SIM, network operator inspection, signal strength monitoring, and carrier configuration APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/telephony/`
  - `SubscriptionInfoManager.kt` (`SubscriptionManager` - active SIMs, multi-SIM detection, carrier details)
  - `CellularNetworkManager.kt` (`TelephonyManager`, `TelephonyCallback` - signal strength, network type 5G/4G/3G/2G)
  - `PhoneNumberHelper.kt` (`PhoneNumberUtils`, formatting, E.164, country detector)
  - `CellInfoScanner.kt` (`CellInfo` query - LTE, NR/5G, WCDMA, GSM cell tower details)
  - `CarrierConfigHelper.kt` (`CarrierConfigManager` inspection)
  - `RestrictedTelephonyAuditor.kt` (Safe probing & limitation documentation for IMEI, IMSI, ICCID, eSIM EID)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/telephony/`
  - `TelephonyScreen.kt` (Parent dashboard)
  - `SimCardsView.kt` (Multi-SIM cards display with carrier name, slot, MCC/MNC, roaming status)
  - `CellularSignalView.kt` (Real-time signal dBm gauge, LTE/5G network state, cell tower details)
  - `PhoneNumberView.kt` (Phone number formatter, normalizer, and country code detector)
  - `TelephonySecurityView.kt` (Audit report of restricted telephony identifiers & platform policies)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/telephony/`
  - `PhoneNumberTest.kt` (Validation, formatting, and international normalization tests)
  - `SubscriptionDataTest.kt` (Data model parsing and multi-SIM state mapping)

## Detailed Implementation Checklist

### 1. SIM & Subscription Information
- [ ] `SubscriptionManager`:
  - `getActiveSubscriptionInfoList()`.
  - Multi-SIM / Dual-SIM presence and slot indexing (`simSlotIndex`).
  - Active subscription count.
  - Per SIM: Subscription ID, Display Name, Carrier Name, MCC (Mobile Country Code), MNC (Mobile Network Code), Country ISO, Roaming status.
  - Default SIM detection: `getDefaultSmsSubscriptionId()`, `getDefaultVoiceSubscriptionId()`, `getDefaultDataSubscriptionId()`.
  - Subscription change listener (`SubscriptionManager.OnSubscriptionsChangedListener`).

### 2. Cellular Network & Signal Strength
- [ ] `TelephonyManager` & `TelephonyCallback`:
  - Signal strength listener: `SignalStrength`, ASU level, dBm level, signal quality.
  - Network type detection: 2G (GPRS/EDGE), 3G (UMTS/HSPA), 4G (LTE), 5G (NR NSA / SA).
  - Data connection state: `DATA_CONNECTED`, `DATA_CONNECTING`, `DATA_DISCONNECTED`, `DATA_SUSPENDED`.
  - Service state: `STATE_IN_SERVICE`, `STATE_OUT_OF_SERVICE`, `STATE_EMERGENCY_ONLY`, `STATE_POWER_OFF`.
  - Network operator name and numeric ID.

### 3. Cell Tower Information (`getAllCellInfo`)
- [ ] Safe query of `CellInfo` with permission check (`ACCESS_FINE_LOCATION`).
- [ ] Parse cell types:
  - `CellInfoLte` (PCI, TAC, CI, RSRP, RSRQ, RSSNR, CQI).
  - `CellInfoNr` (5G NR: NCI, PCI, TAC, SS-RSRP, SS-RSRQ, SS-SINR).
  - `CellInfoWcdma` / `CellInfoGsm` (legacy parameters).

### 4. Phone Number & Formatting Utilities
- [ ] Retrieve device line number if exposed via `SubscriptionManager.getPhoneNumber(subId)` or `TelephonyManager.getLine1Number()`.
- [ ] Handle null / carrier-restricted phone number states gracefully.
- [ ] `PhoneNumberUtils`:
  - International formatting (E.164, RFC3966, International, National).
  - Number normalization (`normalizeNumber()`).
  - Country code detection from network or SIM ISO.
  - Compare numbers (`compare()`).

### 5. Restricted Identifiers & eSIM Audit
- [ ] Document and implement safe capability check for:
  - IMEI / MEID (`TelephonyManager.getImei()`) - check Android 10+ restriction (`READ_PRIVILEGED_PHONE_STATE`).
  - IMSI (`getSubscriberId()`) - restricted to carrier apps.
  - ICCID (`getSimSerialNumber()`) - restricted.
  - eSIM `EuiccManager`: check if device supports eSIM (`euiccManager.isEnabled`), query EID if available.
- [ ] Provide clear report explaining Android privacy restrictions and carrier privilege requirements.

### 6. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. SIM Cards & Subscriptions (Card per active SIM slot with carrier branding badges).
  2. Cellular Network & Towers (Real-time signal level bar, technology badge [5G/LTE], cell tower list).
  3. Phone Number Utility Tool (Interactive text field with real-time international formatting and country flag/code).
  4. Platform Privacy & Restrictions Report (Checklist showing which telephony fields are accessible vs restricted).
- Manage runtime permissions (`READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `ACCESS_FINE_LOCATION`).

### 7. Tests
- Tests for phone number validation, E.164 conversion, and SIM subscription state parsing.
