# ANDROID COMPLETE DEVICE CAPABILITY LAB

## Master API Inventory, Implementation & Testing Specification

**Document version:** 1.0  
**Research date:** 6 November 2026  
**Platform:** Android  
**Recommended language:** Kotlin + Java interoperability  
**Architecture:** Modular Android application  
**Primary purpose:** Hardware, multimedia, telephony, SIM, SMS, calling, location and system API research.

---

# PART 1 — CAMERA: COMPLETE ACCESS METHODS

यह सबसे महत्वपूर्ण sections में से एक है। Camera के लिए सिर्फ CameraX implement करना पर्याप्त नहीं है। अलग-अलग APIs को अलग modules में implement करना चाहिए।

## 1.1 Camera Access Methods

| No. | API / Method | Status | Purpose |
| --- | --- | --- | --- |
| 1 | `android.hardware.Camera` | Deprecated | Legacy Camera1 access |
| 2 | `android.hardware.camera2` | Modern | Low-level camera control |
| 3 | CameraX Core | Modern | Camera hardware abstraction |
| 4 | CameraX Camera2 Interop | Modern | CameraX में Camera2 controls |
| 5 | CameraX LifecycleCameraController | Modern | Simplified camera management |
| 6 | CameraX CameraProvider | Modern | Use-case based camera |
| 7 | CameraX Extensions | Modern | OEM HDR, Night, Bokeh |
| 8 | CameraX ConcurrentCamera | Modern | Simultaneous cameras |
| 9 | Camera Intents | System | External camera application |
| 10 | Camera NDK | Advanced | Native camera access |
| 11 | Camera Viewfinder | Jetpack | Camera2 preview integration |
| 12 | External Camera | Device-dependent | USB / external camera |
| 13 | OEM Camera Extensions | Vendor-dependent | Manufacturer capabilities |

Official references:

- <https://developer.android.com/media/camera/choose-camera-library>
- <https://developer.android.com/media/camera/camerax>
- <https://developer.android.com/reference/android/hardware/camera2/package-summary>
- <https://developer.android.com/ndk/reference/group/camera>

## 1.2 Camera1 — Legacy Camera API

Package:

`android.hardware.Camera`

Implement:

- `Camera.open()`
- `Camera.open(cameraId)`
- `Camera.getNumberOfCameras()`
- `Camera.getCameraInfo()`
- `Camera.Parameters`
- `Camera.setPreviewDisplay()`
- `Camera.setPreviewTexture()`
- `Camera.startPreview()`
- `Camera.stopPreview()`
- `Camera.takePicture()`
- `Camera.autoFocus()`
- `Camera.cancelAutoFocus()`
- `Camera.setDisplayOrientation()`
- `Camera.setParameters()`
- `Camera.getParameters()`
- `Camera.setPreviewCallback()`
- `Camera.setPreviewCallbackWithBuffer()`
- `Camera.setOneShotPreviewCallback()`
- `Camera.unlock()`
- `Camera.lock()`
- `Camera.release()`

Parameters to test:

- Supported picture sizes.
- Supported preview sizes.
- Supported FPS ranges.
- Supported focus modes.
- Supported flash modes.
- Supported scene modes.
- Supported white balance.
- Exposure compensation.
- Zoom ratios.
- JPEG quality.
- Rotation.
- Metering areas.
- Focus areas.
- Video stabilization.

**Important:** Camera1 is deprecated since API 21. इसे legacy compatibility testing के लिए implement करें, primary production camera के लिए नहीं।

## 1.3 Camera2 API — Complete Low-level Access

Package:

`android.hardware.camera2`

### A. Camera Discovery

- `CameraManager`
- `getCameraIdList()`
- `getCameraCharacteristics()`
- `openCamera()`
- `getCameraIdListNoLazy()`, जहाँ उपलब्ध हो
- Camera availability callbacks.
- Physical camera identification.
- Logical camera identification.
- Camera characteristics inspection.

### B. Camera Device

- `CameraDevice`
- `CameraDevice.StateCallback`
- `createCaptureRequest()`
- `createCaptureSession()`
- `close()`
- `createReprocessableCaptureSession()`, जहाँ supported हो
- `createCaptureSessionByOutputConfigurations()`
- `SessionConfiguration`

Test states:

- Camera opened.
- Camera disconnected.
- Camera error.
- Camera closed.
- Camera unavailable.
- Camera reopened.
- Camera access conflict.

### C. Capture Session

- `CameraCaptureSession`
- `setRepeatingRequest()`
- `capture()`
- `captureBurst()`
- `stopRepeating()`
- `abortCaptures()`
- `close()`
- `CaptureCallback`
- `CaptureResult`
- `TotalCaptureResult`
- `CaptureFailure`

### D. Capture Request Controls

Implement every relevant supported control:

- `CONTROL_MODE`
- `CONTROL_AE_MODE`
- `CONTROL_AF_MODE`
- `CONTROL_AWB_MODE`
- `CONTROL_EFFECT_MODE`
- `CONTROL_SCENE_MODE`
- `CONTROL_VIDEO_STABILIZATION_MODE`
- `CONTROL_ZOOM_RATIO`
- `SCALER_CROP_REGION`
- `FLASH_MODE`
- `JPEG_ORIENTATION`
- `JPEG_QUALITY`
- `SENSOR_EXPOSURE_TIME`
- `SENSOR_SENSITIVITY`
- `SENSOR_FRAME_DURATION`
- `LENS_FOCUS_DISTANCE`
- `LENS_APERTURE`
- `LENS_FOCAL_LENGTH`
- `NOISE_REDUCTION_MODE`
- `EDGE_MODE`
- `TONEMAP_MODE`
- `COLOR_CORRECTION_MODE`
- `STATISTICS_FACE_DETECT_MODE`
- `CONTROL_AF_TRIGGER`
- `CONTROL_AE_PRECAPTURE_TRIGGER`

हर control के लिए:

1. Supported values query करें।
2. Current value पढ़ें।
3. Set करने का प्रयास करें।
4. Capture result पढ़ें।
5. Unsupported configuration handle करें।

### E. Camera Metadata

Read:

- Sensor orientation.
- Sensor size.
- Sensor pixel array.
- Active array.
- Focal lengths.
- Apertures.
- Optical stabilization.
- Digital stabilization.
- Flash availability.
- Hardware level.
- Available capabilities.
- Available request keys.
- Available result keys.
- Available stream configurations.
- Maximum output sizes.
- Minimum frame durations.
- Sensor sensitivity range.
- Exposure time range.
- Face detection capabilities.
- Timestamp source.
- Hardware sync type.

### F. Camera Stream Configurations

Test combinations:

- Preview only.
- Preview + photo.
- Preview + analysis.
- Preview + video.
- Preview + photo + analysis.
- Preview + video + analysis.
- Multiple output surfaces.
- YUV output.
- JPEG output.
- RAW output.
- PRIVATE output.
- DEPTH output, where supported.
- High-speed recording sessions.
- Reprocessing sessions.

### G. Camera Advanced Capabilities

- Manual exposure.
- Manual ISO.
- Manual focus.
- Manual white balance.
- RAW capture.
- DNG processing.
- High-speed video.
- Slow motion.
- Optical zoom.
- Digital zoom.
- Focus tracking.
- Face detection.
- Lens distortion correction.
- Hot pixel correction.
- Optical image stabilization.
- Electronic image stabilization.
- Multi-camera capture.
- Depth information.
- Reprocessing.
- HDR capture.
- Low-light capture.
- Dynamic range profiles.
- Color space management.
- 10-bit video, where supported.

## 1.4 CameraX — Complete Implementation

Jetpack artifacts:

- `camera-core`
- `camera-camera2`
- `camera-lifecycle`
- `camera-view`
- `camera-video`
- `camera-extensions`

### A. CameraX Core

Implement:

- `ProcessCameraProvider`
- `CameraSelector`
- `CameraInfo`
- `CameraControl`
- `Camera`
- `UseCase`
- `UseCaseGroup`
- `ViewPort`
- `CameraState`

### B. CameraX Use Cases

**1. Preview**

- Live preview.
- Preview scaling.
- Preview rotation.
- Preview transformation.
- Preview resolution.
- Preview surface handling.

**2. ImageCapture**

- Capture image.
- Capture to file.
- Capture to memory.
- Flash modes.
- Capture modes.
- JPEG quality.
- Image rotation.
- Image metadata.
- Capture callbacks.
- Capture errors.

**3. ImageAnalysis**

- YUV analysis.
- RGBA analysis.
- NV21 analysis, where supported.
- Image proxy.
- Frame timestamps.
- Backpressure strategy.
- Frame dropping.
- Analyzer lifecycle.
- ML Kit integration.
- Custom image processing.

**4. VideoCapture**

- Video recording.
- Audio-enabled recording.
- Audio-disabled recording.
- Pause.
- Resume.
- Stop.
- Video quality selection.
- Recording events.
- Recording finalization.
- Recording error handling.
- File output.
- MediaStore output.
- Video metadata.

### C. CameraX Controls

- Zoom state.
- Linear zoom.
- Torch.
- Focus and metering.
- Exposure compensation.
- Camera switching.
- Flash mode.
- Camera state observation.
- Camera availability.
- Use-case rebinding.

### D. CameraX Extensions

Test all available OEM extensions:

- Auto.
- HDR.
- Night.
- Bokeh.
- Face Retouch.

Extension availability must be checked dynamically. They are not guaranteed on every camera or device, and the standard extensions apply to preview and still image capture rather than video capture.

**Version note:** The CameraX Extensions documentation specifies migration to CameraX 1.6 or later for continued extension support on affected devices from November 2026.

## 1.5 CameraX Concurrent Camera

Implement:

- Front + back camera.
- Two simultaneous cameras.
- Concurrent camera discovery.
- Concurrent camera binding.
- Dual preview.
- Concurrent camera frame capture.
- Stream composition.
- Concurrent camera availability.

Test:

- Device supports concurrency.
- Device does not support concurrency.
- Unsupported resolution.
- Camera resource conflict.
- One camera becomes unavailable.

## 1.6 Camera Intents

External system camera access:

- `MediaStore.ACTION_IMAGE_CAPTURE`
- `MediaStore.ACTION_VIDEO_CAPTURE`
- `Intent.ACTION_GET_CONTENT`
- `Intent.ACTION_PICK`
- Photo Picker integration.
- Activity result handling.
- Output URI.
- File provider.
- Captured media retrieval.

यह direct hardware access नहीं है। इसमें actual capture system camera application कर सकती है।

## 1.7 Camera NDK

Native camera package:

`<camera/NdkCameraManager.h>`

Implement:

- `ACameraManager`
- `ACameraDevice`
- `ACameraMetadata`
- `ACameraCaptureSession`
- `ACaptureRequest`
- `ACameraOutputTarget`
- `ACameraOutputTarget_create`
- `ACaptureSessionOutput`
- `ACameraDevice_StateCallbacks`
- `ACameraCaptureSession_capture`
- `ACameraCaptureSession_setRepeatingRequest`
- `AImageReader`
- `AImage`
- `AImageReader_new`
- `AImageReader_acquireNextImage`

Use JNI to connect Kotlin/Java UI with native processing.

## 1.8 Camera Feature Checklist

| Feature | Test |
| --- | --- |
| Front camera | Yes |
| Back camera | Yes |
| Ultra-wide | If exposed |
| Telephoto | If exposed |
| Macro | If exposed |
| Depth camera | If exposed |
| External camera | If supported |
| Camera count | Yes |
| Camera ID | Yes |
| Camera characteristics | Yes |
| Zoom | Yes |
| Optical zoom | If supported |
| Flash | If supported |
| Torch | If supported |
| Autofocus | If supported |
| Manual focus | If supported |
| Face detection | If supported |
| RAW | If supported |
| HDR | If supported |
| Night mode | If supported |
| Portrait / Bokeh | If supported |
| Stabilization | If supported |
| Slow motion | If supported |
| Concurrent cameras | If supported |
| Video recording | Yes |
| Audio with video | With microphone permission |
| Camera interruption | Yes |
| Camera lifecycle | Yes |

---

# PART 2 — AUDIO / MICROPHONE: COMPLETE ACCESS METHODS

Audio recording के लिए भी सिर्फ `MediaRecorder` और `AudioRecord` पर्याप्त नहीं हैं। Android में high-level recording, low-level PCM capture, native audio capture, playback capture और communication-oriented audio paths उपलब्ध हैं।

## 2.1 Audio Access Methods

| No. | API | Purpose |
| --- | --- | --- |
| 1 | `MediaRecorder` | Encoded audio recording |
| 2 | `AudioRecord` | PCM audio input |
| 3 | `AudioRecord.Builder` | Configurable audio capture |
| 4 | `AudioRecord.getMinBufferSize()` | Buffer configuration |
| 5 | `AAudio` | Native low-latency audio |
| 6 | Oboe | Native cross-version audio |
| 7 | `MediaProjection` + Playback Capture | Eligible app playback capture |
| 8 | `AudioPlaybackCaptureConfiguration` | Internal app audio capture |
| 9 | CameraX VideoCapture | Video with microphone audio |
| 10 | Camera2 + MediaRecorder | Camera video/audio recording |
| 11 | Camera2 + MediaCodec + AudioRecord | Custom synchronized A/V |
| 12 | `ACTION_GET_CONTENT` | Existing audio selection |
| 13 | `ContentResolver` | Authorized audio file access |
| 14 | Bluetooth audio input | Supported Bluetooth device routing |
| 15 | USB audio input | Supported USB audio devices |
| 16 | Wired microphone | Supported headset input |
| 17 | OEM / system audio paths | Privileged or vendor-specific |

## 2.2 MediaRecorder — Complete Audio Recording

Implement:

- `MediaRecorder`
- `setAudioSource()`
- `setOutputFormat()`
- `setAudioEncoder()`
- `setAudioSamplingRate()`
- `setAudioEncodingBitRate()`
- `setAudioChannels()`
- `setAudioProfile()`, where supported
- `setOutputFile()`
- `prepare()`
- `start()`
- `pause()`
- `resume()`
- `stop()`
- `reset()`
- `release()`
- `setMaxDuration()`
- `setMaxFileSize()`
- `setOnInfoListener()`
- `setOnErrorListener()`

### Audio Sources to Test

- `DEFAULT`
- `MIC`
- `CAMCORDER`
- `VOICE_RECOGNITION`
- `VOICE_COMMUNICATION`
- `UNPROCESSED`
- `VOICE_PERFORMANCE`

### Output Formats

- MPEG-4.
- 3GPP.
- AAC ADTS.
- AMR-NB.
- AMR-WB.
- Ogg Opus, where supported.
- Other device-supported formats.

### Encoders

- AAC.
- AAC-ELD.
- HE-AAC.
- AMR-NB.
- AMR-WB.
- Opus, where supported.

Actual encoder availability must be queried using `MediaCodecList`; not every device supports every combination.

## 2.3 AudioRecord — Low-level PCM Capture

Implement:

- `AudioRecord`
- `AudioRecord.Builder`
- `getMinBufferSize()`
- `getState()`
- `getRecordingState()`
- `getSampleRate()`
- `getAudioFormat()`
- `getRoutedDevice()`
- `getActiveRecordingConfiguration()`
- `startRecording()`
- `stop()`
- `read()`
- `release()`
- `getTimestamp()`
- `setPreferredDevice()`
- `setPreferredMicrophoneDirection()`
- `setPreferredMicrophoneFieldDimension()`
- `getActiveMicrophones()`

### Input Formats

- PCM 8-bit, where supported.
- PCM 16-bit.
- PCM float.
- Mono.
- Stereo.
- Multichannel input, where supported.

### Sample Rates to Test

- 8 kHz.
- 11.025 kHz.
- 16 kHz.
- 22.05 kHz.
- 32 kHz.
- 44.1 kHz.
- 48 kHz.
- 96 kHz.
- 192 kHz, where hardware supports it.

Do not assume every sample rate works. Query minimum buffer sizes and verify the actual recording configuration.

### Buffer Modes

- Blocking reads.
- Non-blocking reads.
- Direct ByteBuffer.
- ByteArray.
- ShortArray.
- FloatArray.

## 2.4 AAudio — Native Low-latency Audio

NDK package:

`AAudio`

Implement:

- `AAudioStreamBuilder`
- `AAudioStreamBuilder_openStream`
- `AAudioStreamBuilder_setDirection`
- `AAudioStreamBuilder_setSampleRate`
- `AAudioStreamBuilder_setChannelCount`
- `AAudioStreamBuilder_setFormat`
- `AAudioStreamBuilder_setPerformanceMode`
- `AAudioStreamBuilder_setSharingMode`
- `AAudioStreamBuilder_setDataCallback`
- `AAudioStreamBuilder_setErrorCallback`
- `AAudioStream_requestStart`
- `AAudioStream_requestStop`
- `AAudioStream_read`
- `AAudioStream_getTimestamp`
- `AAudioStream_close`

Test:

- Low-latency input.
- Exclusive sharing, where available.
- Shared mode.
- Callback recording.
- Stream errors.
- Stream disconnection.
- Buffer performance.
- Audio timestamps.

## 2.5 Oboe

Oboe is a native C++ audio library that uses AAudio where available and falls back to OpenSL ES on older Android versions.

Implement:

- Input streams.
- Output streams.
- Low-latency capture.
- Callback-based audio.
- Sample rate negotiation.
- Channel configuration.
- Stream disconnection.
- Audio device changes.
- Buffer size tuning.
- Full-duplex audio.
- Real-time audio processing.

## 2.6 OpenSL ES — Legacy Native Audio

Implement for compatibility research:

- `SLObjectItf`
- `SLRecordItf`
- `SLAndroidSimpleBufferQueueItf`
- Recorder creation.
- Buffer queue callbacks.
- PCM input.
- Recording state.
- Resource cleanup.

OpenSL ES is a legacy route. For new native low-latency development, prefer Oboe/AAudio.

## 2.7 Audio Playback Capture

APIs:

- `MediaProjectionManager`
- `MediaProjection`
- `AudioPlaybackCaptureConfiguration`
- `AudioRecord`
- `AudioAttributes`

Implement:

- User-approved screen/audio capture.
- Eligible app playback capture.
- Media playback capture.
- Game audio capture.
- Capture configuration.
- Audio usage filtering.
- Capture session lifecycle.
- Projection stop handling.
- Audio routing.
- Recording output.

Limitations:

- Requires `RECORD_AUDIO`.
- Requires user approval through MediaProjection.
- Same user profile requirement.
- Source app can opt out of playback capture.
- Not all audio usages are capturable.
- Protected and telephony audio are not made available through this mechanism.

## 2.8 Audio Effects

Implement and detect availability:

- `AcousticEchoCanceler`
- `NoiseSuppressor`
- `AutomaticGainControl`
- `DynamicsProcessing`
- `EnvironmentalReverb`
- `Equalizer`
- `BassBoost`
- `Virtualizer`
- `LoudnessEnhancer`
- `PresetReverb`

For each effect:

1. Query availability.
2. Create effect session.
3. Enable / disable.
4. Read parameters.
5. Modify supported parameters.
6. Release resources.

## 2.9 Audio Routing

Implement:

- Built-in microphone.
- Front microphone.
- Back microphone.
- Wired headset microphone.
- USB microphone.
- Bluetooth SCO microphone.
- Bluetooth LE audio input, where supported.
- Speaker output.
- Earpiece output.
- Wired headphone output.
- USB audio output.
- Bluetooth output.

APIs:

- `AudioManager`
- `AudioDeviceInfo`
- `AudioDeviceCallback`
- `AudioRouting`
- `AudioRecordingConfiguration`
- `AudioFocusRequest`
- `AudioAttributes`
- `AudioTrack`
- `AudioPlaybackConfiguration`

Events:

- Device added.
- Device removed.
- Preferred device changed.
- Recording configuration changed.
- Audio focus gained.
- Audio focus lost.
- Audio focus ducked.
- Route changed.
- Communication device changed.

## 2.10 Microphone Characteristics

Implement:

- Available microphones.
- Microphone ID.
- Microphone type.
- Microphone location.
- Microphone orientation.
- Frequency response.
- Maximum SPL.
- Sensitivity.
- Channel mapping.
- Active microphone.
- Microphone direction.
- Field dimension.

Use:

- `MicrophoneInfo`
- `AudioRecordingConfiguration`
- `AudioDeviceInfo`
- `AudioRecord.getActiveMicrophones()`

## 2.11 Audio Analysis

- RMS.
- Peak amplitude.
- Decibel approximation.
- FFT.
- Frequency spectrum.
- Pitch detection.
- Voice activity detection.
- Silence detection.
- Audio waveform.
- Audio energy.
- Audio clipping.
- Signal-to-noise measurements.
- Sample loss detection.
- Recording latency.
- Timestamp drift.
- Audio level history.

## 2.12 Audio Recording Limitations

`VOICE_UPLINK`, `VOICE_DOWNLINK` और `VOICE_CALL` audio sources सामान्य third-party apps के लिए universal cellular call recording solution नहीं हैं। इनके लिए `CAPTURE_AUDIO_OUTPUT` जैसे privileged permissions की आवश्यकता होती है।

`REMOTE_SUBMIX` भी सामान्य third-party microphone access का alternative नहीं है।

इनके लिए system privilege या specific OEM implementation की जरूरत हो सकती है।

Official references:

- <https://developer.android.com/reference/android/media/AudioRecord>
- <https://developer.android.com/reference/android/media/MediaRecorder>
- <https://developer.android.com/ndk/guides/audio/aaudio/aaudio>
- <https://developer.android.com/games/sdk/oboe>
- <https://developer.android.com/media/platform/av-capture>

---

# PART 3 — VIDEO CAPTURE & MEDIA PIPELINE

## 3.1 Video Recording Paths

- CameraX VideoCapture.
- Camera2 + MediaRecorder.
- Camera2 + MediaCodec.
- Camera2 + MediaMuxer.
- Legacy Camera1 + MediaRecorder.
- External camera + MediaRecorder, where supported.
- Screen recording + MediaProjection.
- Screen recording + MediaCodec.

## 3.2 Video Parameters

- Resolution.
- Frame rate.
- Bitrate.
- Codec.
- Encoder profile.
- Keyframe interval.
- Video rotation.
- Video stabilization.
- Dynamic range.
- HDR profile.
- Color format.
- Container format.
- Audio track.
- Metadata track.
- Timestamp synchronization.

## 3.3 Media APIs

- `MediaCodec`
- `MediaCodecList`
- `MediaFormat`
- `MediaMuxer`
- `MediaExtractor`
- `MediaMetadataRetriever`
- `MediaRecorder`
- `MediaPlayer`
- `Media3`
- `Media3 Transformer`

## 3.4 Video Processing

- Video trimming.
- Transcoding.
- Compression.
- Rotation.
- Cropping.
- Frame extraction.
- Thumbnail generation.
- Audio-video synchronization.
- Video metadata reading.
- Video track inspection.
- Codec capability inspection.

---

# PART 4 — SMS / MMS / MESSAGING

## 4.1 SMS Receiving

- Incoming SMS broadcast.
- SMS sender extraction.
- Message body.
- Timestamp.
- Multipart message.
- Subscription ID.
- SMS format.
- SMS PDU.
- Message encoding.
- SMS delivery metadata.

APIs:

- `SmsManager`
- `Telephony.Sms.Intents`
- `BroadcastReceiver`
- `SmsMessage`
- `Telephony.Sms`

## 4.2 SMS Sending

- Single SMS.
- Multipart SMS.
- Unicode SMS.
- Hindi SMS.
- Long SMS.
- Selected SIM.
- Sent callback.
- Delivery callback.
- Failed SMS handling.
- Retry workflow.
- SMS encoding checks.

## 4.3 SMS Database

- Inbox.
- Sent.
- Draft.
- Outbox.
- Conversation.
- Thread ID.
- Read state.
- Message type.
- Date.
- Address.
- Body.
- Status.

Default SMS application requirements must be handled separately from ordinary SMS permission access.

## 4.4 SMS Features

- SMS notifications.
- SMS search.
- SMS grouping.
- SMS export.
- SMS backup.
- User-controlled forwarding.
- User-selected deletion.
- Spam classification.
- Block/report workflows.
- SMS Retriever API.
- SMS User Consent API.
- Verification code handling.
- Carrier-specific SMS support.

## 4.5 MMS

- Incoming MMS.
- Outgoing MMS.
- MMS notification.
- WAP Push.
- Attachment handling.
- Image attachment.
- Audio attachment.
- Video attachment.
- MMS metadata.
- Carrier MMSC configuration.

## 4.6 RCS

Research separately:

- RCS capability discovery.
- RCS messaging.
- Group messaging.
- Read receipts.
- Typing indicators.
- Rich media.
- Business messaging.

RCS is not a general-purpose public Android inbox API. Carrier and messaging-client support is required.

---

# PART 5 — CALLING / TELECOM

## 5.1 Calling Methods

- `ACTION_DIAL`
- `ACTION_CALL`
- `TelecomManager.placeCall()`
- `ConnectionService`
- `PhoneAccount`
- `PhoneAccountHandle`
- Self-managed calling.
- Managed calling.
- VoIP integration.
- Video calling integration.

## 5.2 Call State Detection

- IDLE.
- RINGING.
- OFFHOOK.
- Incoming.
- Outgoing.
- Active.
- On hold.
- Disconnected.
- Rejected.
- Missed.
- Conference.
- Call waiting.
- Call handover.

## 5.3 Telephony APIs

- `TelephonyManager`
- `TelephonyCallback`
- `PhoneStateListener` (legacy)
- `TelecomManager`
- `InCallService`
- `CallScreeningService`
- `ConnectionService`
- `Call`
- `Call.Details`
- `PhoneAccount`
- `CallAudioState`

## 5.4 Call Screening

- Incoming caller identification.
- Number verification.
- Spam classification.
- Call allow.
- Call disallow.
- Call silence.
- Caller ID display.
- STIR/SHAKEN verdict handling, where available.
- Screening timeout.
- Screening response.

## 5.5 Call Management

- Answer.
- Reject.
- Disconnect.
- Hold.
- Unhold.
- Mute.
- Unmute.
- DTMF.
- Speaker.
- Bluetooth audio.
- Conference.
- Merge.
- Split.
- Swap.
- Audio route selection.

इनमें से कई functions के लिए default dialer या Telecom-managed call role आवश्यक है।

## 5.6 Call History

- Incoming call log.
- Outgoing call log.
- Missed call log.
- Rejected call data, where available.
- Number.
- Date.
- Duration.
- Cached contact name.
- Call type.
- Search.
- Filtering.
- Export.

## 5.7 VoIP

- SIP-based calling.
- WebRTC-based calling.
- Self-managed Telecom calls.
- Audio routing.
- Video calling.
- Call notification.
- Call lifecycle.
- Call statistics.
- Network handover.

## 5.8 Cellular Call Recording

- Call audio state.
- Call recording capability detection.
- Audio source availability checks.
- OEM-specific recording support.
- Recording policy detection.
- Legal consent workflow.

**यहाँ स्पष्ट limitation:** सामान्य ऐप cellular call के दोनों पक्षों का audio universally record नहीं कर सकता। अपने VoIP app के अंदर की call recording अलग और implementable feature है।

---

# PART 6 — PHONE NUMBER / SIM / NETWORK OPERATOR

## 6.1 Phone Number

- Own phone number.
- SIM-associated phone number.
- Number availability.
- Country code.
- Number formatting.
- E.164 conversion.
- Validation.
- Phone number normalization.
- Contact matching.

APIs:

- `TelephonyManager`
- `SubscriptionManager`
- `PhoneNumberUtils`

## 6.2 SIM Information

- SIM presence.
- SIM state.
- SIM slot.
- Subscription ID.
- Display name.
- Carrier name.
- MCC.
- MNC.
- Country ISO.
- Roaming.
- Default SMS SIM.
- Default voice SIM.
- Default data SIM.
- Carrier ID.
- Subscription change events.

## 6.3 Restricted SIM Information

इनका research और availability checker बनाएं:

- ICCID.
- IMSI.
- IMEI.
- Device identifiers.
- EID for eSIM.
- SIM serial number.
- Subscriber identity.

इनमें से कई identifiers सामान्य apps के लिए inaccessible या highly restricted हैं।

## 6.4 eSIM

- eSIM availability.
- Embedded subscription discovery.
- Subscription management.
- Carrier provisioning.
- eSIM profile download.
- Profile activation.
- Profile deactivation.
- Profile deletion.

ये operations carrier privilege, LPA/system integration और supported device functionality पर निर्भर हैं।

---

# PART 7 — LOCATION / GPS

## 7.1 Location Access Methods

- Android `LocationManager`.
- Google Play Services `FusedLocationProviderClient`.
- `CurrentLocation`.
- Last known location.
- Continuous location updates.
- Passive location provider.
- GPS provider.
- Network provider.
- Geofencing.
- Activity recognition.
- Geocoding.
- Reverse geocoding.

## 7.2 Location Data

- Latitude.
- Longitude.
- Accuracy.
- Altitude.
- Speed.
- Bearing.
- Provider.
- Timestamp.
- Elapsed realtime.
- Mock location indicator.
- Vertical accuracy.
- Speed accuracy.
- Bearing accuracy.

## 7.3 Location Events

- Location received.
- GPS fix acquired.
- Location provider enabled.
- Location provider disabled.
- Permission changed.
- Precise permission changed.
- Approximate permission changed.
- Geofence entered.
- Geofence exited.
- Geofence dwell.
- Location service interrupted.

## 7.4 Location Features

- Live tracking.
- Route recording.
- Distance calculation.
- Speed calculation.
- Location history.
- Location sharing.
- Geofencing.
- Background tracking.
- Location notification.
- Mock location detection.
- Battery-efficient tracking.
- Location accuracy testing.

---

# PART 8 — SENSORS

## 8.1 Sensor Access

API:

`SensorManager`

Implement:

- `getSensorList()`
- `getDefaultSensor()`
- `registerListener()`
- `unregisterListener()`
- `getDynamicSensorList()`
- `requestTriggerSensor()`

## 8.2 Sensor Inventory

- Accelerometer.
- Gyroscope.
- Magnetometer.
- Gravity.
- Linear acceleration.
- Rotation vector.
- Game rotation vector.
- Geomagnetic rotation vector.
- Step detector.
- Step counter.
- Significant motion.
- Proximity.
- Light.
- Pressure.
- Relative humidity.
- Ambient temperature, where present.
- Heart rate, on supported devices.
- Heartbeat, where supported.
- Pose-related sensors, where supported.
- Hinge angle.
- Device posture.
- Dynamic sensors.
- Wake-up sensors.
- Non-wake-up sensors.

## 8.3 Sensor Metadata

- Name.
- Vendor.
- Version.
- Resolution.
- Maximum range.
- Power consumption.
- Minimum delay.
- FIFO maximum.
- FIFO reserved count.
- Wake-up property.
- Reporting mode.

## 8.4 Sensor Operations

- Sensor event listener.
- Sensor accuracy changes.
- Sampling frequency.
- Batching.
- Flush.
- Direct sensor channels.
- Sensor availability.
- Sensor removal.
- Dynamic sensor connection.

---

# PART 9 — BLUETOOTH / USB / CONNECTED DEVICES

## 9.1 Bluetooth

- Bluetooth state.
- Bluetooth adapter.
- Classic Bluetooth.
- BLE scanning.
- BLE advertising.
- BLE GATT client.
- BLE GATT server.
- Device discovery.
- Device pairing workflow.
- Bond state.
- Connection state.
- Bluetooth audio device.
- Bluetooth LE Audio.
- Companion device pairing.

## 9.2 USB

- USB device discovery.
- USB host mode.
- USB accessory mode.
- USB permission.
- USB device connection.
- USB disconnection.
- USB audio.
- USB camera, where supported.
- USB serial devices.
- USB transfer.

## 9.3 Device Connectivity Events

- Device connected.
- Device disconnected.
- Device paired.
- Device unpaired.
- USB attached.
- USB detached.
- Bluetooth state changed.
- Audio device changed.

---

# PART 10 — NETWORK / CONNECTIVITY

- Wi-Fi status.
- Wi-Fi network connection.
- Network capabilities.
- Network callback.
- Cellular network state.
- 2G / 3G / 4G / 5G availability.
- Network type.
- Signal strength.
- Service state.
- Roaming.
- VPN state.
- Ethernet.
- Local IP.
- DNS.
- Network binding.
- Network handover.
- Data transfer statistics.
- Connectivity validation.
- Internet reachability testing.

APIs:

- `ConnectivityManager`
- `NetworkCallback`
- `NetworkCapabilities`
- `WifiManager`
- `TelephonyManager`
- `TelephonyCallback`
- `TrafficStats`

---

# PART 11 — NOTIFICATION / SYSTEM EVENTS

## 11.1 Notifications

- Create notification.
- Update notification.
- Cancel notification.
- Notification channels.
- Notification actions.
- Notification groups.
- Foreground notification.
- Notification listeners.
- Notification history limitations.

## 11.2 System Events

- Boot completed.
- Time changed.
- Time zone changed.
- Date changed.
- Airplane mode changed.
- Battery changed.
- Power connected.
- Power disconnected.
- Screen on.
- Screen off.
- User present.
- Package added.
- Package removed.
- Package replaced.
- App updated.
- Locale changed.
- Configuration changed.
- Storage mounted.
- Storage removed.
- Headset connected.
- Bluetooth device connected.
- USB device attached.
- Network changed.

हर event के लिए modern API, legacy broadcast और manifest/runtime receiver differences को document करें।

---

# PART 12 — MEDIA STORAGE / FILE SYSTEM

- MediaStore images.
- MediaStore videos.
- MediaStore audio.
- Photo Picker.
- Storage Access Framework.
- `ACTION_OPEN_DOCUMENT`.
- `ACTION_CREATE_DOCUMENT`.
- `ACTION_OPEN_DOCUMENT_TREE`.
- ContentResolver.
- FileProvider.
- Scoped storage.
- Media metadata.
- EXIF metadata.
- Audio metadata.
- Video metadata.
- File URI access.
- Document URI access.
- Media deletion consent.

---

# PART 13 — DEVICE INFORMATION

- Manufacturer.
- Model.
- Brand.
- Product.
- Device.
- Android release.
- SDK level.
- Build fingerprint.
- Supported ABIs.
- CPU information.
- RAM.
- Internal storage.
- External storage.
- Battery.
- Display.
- Screen density.
- Refresh rate.
- Resolution.
- Available features.
- Available cameras.
- Available sensors.
- Telephony hardware.
- NFC.
- USB.
- Bluetooth.
- Wi-Fi.
- UWB, where supported.

---

# PART 14 — SECURITY / PERMISSION / ROLE MANAGEMENT

## 14.1 Permissions

Implement a centralized permission inventory:

- Normal permissions.
- Dangerous permissions.
- Runtime permissions.
- Special app access.
- Restricted permissions.
- Signature permissions.
- Privileged permissions.
- Role-based permissions.

## 14.2 Roles

- Default SMS.
- Default Dialer.
- Call Screening.
- Assistant.
- Browser.
- Home.
- Emergency.
- Wallet, where applicable.

Use `RoleManager` to inspect/request supported roles.

## 14.3 Permission Events

- Permission granted.
- Permission denied.
- Permission permanently denied.
- Permission revoked.
- One-time permission.
- Auto-reset.
- App-op changed.
- Special access unavailable.

## 14.4 App Operations

Research:

- `AppOpsManager`.
- Permission flags.
- Runtime permission status.
- Usage access.
- Notification access.
- Accessibility access.
- MediaProjection approval.
- Battery optimization exemption.
- All files access, where eligible.

Do not attempt to bypass the user's permission choices or Android privacy indicators.

---

# PART 15 — FOREGROUND SERVICES / BACKGROUND EXECUTION

Implement separate service types:

- Camera.
- Microphone.
- Location.
- Phone call.
- Media playback.
- Media projection.
- Connected device.
- Data synchronization.
- Media processing.

Also test:

- Foreground service start.
- Foreground service stop.
- Notification visibility.
- Service lifecycle.
- Process recreation.
- Task removal.
- Boot handling.
- Background execution limits.
- Battery optimization.
- Doze mode.
- App standby.
- WorkManager.
- JobScheduler.
- AlarmManager.

---

# PART 16 — API TESTING FRAMEWORK

हर API के लिए एक standardized test result बनाएं।

| Field | Description |
| --- | --- |
| Feature ID | Unique identifier |
| API name | API / class / method |
| Category | Camera / Audio / SIM आदि |
| Min SDK | Minimum Android version |
| Max SDK | Maximum tested version |
| Permission | Required permission |
| Role | Required role |
| Device support | Supported / Unsupported |
| Execution | Pass / Fail / Blocked |
| Output | Returned data |
| Exception | Error information |
| Timestamp | Test time |
| Notes | Limitations |

## Required test cases

1. Permission granted.
2. Permission denied.
3. Permission revoked.
4. Feature unsupported.
5. Hardware unavailable.
6. App foreground.
7. App background.
8. App process killed.
9. Device reboot.
10. Airplane mode.
11. No SIM.
12. Dual SIM.
13. No internet.
14. Battery saver.
15. OEM-specific restrictions.
16. API unavailable on old Android.
17. API deprecated.
18. API requires system role.
19. Concurrent resource conflict.
20. Device rotation and lifecycle interruption.

---

# PART 17 — IMPORTANT PLATFORM LIMITATIONS

निम्नलिखित को पूर्ण implementation guarantee के रूप में नहीं लेना चाहिए:

| Capability | Limitation |
| --- | --- |
| Cellular call recording | No universal public third-party API |
| Hidden microphone recording | Not an acceptable general app capability |
| Hidden camera recording | Not an acceptable general app capability |
| IMEI | Highly restricted |
| IMSI | Highly restricted |
| ICCID | Restricted |
| SIM phone number | Not guaranteed |
| SMS database write | Default SMS role |
| SMS / call log permissions | Google Play restrictions |
| Background location | Special permission and policy |
| Background microphone | Foreground service restrictions |
| OEM camera modes | Device-dependent |
| RAW camera | Device-dependent |
| Multi-camera | Device-dependent |
| Audio sample rates | Device-dependent |
| Audio effects | Device-dependent |
| eSIM management | Carrier/system integration |
| Bluetooth audio input | Device/profile dependent |

---

# PART 18 — OFFICIAL DOCUMENTATION INDEX

Use official Android documentation as the source of truth.

## Camera

- <https://developer.android.com/media/camera>
- <https://developer.android.com/media/camera/choose-camera-library>
- <https://developer.android.com/media/camera/camerax>
- <https://developer.android.com/reference/android/hardware/camera2/package-summary>
- <https://developer.android.com/ndk/reference/group/camera>

## Audio

- <https://developer.android.com/reference/android/media/AudioRecord>
- <https://developer.android.com/reference/android/media/MediaRecorder>
- <https://developer.android.com/reference/android/media/AudioManager>
- <https://developer.android.com/ndk/guides/audio/aaudio/aaudio>
- <https://developer.android.com/games/sdk/oboe>

## Telephony

- <https://developer.android.com/reference/android/telephony/TelephonyManager>
- <https://developer.android.com/reference/android/telephony/SubscriptionManager>
- <https://developer.android.com/reference/android/telephony/SmsManager>
- <https://developer.android.com/develop/connectivity/telecom>

## Location

- <https://developer.android.com/develop/sensors-and-location/location>

## Sensors

- <https://developer.android.com/reference/android/hardware/SensorManager>

## Permissions

- <https://developer.android.com/guide/topics/permissions/overview>

## Background Execution

- <https://developer.android.com/develop/background-work/services/foreground-services>

---

# FINAL PROJECT OBJECTIVE

The final application should have:

1. Every major Android public hardware API represented.
2. Separate implementations for legacy, modern and native API paths.
3. Every supported camera capability tested.
4. Every supported audio capture path tested.
5. All available system events documented.
6. All applicable permissions and roles managed.
7. Unsupported features detected and reported.
8. Android version compatibility recorded.
9. Device-specific limitations documented.
10. Complete API test reports stored locally.

**Important scope statement:** Android's public SDK, Jetpack libraries, NDK, OEM APIs and privileged system APIs are different API domains. This document is a comprehensive implementation roadmap, not a claim that every private, proprietary or privileged API has been enumerated. For each new Android release, the SDK/API reference and release notes must be checked again.

**Definition of Done:** A feature is complete only when its API is implemented, permission/role requirements are documented, hardware support is checked, positive and negative cases are tested, and limitations are recorded.
