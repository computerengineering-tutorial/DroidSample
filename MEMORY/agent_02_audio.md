# Subagent 02: Audio & Microphone Specialist TODO & Implementation Specification

## Scope & Objective
Implement all public Android audio capture, routing, hardware inspection, and sound analysis methods natively using Kotlin and Jetpack Compose.
Focus strictly on direct code generation, exhaustive API coverage, and UI integration. Do NOT waste context on long explanations or deep reasoning.

## Target Directories & Files
- Core logic: `app-core/src/main/kotlin/com/example/droidsample/audio/`
  - `MediaAudioRecorder.kt` (`MediaRecorder` across all sources & formats)
  - `PcmAudioRecorder.kt` (`AudioRecord` & `AudioRecord.Builder` low-level PCM capture)
  - `AudioRoutingManager.kt` (`AudioManager`, `AudioDeviceInfo`, `AudioDeviceCallback`)
  - `MicrophoneCharacteristicsManager.kt` (`MicrophoneInfo` query, direction, sensitivity)
  - `AudioEffectsManager.kt` (Equalizer, BassBoost, AcousticEchoCanceler, NoiseSuppressor, DynamicsProcessing)
  - `AudioAnalyzer.kt` (Real-time RMS, peak dB, waveform generation, FFT frequency bands)
  - `AudioPlaybackCaptureHelper.kt` (`AudioPlaybackCaptureConfiguration` & limitations)
  - `NativeAudioBridge.kt` (AAudio / Oboe architecture interfaces)
- UI Screens: `app-core/src/main/kotlin/com/example/droidsample/ui/audio/`
  - `AudioScreen.kt` (Parent dashboard)
  - `MediaRecorderView.kt` (Source/Encoder selection & recording player)
  - `PcmRecorderView.kt` (Live oscilloscope waveform & VU meter)
  - `AudioEffectsView.kt` (Equalizer sliders, reverb, noise suppressor toggles)
  - `AudioRoutingView.kt` (Available audio inputs/outputs, communication device selector)
- Unit Tests: `app-core/src/test/kotlin/com/example/droidsample/audio/`
  - `AudioAnalysisTest.kt` (RMS, peak amplitude, buffer converter tests)
  - `AudioConfigTest.kt` (Sample rate & buffer size calculation tests)

## Detailed Implementation Checklist

### 1. MediaRecorder High-Level Recording
- [ ] Sources: `DEFAULT`, `MIC`, `CAMCORDER`, `VOICE_RECOGNITION`, `VOICE_COMMUNICATION`, `UNPROCESSED`, `VOICE_PERFORMANCE`.
- [ ] Output formats: MPEG-4, 3GPP, AAC ADTS, AMR-NB, AMR-WB, Ogg Opus.
- [ ] Audio encoders: AAC, AAC-ELD, HE-AAC, AMR-NB, AMR-WB, Opus.
- [ ] Config: sample rates (8k - 48kHz), bitrates, channels (mono/stereo), max file size/duration listeners.
- [ ] Controls: `prepare()`, `start()`, `pause()`, `resume()`, `stop()`, `reset()`, `release()`.

### 2. AudioRecord Low-Level PCM Streaming
- [ ] `AudioRecord.Builder` configuration.
- [ ] Formats: `ENCODING_PCM_16BIT`, `ENCODING_PCM_FLOAT`, `ENCODING_PCM_8BIT`.
- [ ] Sample rates to test: 8000, 11025, 16000, 22050, 32000, 44100, 48000, 96000, 192000 Hz.
- [ ] Buffer handling: `getMinBufferSize()`, direct `ByteBuffer`, `ShortArray`, `FloatArray`.
- [ ] Streaming loop in coroutine with real-time buffer emission for UI visualization.

### 3. Audio Effects & Enhancements
- [ ] Check availability and initialize:
  - `AcousticEchoCanceler`
  - `NoiseSuppressor`
  - `AutomaticGainControl`
  - `Equalizer` (bands, band levels, presets)
  - `BassBoost` (strength)
  - `Virtualizer` (strength)
  - `DynamicsProcessing`
  - `LoudnessEnhancer`
- [ ] Safely handle unsupported effects per device OEM.

### 4. Audio Routing & Device Management
- [ ] `AudioManager.getDevices(GET_DEVICES_INPUTS / GET_DEVICES_OUTPUTS)`.
- [ ] Inspect `AudioDeviceInfo`: type (built-in mic, headset, USB, Bluetooth SCO/A2DP, LE Audio), channels, sample rates.
- [ ] Register `AudioDeviceCallback` for dynamic attach/detach events.
- [ ] Preferred communication device routing (`setCommunicationDevice()`).
- [ ] Audio focus management (`AudioFocusRequest`, gain/loss/ducking).

### 5. Microphone Characteristics & Inspection
- [ ] Query `AudioManager.getMicrophones()`, `AudioRecord.getActiveMicrophones()`.
- [ ] Read `MicrophoneInfo`: ID, type, address, location (main, secondary), orientation, frequency response curves, max SPL, sensitivity.
- [ ] Microphone directionality: `setPreferredMicrophoneDirection()`, `setPreferredMicrophoneFieldDimension()`.

### 6. Real-Time Audio Analysis
- [ ] Calculate RMS (Root Mean Square) energy.
- [ ] Peak amplitude and Decibel (dBFS) approximation.
- [ ] Waveform downsampling for 60fps Compose canvas renderer.
- [ ] Basic frequency band energy estimation for visualizer bars.

### 7. Native Low-Latency Audio Architecture (AAudio / Oboe)
- [ ] Document and provide clean Kotlin bindings / architecture stubs representing `AAudioStreamBuilder` and low-latency audio callbacks.

### 8. UI Requirements (Jetpack Compose)
- Multi-tabbed interface:
  1. Recorder Studio (MediaRecorder & PCM stream controls).
  2. Live Oscilloscope & Audio Level Meters (Canvas waveform + VU meters).
  3. Audio Effects Console (Sliders, switches, and preset pickers).
  4. Audio Devices & Mic Diagnostics (Full hardware characteristics list).
- Clear permission handling for `android.permission.RECORD_AUDIO` and `android.permission.MODIFY_AUDIO_SETTINGS`.

### 9. Tests
- Mathematical tests for RMS, peak dBFS, PCM byte-to-short conversion, and sample buffer management.
