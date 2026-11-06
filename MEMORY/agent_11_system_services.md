# Subagent 11: System Events, Notifications & Foreground Services Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android notification styles/channels, system broadcast event monitoring, Android 14+ typed foreground services, and background scheduling APIs natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/system/`
  - `NotificationHelper.kt` (`NotificationManager`, channels, importance, BigTextStyle, BigPictureStyle, actions)
  - `SystemBroadcastReceiver.kt` (Dynamic & manifest receivers for battery, power, screen, boot, time)
  - `AppForegroundService.kt` (Android 14+ typed foreground services: camera, microphone, location, dataSync)
  - `WorkSchedulerHelper.kt` (`WorkManager` / `JobScheduler` background execution with constraints)
  - `AlarmManagerHelper.kt` (`AlarmManager` exact vs inexact, `setExactAndAllowWhileIdle`, alarms permission)
  - `PowerBatteryManager.kt` (`BatteryManager`, charging source, temperature, health, voltage)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/system/`
  - `SystemScreen.kt` (Parent dashboard)
  - `NotificationLabView.kt` (Interactive notification generator with custom channels, styles, and action buttons)
  - `SystemEventLogView.kt` (Live stream of detected system broadcasts with timestamp and extras)
  - `BatteryHealthView.kt` (Battery level gauge, temperature in °C, voltage in mV, health status, charging status)
  - `ForegroundServiceView.kt` (Start/stop controls for foreground service types with persistent notification badge)
  - `BackgroundSchedulerView.kt` (Schedule one-time/periodic background jobs with constraints)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/system/`
  - `BatteryDataTest.kt` (Battery intent extras decoder tests)
  - `NotificationChannelTest.kt` (Channel importance and ID validation tests)

## Detailed Implementation Checklist

### 1. Advanced Notification Management
- [ ] Create and register `NotificationChannel` with importance levels (`IMPORTANCE_HIGH`, `DEFAULT`, `LOW`, `MIN`).
- [ ] Create `NotificationChannelGroup`.
- [ ] Notification styles:
  - Standard Notification with title, content, small icon, large icon.
  - `BigTextStyle` for long expandible text.
  - `BigPictureStyle` for large image preview.
  - `InboxStyle` for multi-line summary.
  - Messaging style simulator.
- [ ] Action buttons with `PendingIntent` and broadcast receivers.
- [ ] Notification grouping & summary notifications (`setGroup()`, `setGroupSummary()`).
- [ ] Update notification dynamically (progress bar updates 0% to 100%).
- [ ] Cancel individual and all notifications.

### 2. System Event Broadcast Receivers
- [ ] Battery & Power (`Intent.ACTION_BATTERY_CHANGED`, `ACTION_POWER_CONNECTED`, `ACTION_POWER_DISCONNECTED`):
  - Battery level & scale (calculate percentage).
  - Plugged source (`BATTERY_PLUGGED_AC`, `USB`, `WIRELESS`).
  - Battery health (`HEALTH_GOOD`, `OVERHEAT`, `DEAD`, `OVER_VOLTAGE`).
  - Battery temperature (tenths of a degree Celsius).
  - Battery voltage (millivolts).
- [ ] Screen & State:
  - `Intent.ACTION_SCREEN_ON`, `ACTION_SCREEN_OFF`, `ACTION_USER_PRESENT`.
- [ ] Time & Date:
  - `Intent.ACTION_TIME_TICK`, `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`.
- [ ] System Configuration & Packages:
  - `Intent.ACTION_AIRPLANE_MODE_CHANGED`.
  - `Intent.ACTION_LOCALE_CHANGED`.
  - `Intent.ACTION_CONFIGURATION_CHANGED`.
  - `Intent.ACTION_PACKAGE_ADDED`, `ACTION_PACKAGE_REMOVED`.

### 3. Typed Foreground Services (Android 14+ / API 34 compliant)
- [ ] Implement `AppForegroundService` inheriting from `Service`.
- [ ] Manifest registration with `android:foregroundServiceType`:
  - `camera`
  - `microphone`
  - `location`
  - `dataSync`
  - `mediaPlayback`
- [ ] Start foreground service with persistent notification (`startForeground(id, notification, type)`).
- [ ] Graceful stop and lifecycle management (`stopForeground()`, `stopSelf()`).

### 4. Background Scheduling (WorkManager & JobScheduler)
- [ ] Background Worker implementation using `WorkManager` / `CoroutineWorker`.
- [ ] Constraints:
  - Required network type (`CONNECTED`, `UNMETERED`).
  - Requires charging (`setRequiresCharging(true)`).
  - Requires device idle (`setRequiresDeviceIdle(true)`).
  - Requires battery not low.
- [ ] One-time work request (`OneTimeWorkRequestBuilder`).
- [ ] Periodic work request (`PeriodicWorkRequestBuilder` with 15 min minimum interval).

### 5. AlarmManager (Exact & Inexact Alarms)
- [ ] Inexact alarms (`set()` / `setInexactRepeating()`).
- [ ] Exact alarms (`setExactAndAllowWhileIdle()`):
  - Check permission `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` on Android 12+ (`canScheduleExactAlarms()`).
  - PendingIntent destination handling.

### 6. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Notification Lab (Send test notifications with different styles, channels, and progress sliders).
  2. Live System Event Log (Chronological list of broadcasts received with expandable payload).
  3. Battery & Power Station (Animated battery gauge, voltage/temperature indicators, charging status).
  4. Foreground Service Control (Toggles to start/stop camera/location/dataSync foreground services).
  5. Work Scheduler (Configure constraints and enqueue background jobs with live status indicator).
- Manage runtime permissions (`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `SCHEDULE_EXACT_ALARM`).

### 7. Tests
- Tests for battery percentage calculation, notification channel attribute builders, and work constraint builders.
