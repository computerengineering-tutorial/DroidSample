package com.example.droidsample.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Robust controller wrapping Android MediaPlayer with reactive state streams.
 */
class MediaPlayerController(private val context: Context) {

    sealed interface PlayerState {
        data object Idle : PlayerState
        data object Preparing : PlayerState
        data class Ready(val durationMs: Int) : PlayerState
        data class Playing(val positionMs: Int, val durationMs: Int) : PlayerState
        data class Paused(val positionMs: Int, val durationMs: Int) : PlayerState
        data class Completed(val durationMs: Int) : PlayerState
        data class Error(val what: Int, val extra: Int, val message: String) : PlayerState
    }

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0)
    val currentPositionMs: StateFlow<Int> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0)
    val durationMs: StateFlow<Int> = _durationMs.asStateFlow()

    private val _isLooping = MutableStateFlow(false)
    val isLooping: StateFlow<Boolean> = _isLooping.asStateFlow()

    private val _volume = MutableStateFlow(1.0f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private var pendingSurfaceHolder: SurfaceHolder? = null

    /**
     * Prepares and loads the media source from a given Uri.
     */
    fun loadMedia(uri: Uri) {
        release()
        _playerState.value = PlayerState.Preparing

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = _isLooping.value
                setVolume(_volume.value, _volume.value)

                pendingSurfaceHolder?.let { setDisplay(it) }

                setOnPreparedListener { mp ->
                    val dur = mp.duration
                    _durationMs.value = dur
                    _playerState.value = PlayerState.Ready(dur)
                }

                setOnCompletionListener {
                    stopProgressTracking()
                    _currentPositionMs.value = _durationMs.value
                    _playerState.value = PlayerState.Completed(_durationMs.value)
                }

                setOnErrorListener { _, what, extra ->
                    stopProgressTracking()
                    _playerState.value = PlayerState.Error(
                        what = what,
                        extra = extra,
                        message = "Playback error (what: $what, extra: $extra)"
                    )
                    true
                }

                prepareAsync()
            }
            mediaPlayer = player
        } catch (e: Exception) {
            _playerState.value = PlayerState.Error(
                what = -1,
                extra = -1,
                message = "Initialization failed: ${e.message}"
            )
        }
    }

    /**
     * Binds display surface to the MediaPlayer.
     */
    fun attachSurface(surfaceHolder: SurfaceHolder?) {
        pendingSurfaceHolder = surfaceHolder
        mediaPlayer?.setDisplay(surfaceHolder)
    }

    fun play() {
        val player = mediaPlayer ?: return
        if (!player.isPlaying) {
            player.start()
            _playerState.value = PlayerState.Playing(player.currentPosition, player.duration)
            startProgressTracking()
        }
    }

    fun pause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            stopProgressTracking()
            _playerState.value = PlayerState.Paused(player.currentPosition, player.duration)
        }
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.let { player ->
            player.seekTo(positionMs)
            _currentPositionMs.value = positionMs
            val cur = _playerState.value
            if (cur is PlayerState.Paused) {
                _playerState.value = PlayerState.Paused(positionMs, player.duration)
            } else if (cur is PlayerState.Playing) {
                _playerState.value = PlayerState.Playing(positionMs, player.duration)
            }
        }
    }

    fun setLooping(enabled: Boolean) {
        _isLooping.value = enabled
        mediaPlayer?.isLooping = enabled
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0.0f, 1.0f)
        _volume.value = clamped
        mediaPlayer?.setVolume(clamped, clamped)
    }

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let { player ->
                    if (player.isPlaying) {
                        val pos = player.currentPosition
                        _currentPositionMs.value = pos
                        _playerState.value = PlayerState.Playing(pos, player.duration)
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracking()
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                reset()
                release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _playerState.value = PlayerState.Idle
        _currentPositionMs.value = 0
        _durationMs.value = 0
    }
}
