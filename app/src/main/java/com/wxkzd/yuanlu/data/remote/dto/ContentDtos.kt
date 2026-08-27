package com.wxkzd.yuanlu.data.remote.dto

import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.SubtitleWord
import com.wxkzd.yuanlu.domain.model.Tag
import kotlinx.serialization.Serializable

/**
 * DTO 与后端 Next.js 路由的 JSON 一一映射（字段名保持原样）。
 * 后端部分接口返回裸数组、部分返回 { success, data } 信封：
 * - 裸数组端点直接用 List<XxxDto> / XxxDto 建模
 * - 信封端点复用 ApiResponse<T>
 * Json 配置了 ignoreUnknownKeys，多余字段自动忽略。
 */

@Serializable
data class TagDto(
    val id: Int,
    val name: String
) {
    fun toDomain() = Tag(id, name)
}

@Serializable
data class SubtitleWordDto(
    val word: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0
) {
    fun toDomain() = SubtitleWord(word, start, end)
}

@Serializable
data class SubtitleDto(
    val id: Int = 0,
    val start: Double = 0.0,
    val end: Double = 0.0,
    val speaker: String? = null,
    val textEn: String = "",
    val textCn: String? = null,
    val words: List<SubtitleWordDto>? = null
) {
    fun toDomain() = Subtitle(
        id = id,
        textEn = textEn,
        textCn = textCn,
        start = start,
        end = end,
        speaker = speaker,
        words = words?.map { it.toDomain() }
    )
}

/** /api/episode/subtitles 的响应：信封外多一个 audioUrl（登录后签发的 OSS 直链） */
@Serializable
data class SubtitlesResponseDto(
    val success: Boolean = true,
    val data: List<SubtitleDto> = emptyList(),
    val audioUrl: String? = null
)

@Serializable
data class PodcastRefDto(
    val podcastid: String = "",
    val title: String? = null
)

@Serializable
data class EpisodeUserStateDto(
    val progressSeconds: Int? = null,
    val isFinished: Boolean? = null,
    val lastListenAt: String? = null,
    val isFavorited: Boolean? = null
)

@Serializable
data class EpisodeDto(
    val episodeid: String = "",
    val title: String = "",
    val description: String? = null,
    val coverUrl: String? = null,
    val coverFileName: String? = null,
    val audioUrl: String? = null,
    val audioFileName: String? = null,
    val duration: Int? = null,
    val playCount: Int? = null,
    val publishAt: String? = null,
    val status: String? = null,
    val isExclusive: Boolean? = null,
    val isCommentEnabled: Boolean? = null,
    val difficulty: String? = null,
    val podcastid: String? = null,
    val podcast: PodcastRefDto? = null,
    val tags: List<TagDto>? = null,
    val isFavorited: Boolean? = null,
    val progressSeconds: Int? = null,
    val isFinished: Boolean? = null,
    val userState: EpisodeUserStateDto? = null
) {
    fun toDomain() = Episode(
        episodeid = episodeid,
        title = title,
        description = description,
        coverUrl = coverUrl,
        audioUrl = audioUrl,
        duration = duration ?: 0,
        playCount = playCount ?: 0,
        publishAt = publishAt,
        status = status,
        isExclusive = isExclusive ?: false,
        isCommentEnabled = isCommentEnabled ?: true,
        difficulty = difficulty,
        podcastid = podcastid ?: podcast?.podcastid,
        podcastTitle = podcast?.title,
        tags = tags.orEmpty().map { it.toDomain() },
        isFavorited = isFavorited ?: userState?.isFavorited ?: false,
        progressSeconds = progressSeconds ?: userState?.progressSeconds ?: 0,
        isFinished = isFinished ?: userState?.isFinished ?: false
    )
}

@Serializable
data class PodcastDto(
    val podcastid: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val coverFileName: String? = null,
    val description: String? = null,
    val platform: String? = null,
    val isEditorPick: Boolean? = null,
    val followerCount: Int? = null,
    val totalPlays: Int? = null,
    val episodeCount: Int? = null,
    val createAt: String? = null,
    val tags: List<TagDto>? = null
) {
    fun toDomain() = Podcast(
        podcastid = podcastid,
        title = title,
        coverUrl = coverUrl,
        description = description,
        platform = platform,
        isEditorPick = isEditorPick ?: false,
        followerCount = followerCount ?: 0,
        totalPlays = totalPlays ?: 0,
        episodeCount = episodeCount ?: 0,
        createAt = createAt,
        tags = tags.orEmpty().map { it.toDomain() }
    )
}

/** /api/podcast/detail 返回播客字段 + episode[] + channelPodcasts[] 的扁平结构 */
@Serializable
data class PodcastDetailDto(
    val podcastid: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val coverFileName: String? = null,
    val description: String? = null,
    val platform: String? = null,
    val isEditorPick: Boolean? = null,
    val followerCount: Int? = null,
    val totalPlays: Int? = null,
    val tags: List<TagDto>? = null,
    val isFavorited: Boolean? = null,
    val channelPodcasts: List<PodcastDto>? = null
) {
    fun toDomain() = PodcastDetail(
        podcast = PodcastDto(
            podcastid = podcastid,
            title = title,
            coverUrl = coverUrl,
            description = description,
            platform = platform,
            isEditorPick = isEditorPick,
            followerCount = followerCount,
            totalPlays = totalPlays,
            tags = tags
        ).toDomain(),
        isFavorited = isFavorited ?: false,
        channelPodcasts = channelPodcasts.orEmpty().map { it.toDomain() }
    )
}

/** /api/episode/list-by-podcastid 的 data 部分 */
@Serializable
data class EpisodePageDto(
    val episodes: List<EpisodeDto> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false
) {
    fun toDomain() = EpisodePage(
        episodes = episodes.map { it.toDomain() },
        total = total,
        hasMore = hasMore
    )
}

/** /api/channel/[name] 的 data 部分 */
@Serializable
data class ChannelDataDto(
    val platformName: String = "",
    val podcastCount: Int = 0,
    val topShows: List<PodcastDto> = emptyList(),
    val topEpisodes: List<EpisodeDto> = emptyList()
) {
    fun toDomain() = ChannelData(
        platformName = platformName,
        podcastCount = podcastCount,
        topShows = topShows.map { it.toDomain() },
        topEpisodes = topEpisodes.map { it.toDomain() }
    )
}

fun SubtitlesResponseDto.toBundle() = SubtitleBundle(
    subtitles = data.map { it.toDomain() },
    audioUrl = audioUrl
)
