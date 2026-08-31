package com.wxkzd.yuanlu.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.media.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 全局播放壳 ViewModel：挂在 AppNavHost（Activity 作用域），跨导航页共享。
 *
 * 播放状态与迷你播放条可见性的唯一事实来源是单例 [PlayerController]（Media3 ExoPlayer +
 * StateFlow）：currentEpisode 非空即视为「有正在收听的音轨」，迷你条 Visible/Gone 由该状态
 * 派生，因此任意导航切换（Tab / 详情页 / 频道页 / 精听页）之间浮条状态天然一致。
 * 该 ViewModel 只做委托，保证 UI 层不直接触碰单例。
 */
@HiltViewModel
class PlayerShellViewModel @Inject constructor(
    private val playerController: PlayerController
) : ViewModel() {

    /** 播放状态流（当前剧集 / 播放中 / 进度 / 时长 / 精听标记） */
    val playerState: StateFlow<PlayerState> = playerController.playerState

    /** 迷你播放条可见性：有当前剧集即显示（全局导航切换间保持一致） */
    val showMiniPlayer: StateFlow<Boolean> = playerController.playerState
        .map { it.currentEpisode != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun togglePlayPause() = playerController.togglePlayPause()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    fun seekBy(deltaMs: Long) = playerController.seekBy(deltaMs)

    /** 关闭播放器：停止播放并隐藏迷你条（对齐 Web closePlayer） */
    fun stop() = playerController.stop()

    /** 倍速循环切换（1 / 1.25 / 1.5 / 2 / 0.75） */
    fun cyclePlaybackRate() = playerController.cyclePlaybackRate()

    /** 循环模式切换（不循环 / 列表 / 单曲） */
    fun toggleLoopMode() = playerController.toggleLoopMode()

    /** 应用定时关闭配置（按时间 / 按集数 / 播完本集） */
    fun applySleepConfig(config: com.wxkzd.yuanlu.core.media.SleepConfig) =
        playerController.applySleepConfig(config)

    /** 取消定时关闭 */
    fun cancelSleepTimer() = playerController.cancelSleepTimer()
}
