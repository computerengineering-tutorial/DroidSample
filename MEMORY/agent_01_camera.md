# Subagent 01: Camera Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android camera access methods and testing infrastructure natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/camera/`
  - `Camera1Manager.kt` (Legacy `android.hardware.Camera`)
  - `Camera2Manager.kt` (Low-level `android.hardware.camera2` - characteristics, sessions, controls)
  - `CameraXManager.kt` (Jetpack CameraX - Core, Preview, ImageCapture, ImageAnalysis, VideoCapture)
  - `CameraXConcurrentManager.kt` (Dual front+back simultaneous capture)
  - `CameraXExtensionsManager.kt` (OEM HDR, Night, Bokeh, Face Retouch, Auto)
  - `CameraIntentsHelper.kt` (System camera intents & Photo Picker)
  - `CameraNdkBridge.kt` (NDK architecture & JNI interface definitions)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/camera/`
  - `CameraScreen.kt` (Parent dashboard with mode selector)
  - `Camera1View.kt` (SurfaceView/TextureView preview & parameters)
  - `Camera2View.kt` (Camera2 preview with manual AE/AF/AWB/zoom sliders)
  - `CameraXView.kt` (PreviewView with use-case toggles)
  - `CameraMetadataInspector.kt` (Hardware level, capabilities, stream sizes)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/camera/`
  - `CameraMetadataTest.kt`
  - `CameraStateTest.kt`

## Detailed Implementation Checklist

### 1. Camera1 Legacy (`android.hardware.Camera`)
- [ ] Implement `Camera.open()` and `Camera.open(id)`.
- [ ] Query `Camera.getNumberOfCameras()`, `Camera.getCameraInfo()`.
- [ ] Inspect and configure `Camera.Parameters`: supported picture sizes, preview sizes, FPS ranges, focus modes, flash modes, scene modes, white balance, zoom, exposure compensation.
- [ ] Surface preview binding (`setPreviewDisplay` / `setPreviewTexture`).
- [ ] Capture lifecycle: `startPreview()`, `stopPreview()`, `takePicture()`, `autoFocus()`, `release()`.
- [ ] Preview callbacks: `setPreviewCallbackWithBuffer()`.

### 2. Camera2 Low-Level (`android.hardware.camera2`)
- [ ] `CameraManager`: list cameras, observe availability callbacks, query `CameraCharacteristics`.
- [ ] Characteristics inspection: hardware level (`LIMITED`, `FULL`, `LEVEL_3`), sensor orientation, active array, sensitivity range, exposure time range, supported stream configurations.
- [ ] `CameraDevice.StateCallback` lifecycle: onOpened, onDisconnected, onError.
- [ ] `CameraCaptureSession`: repeating request (preview), burst capture, abort, close.
- [ ] Manual controls: `CONTROL_AE_MODE`, `CONTROL_AF_MODE`, `CONTROL_AWB_MODE`, `CONTROL_ZOOM_RATIO`, `SCALER_CROP_REGION`, `FLASH_MODE`, `SENSOR_EXPOSURE_TIME`, `SENSOR_SENSITIVITY`, `LENS_FOCUS_DISTANCE`.
- [ ] Stream outputs: YUV_420_888, JPEG, SurfaceTexture, ImageReader.

### 3. CameraX Full Pipeline (`androidx.camera:*`)
- [ ] Core: `ProcessCameraProvider`, `CameraSelector` (DEFAULT_BACK_CAMERA, DEFAULT_FRONT_CAMERA).
- [ ] Use Cases:
  - `Preview` with `PreviewView` / SurfaceProvider.
  - `ImageCapture` (capture to file, capture to memory buffer, flash modes, rotation).
  - `ImageAnalysis` (YUV/RGBA analyzer, backpressure strategy `STRATEGY_KEEP_ONLY_LATEST`).
  - `VideoCapture` (Recorder, recording pause, resume, stop).
- [ ] CameraControl & CameraInfo: linear zoom, pinch-to-zoom, tap-to-focus/metering, torch toggle, exposure compensation.
- [ ] CameraX Extensions: Query availability of `HDR`, `BOKEH`, `NIGHT`, `FACE_RETOUCH`, `AUTO`. Fallback handling when unsupported.
- [ ] Concurrent Camera: Front + Back simultaneous stream binding where supported by hardware.

### 4. Camera Intents & System Integration
- [ ] `MediaStore.ACTION_IMAGE_CAPTURE` and `ACTION_VIDEO_CAPTURE` with `FileProvider` URI.
- [ ] Photo Picker integration (`ActivityResultContracts.PickVisualMedia`).

### 5. UI Requirements (Jetpack Compose)
- Tabbed interface switching between:
  1. CameraX Modern (Preview + capture controls + live analysis stats).
  2. Camera2 Pro (Live preview with manual sliders for ISO, shutter speed, focus distance, EV).
  3. Camera1 Legacy (Compatibility view).
  4. Camera Hardware Diagnostics (Full list of cameras, sensors, resolutions, features).
- Handle runtime permissions (`android.permission.CAMERA`, `android.permission.RECORD_AUDIO`).
- Display graceful "Hardware unsupported" or "Permission denied" states.

### 6. Tests
- Tests verifying parameter translation, metadata extraction, state transitions, and error handling.
