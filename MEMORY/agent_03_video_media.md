# Subagent 03: Video Capture & Media Pipeline Specialist TODO & Implementation Specification

## Scope & Objective
Implement all native Android video recording pathways, media encoding/decoding, screen capture, and media pipeline inspection using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/media/`
  - `CameraVideoRecorder.kt` (CameraX VideoCapture with QualitySelector & Audio)
  - `Camera2MediaRecorder.kt` (Camera2 + MediaRecorder direct video pipeline)
  - `ScreenRecorderManager.kt` (`MediaProjection` + `VirtualDisplay` + `MediaRecorder`)
  - `MediaCodecInspector.kt` (`MediaCodecList` encoder/decoder capability query)
  - `MediaPipelineProcessor.kt` (`MediaExtractor`, `MediaMuxer`, `MediaMetadataRetriever`)
  - `MediaPlayerController.kt` (`MediaPlayer` playback controller)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/media/`
  - `MediaScreen.kt` (Parent dashboard)
  - `VideoCaptureView.kt` (Video camera recording UI with audio/mute, quality selector, timer)
  - `ScreenCaptureView.kt` (Screen record launcher with resolution & bitrate settings)
  - `CodecCapabilityBrowserView.kt` (List all hardware/software codecs, profiles, color formats)
  - `MediaInspectorView.kt` (Inspect video files, extract frames, display bitrate/duration/track metadata)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/media/`
  - `CodecCapabilityTest.kt`
  - `MediaMetadataTest.kt`

## Detailed Implementation Checklist

### 1. Video Recording Pathways
- [ ] CameraX `VideoCapture`:
  - `Recorder.Builder` with `QualitySelector` (`SD`, `HD`, `FHD`, `UHD`).
  - Output to internal storage file or MediaStore `MediaStore.Video`.
  - Audio recording toggle (requires `RECORD_AUDIO`).
  - `Recording` controls: start, pause, resume, stop.
  - Recording event listener (`VideoRecordEvent.Start`, `Status`, `Pause`, `Resume`, `Finalize`).
- [ ] Camera2 + `MediaRecorder`:
  - Configure video surface and recorder surface simultaneously.
  - Resolution, frame rate (30fps/60fps), video bitrate, encoder (`H264`, `HEVC`, `VP8`).

### 2. Screen Recording with MediaProjection
- [ ] `MediaProjectionManager.createScreenCaptureIntent()`.
- [ ] User approval handling via Activity result.
- [ ] Setup `VirtualDisplay` matching device display density and size.
- [ ] Pipe surface to `MediaRecorder` or `MediaCodec`.
- [ ] Screen capture stop and cleanup lifecycle.

### 3. MediaCodec & Codec Inspection
- [ ] Query `MediaCodecList(REGULAR_CODECS)` and `ALL_CODECS`.
- [ ] For each codec, inspect:
  - Is encoder vs decoder.
  - Is hardware accelerated vs software.
  - Supported MIME types (video/avc, video/hevc, video/x-vnd.on2.vp9, video/av01, audio/mp4a-latm, etc.).
  - `CodecCapabilities`: supported profiles & levels, max instances, color formats (`COLOR_FormatYUV420Flexible`, etc.).
  - Video capabilities: max resolution, max bitrate, supported frame rate ranges.

### 4. Media Pipeline & Processing APIs
- [ ] `MediaMetadataRetriever`:
  - Extract duration, resolution, rotation, bitrate, MIME type, date, location.
  - Extract thumbnail frame at specified microseconds (`getFrameAtTime`).
- [ ] `MediaExtractor`:
  - Query track count, format per track, read sample data and timestamps.
- [ ] `MediaMuxer`:
  - Multiplex separate audio and video tracks into MP4 container format.
- [ ] `MediaPlayer`:
  - Prepare, play, seek, pause, loop, surface view binding.

### 5. UI Requirements (Jetpack Compose)
- Multi-tabbed layout:
  1. Video Camera Studio (Live preview, recording controls, quality badge, audio indicator).
  2. Screen Recorder (Start/stop screen recording with foreground service indicator).
  3. Device Codec Explorer (Searchable list of hardware & software encoders/decoders with technical specs).
  4. Media File Inspector (Select recorded video, view metadata table, and preview extracted frame thumbnails).
- Comprehensive permission handling (`CAMERA`, `RECORD_AUDIO`, `FOREGROUND_SERVICE_MEDIA_PROJECTION`).

### 6. Tests
- Tests validating codec filter logic, metadata extraction fallbacks, and video recording state machines.
