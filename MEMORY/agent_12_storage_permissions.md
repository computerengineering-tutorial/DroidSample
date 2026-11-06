# Subagent 12: Storage, MediaStore, Device Info, Security & Testing Framework Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android storage access, MediaStore, Photo Picker, Storage Access Framework (SAF), Device Hardware Information, Security/Permissions/Role management, and the Master API Testing Framework runner using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/storage/`
  - `StorageHelper.kt` (Internal/External files, Scoped Storage rules, cache inspection)
  - `MediaStoreManager.kt` (`MediaStore.Images`, `Video`, `Audio`, insert, query, update, delete)
  - `SafHelper.kt` (Storage Access Framework: `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`)
  - `AppFileProviderHelper.kt` (`FileProvider.getUriForFile()`, secure file sharing)
  - `DeviceInfoProvider.kt` (`Build`, CPU ABIs, RAM `ActivityManager.MemoryInfo`, Storage `StatFs`, Display metrics)
  - `PermissionRoleManager.kt` (Runtime permissions, Special App Access, `RoleManager`, `AppOpsManager`, Biometric authentication)
  - `TestingFramework.kt` (Standardized `FeatureTestResult` model, category runner, JSON/Markdown exporter)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/storage/`
  - `StorageSecurityScreen.kt` (Parent dashboard)
  - `StorageExplorerView.kt` (Internal/external file viewer, SAF file picker, Photo Picker launcher)
  - `MediaStoreBrowserView.kt` (Gallery view of images/videos loaded from MediaStore)
  - `DeviceInfoView.kt` (Comprehensive hardware sheet: SoC, RAM, storage, display resolution, refresh rate)
  - `PermissionsRoleView.kt` (Centralized permission checklist, role manager requester, Biometric prompt button)
  - `ApiTestRunnerView.kt` (Master Test Suite Runner - runs automated tests for all 18 categories, displays pass/fail status and exports report)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/storage/`
  - `TestResultModelTest.kt` (Serialization and report generation tests)
  - `DeviceInfoTest.kt` (Hardware spec formatting tests)

## Detailed Implementation Checklist

### 1. Storage & Scoped Storage
- [ ] Internal storage: `context.filesDir`, `context.cacheDir`.
- [ ] External app-specific storage: `context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)`.
- [ ] Calculate directory sizes and file counts.
- [ ] Clean cache directory utility.
- [ ] Scoped storage rules compliance (no direct unrestricted file system access).

### 2. MediaStore Access
- [ ] Query images: `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` (ID, Display Name, Size, MIME type, Date added).
- [ ] Query videos: `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` (Duration, Resolution).
- [ ] Query audio: `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` (Artist, Album, Duration).
- [ ] Insert new media item via `ContentResolver.insert()` with `IS_PENDING` flag for Android 10+.

### 3. Storage Access Framework (SAF) & Photo Picker
- [ ] Open document: `Intent.ACTION_OPEN_DOCUMENT` with MIME filters (`image/*`, `*/*`).
- [ ] Create document: `Intent.ACTION_CREATE_DOCUMENT` with file name and MIME type.
- [ ] Open directory tree: `Intent.ACTION_OPEN_DOCUMENT_TREE` and persist permissions (`takePersistableUriPermission`).
- [ ] Android 13+ Photo Picker (`ActivityResultContracts.PickVisualMedia`, `PickMultipleVisualMedia`).

### 4. Device Information & Hardware Sheet
- [ ] `Build` info: Manufacturer, Model, Brand, Product, Board, Hardware, Fingerprint, Android Release, SDK Level (`Build.VERSION.SDK_INT`).
- [ ] CPU & Architecture: `Build.SUPPORTED_ABIS`, core count via `Runtime.getRuntime().availableProcessors()`.
- [ ] Memory (RAM): `ActivityManager.MemoryInfo` (totalMem, availMem, threshold, lowMemory).
- [ ] Storage capacity: `StatFs` on data directory (total bytes, available bytes, free percentage).
- [ ] Display specs: Screen resolution in px, density DPI, refresh rate in Hz, HDR capabilities (`Display.getHdrCapabilities()`).

### 5. Security, Permissions & Roles
- [ ] Runtime Permissions matrix: status of Camera, Mic, Location, Contacts, Call Log, SMS, Storage, Notifications.
- [ ] Request permissions launcher with rationale handling.
- [ ] Special App Access:
  - Check `Environment.isExternalStorageManager()` (All Files Access).
  - Check `Settings.canDrawOverlays()` (System Alert Window).
- [ ] RoleManager:
  - Check & request `ROLE_SMS`, `ROLE_DIALER`, `ROLE_CALL_SCREENING`.
- [ ] Biometric Authentication:
  - `BiometricManager.canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK)`.
  - Launch `BiometricPrompt` with authentication callback.

### 6. Master API Testing Framework (Part 16 of NOTE.md)
- [ ] Data class `FeatureTestResult`:
  - `featureId: String`
  - `apiName: String`
  - `category: String` (Camera, Audio, Video, Telephony, Calling, SMS, Location, Sensors, Connectivity, Network, System, Storage)
  - `minSdk: Int`
  - `maxSdk: Int?`
  - `permissionRequired: String?`
  - `roleRequired: String?`
  - `deviceSupported: Boolean`
  - `status: TestStatus` (PASS, FAIL, BLOCKED, UNSUPPORTED)
  - `outputData: String`
  - `errorMessage: String?`
  - `timestamp: Long`
- [ ] Test Suite Runner executing diagnostics for each subsystem.
- [ ] Markdown & JSON export generation of the full capability report.

### 7. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. File & Storage Explorer (Internal files, cache cleaner, SAF picker buttons).
  2. MediaStore Gallery (Grid view of photos and audio files retrieved from MediaStore).
  3. Device Hardware Sheet (Polished system spec card with RAM, CPU, storage, and display indicators).
  4. Permissions & Roles Center (Status switch for each dangerous permission, Role request buttons, Biometric test button).
  5. Master API Test Runner (Run All Tests button, progress indicator, category accordion with pass/fail badges, Export Report button).

### 8. Tests
- Tests for `FeatureTestResult` JSON/Markdown serialization and storage capacity calculators.
