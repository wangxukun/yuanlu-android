package com.wxkzd.yuanlu.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    /** 音频直链可用，正常播放 */
    data class Ready(val episode: Episode) : PlayerUiState

    /** 未登录 / 无权限：字幕仅 3 分钟预览，无音频直链 */
    data class LoginRequired(val episode: Episode) : PlayerUiState

    data class Error(val message: String) : PlayerUiState
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val contentRepository: ContentRepository
) : ViewModel() {

    val playerState = playerController.playerState

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()

    val activeSubtitleIndex: StateFlow<Int> = combine(
        playerController.playerState.map { it.currentPosition },
        _subtitles
    ) { positionMs, subs ->
        val positionSec = positionMs / 1000.0
        subs.indexOfFirst { positionSec >= it.start && positionSec <= it.end }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = -1
    )

    private var loadedEpisodeId: String? = null

    /**
     * Nav3 entry 不做 SavedStateHandle 参数注入，由路由层在 LaunchedEffect 中调用；
     * 同一剧集重复调用幂等。
     */
    fun load(episodeid: String) {
        if (loadedEpisodeId == episodeid) return
        loadedEpisodeId = episodeid
        _uiState.value = PlayerUiState.Loading
        _subtitles.value = emptyList()

        viewModelScope.launch {
            // 字幕接口一次性给出归一化字幕与登录后签发的音频直链
            val subtitlesResult = contentRepository.getSubtitles(episodeid)
            val episodeResult = contentRepository.getEpisode(episodeid)

            val episode = when (episodeResult) {
                is Result.Success -> episodeResult.data
                is Result.Error -> {
                    _uiState.value = PlayerUiState.Error(episodeResult.message)
                    return@launch
                }
                Result.NetworkError -> {
                    _uiState.value = PlayerUiState.Error("Network connection failed")
                    return@launch
                }
            }

            when (subtitlesResult) {
                is Result.Success -> {
                    val bundle = subtitlesResult.data
                    _subtitles.value = bundle.subtitles
                    val audioUrl = bundle.audioUrl
                    if (audioUrl.isNullOrBlank()) {
                        _uiState.value = PlayerUiState.LoginRequired(episode)
                    } else {
                        _uiState.value = PlayerUiState.Ready(episode)
                        playerController.play(
                            episode.copy(audioUrl = audioUrl),
                            startPositionMs = resumePositionMs(episode)
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.value = PlayerUiState.Error(subtitlesResult.message)
                }
                Result.NetworkError -> {
                    _uiState.value = PlayerUiState.Error("Network connection failed")
                }
            }
        }
    }

    fun retry() {
        loadedEpisodeId?.let { load(it) }
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        playerController.seekBy(deltaMs)
    }

    fun seekToSubtitle(subtitle: Subtitle) {
        playerController.seekTo((subtitle.start * 1000).toLong())
    }

    /** 断点续播：跳过开头 30s 内的进度，接近结尾视为已听完从头播 */
    private fun resumePositionMs(episode: Episode): Long {
        val progress = episode.progressSeconds
        if (progress <= 30) return 0L
        val duration = episode.duration
        if (duration > 0 && progress >= duration - 15) return 0L
        return progress * 1000L
    }
}
