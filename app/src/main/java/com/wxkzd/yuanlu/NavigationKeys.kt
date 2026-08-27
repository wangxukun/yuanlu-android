package com.wxkzd.yuanlu

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class PodcastDetailNav(val podcastid: String) : NavKey

/** 频道名可能含空格等字符，作为 encoded path segment 传递 */
@Serializable data class ChannelNav(val name: String) : NavKey

@Serializable data class PlayerNav(val episodeid: String) : NavKey
