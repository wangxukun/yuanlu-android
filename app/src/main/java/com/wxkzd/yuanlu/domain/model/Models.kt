package com.wxkzd.yuanlu.domain.model

data class Tag(
    val id: Int,
    val name: String
)

data class SubtitleWord(
    val word: String,
    val start: Double,
    val end: Double
)

/**
 * 与后端 /api/episode/subtitles 返回的归一化字幕一一对应：
 * start/end 为秒，words 供词级高亮（M2 已消费，M6 评测复用）。
 */
data class Subtitle(
    val id: Int,
    val textEn: String,
    val textCn: String?,
    val start: Double,
    val end: Double,
    val speaker: String? = null,
    val words: List<SubtitleWord>? = null
)

data class Episode(
    val episodeid: String,
    val title: String,
    val description: String? = null,
    val coverUrl: String? = null,
    val audioUrl: String? = null,
    val duration: Int = 0,
    val playCount: Int = 0,
    val publishAt: String? = null,
    val status: String? = null,
    val isExclusive: Boolean = false,
    val isCommentEnabled: Boolean = true,
    val difficulty: String? = null,
    val podcastid: String? = null,
    val podcastTitle: String? = null,
    val tags: List<Tag> = emptyList(),
    val isFavorited: Boolean = false,
    val progressSeconds: Int = 0,
    val isFinished: Boolean = false
)

data class Podcast(
    val podcastid: String,
    val title: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val platform: String? = null,
    val isEditorPick: Boolean = false,
    val followerCount: Int = 0,
    val totalPlays: Int = 0,
    val episodeCount: Int = 0,
    val createAt: String? = null,
    val tags: List<Tag> = emptyList()
)

data class PodcastDetail(
    val podcast: Podcast,
    val isFavorited: Boolean,
    val channelPodcasts: List<Podcast>
)

data class EpisodePage(
    val episodes: List<Episode>,
    val total: Int,
    val hasMore: Boolean
)

data class ChannelData(
    val platformName: String,
    val podcastCount: Int,
    val topShows: List<Podcast>,
    val topEpisodes: List<Episode>
)

data class SubtitleBundle(
    val subtitles: List<Subtitle>,
    val audioUrl: String?
)
