package com.wxkzd.yuanlu.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    val playerState = playerController.playerState

    // Mock subtitles for testing
    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()

    // Calculated active indices based on current position
    val activeSubtitleIndex: StateFlow<Int> = combine(
        playerController.playerState.map { it.currentPosition },
        _subtitles
    ) { positionMs, subs ->
        val positionSec = positionMs / 1000.0
        subs.indexOfFirst { positionSec >= it.startSeconds && positionSec <= it.endSeconds }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = -1
    )

    init {
        // Load mock episode and subtitles
        val mockEpisode = Episode(
            episodeid = "ep_1",
            title = "Welcome to Yuanlu",
            coverUrl = "https://example.com/cover.jpg",
            audioUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            duration = 372
        )
        
        _subtitles.value = listOf(
            Subtitle(1, "Welcome to Yuanlu.", "欢迎来到远路播客。", 0.0, 5.0),
            Subtitle(2, "This is a mock audio stream.", "这是一个模拟音频流。", 5.5, 10.0),
            Subtitle(3, "You can see subtitles sync here.", "你可以在这里看到字幕同步。", 10.5, 15.0)
        )
        
        playerController.play(mockEpisode)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekToSubtitle(subtitle: Subtitle) {
        playerController.seekTo((subtitle.startSeconds * 1000).toLong())
    }
}
