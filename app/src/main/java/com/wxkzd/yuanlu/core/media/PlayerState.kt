package com.wxkzd.yuanlu.core.media

import com.wxkzd.yuanlu.domain.model.Episode

data class PlayerState(
    val currentEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackState: Int = 1 // Player.STATE_IDLE
)
