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

/** 学习路径列表（我的集合/发现双 Tab + 搜索 + 创建，复刻 Web /library/learning-paths），需登录 */
@Serializable data object LearningPathsNav : NavKey

/** 学习路径详情（播放全部/剧集清单，拥有者可编辑/删除/添加/移除剧集），携带路径 id */
@Serializable data class LearningPathDetailNav(val pathId: Int) : NavKey


/**
 * 语音评测：由剧集详情「语音评测」按钮进入，携带当前 episodeId；
 * 发音弱项本的弱项句子卡片进入时额外携带 subtitleId，
 * 直接定位该句录音卡（对齐 Web /episode/{id}?practice=true&subtitleId= 参数）。
 */
@Serializable data class SpeechEvalNav(
    val episodeid: String,
    val subtitleId: Int? = null
) : NavKey

/** 发音弱项本主页（能力画像/音素诊断/弱项列表，复刻 Web /library/pronunciation），需登录 */
@Serializable data object PronunciationNotebookNav : NavKey

/** 发音闯关复习（逐题录音评测流转，复刻 Web /library/pronunciation/practice），PRO 会员功能 */
@Serializable data object WeaknessPracticeNav : NavKey

/** 发音达人榜（周期×维度双 Tab 排行，复刻 Web /library/pronunciation/leaderboard），需登录 */
@Serializable data object SpeechLeaderboardNav : NavKey
