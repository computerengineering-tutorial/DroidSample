# Subagent 06: SMS, MMS & Messaging Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android SMS sending, SMS receiving, SMS database access, SMS Retriever API, and MMS metadata APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/sms/`
  - `SmsSenderHelper.kt` (`SmsManager` single, multipart, Unicode/Hindi, SIM selection, delivery callbacks)
  - `SmsBroadcastReceiver.kt` (`BroadcastReceiver` for `SMS_RECEIVED_ACTION`, PDU decoding)
  - `SmsDatabaseRepository.kt` (Query `Telephony.Sms.Inbox`, `Sent`, `Conversations` via ContentResolver)
  - `SmsRetrieverHelper.kt` (`SmsRetrieverClient` automated verification & Consent API)
  - `MmsMetadataHelper.kt` (WAP Push, MMS notification structure, attachment metadata)
  - `DefaultSmsRoleManager.kt` (`RoleManager.ROLE_SMS` status & request workflow)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/sms/`
  - `SmsScreen.kt` (Parent dashboard)
  - `SmsComposerView.kt` (SMS composer with SIM card picker, character counter, Unicode indicator)
  - `SmsInboxView.kt` (Threaded SMS list with search and conversation detail)
  - `SmsMonitorView.kt` (Live log of incoming SMS broadcasts and decoded PDU headers)
  - `SmsVerificationView.kt` (Simulated automated OTP detection with SMS Retriever & Consent)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/sms/`
  - `SmsEncodingTest.kt` (7-bit GSM vs 16-bit UCS-2 character counter & multipart splitting tests)
  - `OtpParserTest.kt` (Regex extraction for OTP tokens and sender tags)

## Detailed Implementation Checklist

### 1. SMS Sending (`SmsManager`)
- [ ] Get instance for default SIM (`SmsManager.getDefault()`) and specific SIM (`context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)`).
- [ ] Send single text message (`sendTextMessage()`).
- [ ] Send multipart text message (`sendMultipartTextMessage()`) for long messages > 160 characters.
- [ ] Unicode text support (Hindi, emojis, UTF-16).
- [ ] Delivery tracking: `PendingIntent` for `sentIntent` and `deliveryIntent`.
- [ ] Broadcast receivers to track SENT (RESULT_OK, RESULT_ERROR_GENERIC_FAILURE, RESULT_ERROR_NO_SERVICE, RESULT_ERROR_RADIO_OFF) and DELIVERED status.

### 2. SMS Receiving & PDU Decoding
- [ ] Register `BroadcastReceiver` in manifest for `android.provider.Telephony.SMS_RECEIVED`.
- [ ] Extract PDUs from `intent.extras.get("pdus")`.
- [ ] Decode using `SmsMessage.createFromPdu(pdu, format)`.
- [ ] Extract sender address (`originatingAddress`), timestamp (`timestampMillis`), message body (`messageBody`), subscription ID.
- [ ] Assemble multi-part SMS messages.
- [ ] Emit received message to live reactive UI flow.

### 3. SMS Database & Content Provider
- [ ] Query `Telephony.Sms.CONTENT_URI` / `Telephony.Sms.Inbox.CONTENT_URI`.
- [ ] Columns: `_ID`, `ADDRESS`, `BODY`, `DATE`, `READ`, `TYPE`, `THREAD_ID`, `STATUS`.
- [ ] Group messages by `THREAD_ID` to display conversations.
- [ ] Filter by sender and date.
- [ ] Differentiate between read-only access (with `READ_SMS`) and write access (requiring Default SMS App role).

### 4. SMS Retriever & User Consent APIs
- [ ] Implement `SmsRetrieverClient` start workflow (`startSmsRetriever()`).
- [ ] Broadcast receiver for `SmsRetriever.SMS_RETRIEVED_ACTION`.
- [ ] Extract 4-8 digit OTP tokens from message text via regex patterns.
- [ ] User Consent API (`startSmsUserConsent()`) for receiving SMS without app-specific hash.

### 5. MMS & WAP Push Metadata
- [ ] Handle `WAP_PUSH_RECEIVED_ACTION` broadcast.
- [ ] Parse MMS notification PDU headers (MMS version, sender, expiry, content location URL).
- [ ] Document MMS carrier APN / MMSC transmission flow and carrier limits.

### 6. Default SMS App Role
- [ ] Use `RoleManager` (Android 10+) to check `roleManager.isRoleHeld(RoleManager.ROLE_SMS)`.
- [ ] Request role via `roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)`.
- [ ] Document privileges unlocked by holding Default SMS app status (write SMS DB, handle MMS directly).

### 7. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. SMS Composer (Recipient input, message box with GSM 7-bit vs UCS-2 character counter, SIM selector, Send button).
  2. SMS Inbox & Conversations (Clean list of message threads with time stamps and unread badges).
  3. Live SMS Receiver Monitor (Real-time card showing raw broadcast details when an SMS arrives).
  4. OTP & Verification Lab (Auto-listening OTP code input box with auto-fill animation).
- Manage runtime permissions (`SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`).

### 8. Tests
- Tests for SMS segment calculation (160 GSM chars vs 70 UCS-2 chars per segment), OTP regex matching, and PDU metadata extraction.
