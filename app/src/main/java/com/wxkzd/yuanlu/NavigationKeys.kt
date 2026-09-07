package com.wxkzd.yuanlu

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class PodcastDetailNav(val podcastid: String) : NavKey

/** 频道名可能含空格等字符，作为 encoded path segment 传递 */
@Serializable data class ChannelNav(val name: String) : NavKey

@Serializable data class PlayerNav(val episodeid: String) : NavKey

/**
 * 精听页：由全屏播放器「精听模式」按钮进入，
 * 携带当前 episodeId + playbackPosition（ms），进入后同步接续播放。
 */
@Serializable data class IntensiveListeningNav(
    val episodeid: String,
    val positionMs: Long = 0L
) : NavKey

@Serializable data object ChannelListNav : NavKey

/** 个人中心（旅程数据/里程碑/最近听过/账号与安全），需登录 */
@Serializable data object PersonalCenterNav : NavKey

/** 我的收藏（播客系列/单集双 Tab，复刻 Web /library/favorites），需登录 */
@Serializable data object FavoritesNav : NavKey

/** 收听历史（过滤/时间分组/断点续播入口，复刻 Web /library/history），需登录 */
@Serializable data object ListeningHistoryNav : NavKey


/** 语音评测：由剧集详情「语音评测」按钮进入，携带当前 episodeId */
@Serializable data class SpeechEvalNav(val episodeid: String) : NavKey
