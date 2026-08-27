package com.wxkzd.yuanlu.core.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wxkzd.yuanlu.domain.model.Episode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    val exoPlayer: ExoPlayer
) {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.update {
                    it.copy(
                        playbackState = playbackState,
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                    )
                }
            }
        })
    }

    fun play(episode: Episode, startPositionMs: Long = 0L) {
        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId(episode.episodeid)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.play()

        _playerState.update {
            it.copy(
                currentEpisode = episode,
                currentPosition = startPositionMs,
                duration = if (episode.duration > 0) episode.duration * 1000L else 0L
            )
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        _playerState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _playerState.update {
                    it.copy(
                        currentPosition = exoPlayer.currentPosition,
                        bufferedPosition = exoPlayer.bufferedPosition
                    )
                }
                delay(200)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }
}
