package com.example.droidsample.audio

/**
 * Architectural bridge and interface representation for Android Native Low-Latency Audio (AAudio / Oboe).
 *
 * AAudio (API 26+) and Google Oboe bypass the Java AudioTrack / AudioRecord audio server overhead,
 * establishing direct MMAP (Memory-Mapped) hardware ring-buffer access to the ALSA audio DSP for
 * ultra low-latency (< 10ms round-trip) audio input/output.
 */
object NativeAudioBridge {

    // AAudio Direction
    const val AAUDIO_DIRECTION_OUTPUT: Int = 0
    const val AAUDIO_DIRECTION_INPUT: Int = 1

    // AAudio Format
    const val AAUDIO_FORMAT_UNSPECIFIED: Int = 0
    const val AAUDIO_FORMAT_PCM_I16: Int = 1
    const val AAUDIO_FORMAT_PCM_FLOAT: Int = 2

    // AAudio Sharing Mode
    const val AAUDIO_SHARING_MODE_EXCLUSIVE: Int = 0
    const val AAUDIO_SHARING_MODE_SHARED: Int = 1

    // AAudio Performance Mode
    const val AAUDIO_PERFORMANCE_MODE_NONE: Int = 10
    const val AAUDIO_PERFORMANCE_MODE_POWER_SAVING: Int = 11
    const val AAUDIO_PERFORMANCE_MODE_LOW_LATENCY: Int = 12

    // AAudio Stream State
    const val AAUDIO_STREAM_STATE_UNINITIALIZED: Int = 0
    const val AAUDIO_STREAM_STATE_UNKNOWN: Int = 1
    const val AAUDIO_STREAM_STATE_OPEN: Int = 2
    const val AAUDIO_STREAM_STATE_STARTED: Int = 3
    const val AAUDIO_STREAM_STATE_PAUSED: Int = 4
    const val AAUDIO_STREAM_STATE_STOPPED: Int = 5
    const val AAUDIO_STREAM_STATE_CLOSED: Int = 11

    data class StreamConfig(
        val direction: Int = AAUDIO_DIRECTION_INPUT,
        val sampleRate: Int = 48000,
        val channelCount: Int = 2,
        val format: Int = AAUDIO_FORMAT_PCM_FLOAT,
        val sharingMode: Int = AAUDIO_SHARING_MODE_EXCLUSIVE,
        val performanceMode: Int = AAUDIO_PERFORMANCE_MODE_LOW_LATENCY,
        val framesPerBurst: Int = 192
    )

    data class StreamStats(
        val state: Int = AAUDIO_STREAM_STATE_UNINITIALIZED,
        val xRunCount: Int = 0,
        val framesRead: Long = 0L,
        val framesWritten: Long = 0L,
        val bufferCapacityInFrames: Int = 384,
        val bufferSizeInFrames: Int = 192,
        val isMMapUsed: Boolean = false,
        val estimatedLatencyMs: Float = 0f
    )

    interface NativeAudioCallback {
        /**
         * Called on a high-priority realtime audio thread when new audio frames are ready.
         */
        fun onAudioData(audioData: FloatArray, numFrames: Int)

        /**
         * Called when an audio error or buffer underflow/overflow occurs.
         */
        fun onError(errorCode: Int, errorMessage: String)
    }

    private var isNativeLibLoaded = false

    init {
        try {
            System.loadLibrary("droidsample_native_audio")
            isNativeLibLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            isNativeLibLoaded = false
        }
    }

    val isNativeEngineAvailable: Boolean
        get() = isNativeLibLoaded

    /**
     * Diagnostic report on AAudio hardware capabilities and status.
     */
    fun getHardwareDiagnostics(): Map<String, String> {
        return mapOf(
            "Native Library Loaded" to isNativeLibLoaded.toString(),
            "Recommended Performance Mode" to "AAUDIO_PERFORMANCE_MODE_LOW_LATENCY",
            "Optimal Hardware Sample Rate" to "48000 Hz",
            "MMAP Direct Ring Buffer" to if (isNativeLibLoaded) "Supported" else "Emulated / Stub",
            "Typical Round-Trip Latency" to "< 10 ms (Hardware dependent)"
        )
    }

    /**
     * Stubs for native JNI methods (when native C++ engine is linked).
     */
    fun openStream(config: StreamConfig): Boolean {
        return isNativeLibLoaded
    }

    fun startStream(): Boolean {
        return isNativeLibLoaded
    }

    fun stopStream(): Boolean {
        return isNativeLibLoaded
    }

    fun closeStream(): Boolean {
        return isNativeLibLoaded
    }
}
