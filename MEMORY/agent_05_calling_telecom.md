# Subagent 05: Calling, Telecom & Call Screening Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android calling, Telecom framework, Call Screening, Call Log, and VoIP connection service APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/calling/`
  - `CallTriggerHelper.kt` (`ACTION_DIAL`, `ACTION_CALL`, `TelecomManager.placeCall()`)
  - `CallStateMonitor.kt` (`TelephonyCallback.CallStateListener`, call states IDLE, RINGING, OFFHOOK)
  - `AppCallScreeningService.kt` (`CallScreeningService` implementation for incoming call spam protection)
  - `AppConnectionService.kt` (`ConnectionService` implementation for self-managed VoIP calls)
  - `CallLogRepository.kt` (`CallLog.Calls` query, filter, search incoming/outgoing/missed/rejected)
  - `TelecomManagerHelper.kt` (`TelecomManager`, `PhoneAccount`, `PhoneAccountHandle` registration)
  - `CallRecordingAuditor.kt` (Cellular call recording policy check and limitation analysis)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/calling/`
  - `CallingScreen.kt` (Parent dashboard)
  - `PhoneDialerView.kt` (Interactive dialpad with direct call & dial intents)
  - `CallLogView.kt` (Filterable call history with caller name, date, duration, call type)
  - `CallScreeningConfigView.kt` (Configurable spam rules: block, silence, allow, skip call log)
  - `VoipCallSimulatorView.kt` (Self-managed call interface with active call controls: hold, mute, speaker, DTMF)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/calling/`
  - `CallLogQueryTest.kt`
  - `CallScreeningRuleTest.kt`

## Detailed Implementation Checklist

### 1. Outgoing Calling Triggers
- [ ] `Intent.ACTION_DIAL` (opens dialer without immediate calling, safe, requires no dangerous permission).
- [ ] `Intent.ACTION_CALL` (initiates immediate direct call, requires `CALL_PHONE` permission).
- [ ] `TelecomManager.placeCall()` with `Bundle` extras (`EXTRA_PHONE_ACCOUNT_HANDLE`).

### 2. Call State Monitoring
- [ ] Implement `TelephonyCallback.CallStateListener` (Android 12+) and fallback `PhoneStateListener`.
- [ ] Detect states: `CALL_STATE_IDLE`, `CALL_STATE_RINGING`, `CALL_STATE_OFFHOOK`.
- [ ] Extract incoming caller number where authorized (`READ_CALL_LOG` / `READ_PHONE_STATE`).

### 3. Call Screening Service (`CallScreeningService`)
- [ ] Service manifest registration with permission `android.permission.BIND_SCREENING_SERVICE`.
- [ ] Override `onScreenCall(callDetails: Call.Details)`.
- [ ] Inspect caller details: handle/phone number, contact display name, call direction.
- [ ] Build `CallResponse`:
  - `setDisallowCall(true/false)`.
  - `setRejectCall(true/false)`.
  - `setSilenceCall(true/false)`.
  - `setSkipCallLog(true/false)`.
  - `setSkipNotification(true/false)`.
- [ ] Respond via `respondToCall(callDetails, response)`.

### 4. Telecom Framework & Self-Managed VoIP (`ConnectionService`)
- [ ] Create and register `PhoneAccount` via `TelecomManager.registerPhoneAccount()`.
- [ ] Implement `ConnectionService`:
  - `onCreateOutgoingConnection()`.
  - `onCreateIncomingConnection()`.
- [ ] Implement `Connection`:
  - State handling (INITIALIZING, RINGING, DIALING, ACTIVE, ON_HOLD, DISCONNECTED).
  - Actions: `onAnswer()`, `onReject()`, `onHold()`, `onUnhold()`, `onDisconnect()`, `onPlayDtmfTone()`.
  - Audio routes: `onCallAudioStateChanged()` (EARPIECE, BLUETOOTH, SPEAKER, WIRED_HEADSET).

### 5. Call Log & History (`CallLog.Calls`)
- [ ] Query ContentResolver on `CallLog.Calls.CONTENT_URI`.
- [ ] Read columns: `NUMBER`, `CACHED_NAME`, `DATE`, `DURATION`, `TYPE` (`INCOMING_TYPE`, `OUTGOING_TYPE`, `MISSED_TYPE`, `VOICEMAIL_TYPE`, `REJECTED_TYPE`, `BLOCKED_TYPE`).
- [ ] Search by number/name, filter by call type and date ranges.

### 6. Cellular Call Recording Policy Audit
- [ ] Audit and document Android's strict privacy barriers:
  - Third-party apps cannot record cellular two-way audio using standard public APIs.
  - Privileged permission `CAPTURE_AUDIO_OUTPUT` required for system-level call recording.
  - Document legitimate VoIP in-app audio recording paths.

### 7. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Phone Dialer (Material 3 dialpad with DTMF audio feedback and number input).
  2. Call History (List of calls with icons for missed/incoming/outgoing, duration, timestamp).
  3. Call Screening Shield (Toggles for auto-blocking spam, silencing unknown callers, screening activity log).
  4. Self-Managed VoIP Demo (Simulate an incoming/outgoing VoIP call with full Telecom connection controls).
- Manage runtime permissions (`CALL_PHONE`, `READ_CALL_LOG`, `MANAGE_OWN_CALLS`).

### 8. Tests
- Tests for spam rule evaluation, call duration formatting, and call log query projection parsing.
