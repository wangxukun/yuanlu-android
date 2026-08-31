package com.wxkzd.yuanlu.core.media

import com.wxkzd.yuanlu.domain.model.Episode

/** 循环模式（对齐 Web player-store 的 loopMode） */
enum class LoopMode { NONE, ALL, ONE }

/** 定时关闭配置（全屏播放器「定时」弹层选项，对齐截图口径） */
sealed interface SleepConfig {
    /** N 分钟后停止 */
    data class Minutes(val minutes: Int) : SleepConfig
    /** 播完 N 集后停止 */
    data class Episodes(val count: Int) : SleepConfig
    /** 播完整集声音再停止 */
    data object EpisodeEnd : SleepConfig
}

/** 激活中的定时关闭任务（label 供迷你条/弹层展示） */
data class SleepTimer(
    val config: SleepConfig,
    val label: String,
    /** 按时间任务的截止时刻（SystemClock.elapsedRealtime 基准），按集数为 null */
    val endsAtElapsedMs: Long? = null
)

/** 定时配置的展示文案（「上次定时 播完2集后关闭」等） */
fun SleepConfig.describe(): String = when (this) {
    is SleepConfig.Minutes -> "${minutes}分钟后关闭"
    is SleepConfig.Episodes -> if (count <= 1) "播完本集后关闭" else "播完${count}集后关闭"
    SleepConfig.EpisodeEnd -> "播完整集后关闭"
}

data class PlayerState(
    val currentEpisode: Episode? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bufferedPosition: Long = 0L,
    val playbackState: Int = 1, // Player.STATE_IDLE
    /**
     * 精听模式标记：由「开始精听」入口（剧集详情页 / 全屏播放器精听按钮）起播时置位，
     * 迷你播放条与全屏播放器据此显示精听模式指示器。普通起播会复位该标记。
     */
    val isIntensiveMode: Boolean = false,
    /** 播放倍速（Web 端 cyclePlaybackRate 口径：1 / 1.25 / 1.5 / 2 / 0.75 循环） */
    val playbackRate: Float = 1f,
    /** 循环模式：不循环 / 列表循环 / 单曲循环 */
    val loopMode: LoopMode = LoopMode.NONE,
    /** 激活中的定时关闭任务；null 表示未开启 */
    val sleepTimer: SleepTimer? = null,
    /** 上次使用的定时配置（弹层「上次定时」行 + Switch 快捷重开） */
    val lastSleepConfig: SleepConfig? = null
)
