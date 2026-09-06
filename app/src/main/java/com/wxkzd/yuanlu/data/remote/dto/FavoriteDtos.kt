package com.wxkzd.yuanlu.data.remote.dto

import com.wxkzd.yuanlu.domain.model.FavoritesBundle
import kotlinx.serialization.Serializable

/**
 * 收藏相关端点的响应建模（后端 yuanlu 仓库 app/api 下的 podcast/episode favorite 系列路由与 app/api/user/favorites）。
 * 后端 insert/delete 读取 FormData（Android 以 application/x-www-form-urlencoded 发送，
 * Next.js request.formData() 同样可解析）；find-unique 用 success 布尔表达"是否已收藏"。
 */

/** 裸 { success, message?, status? }：收藏新增/取消的通用响应 */
@Serializable
data class FavoriteMutationDto(
    val success: Boolean = false,
    val message: String? = null
)

/** GET api/user/favorites：信封 { success, data: { podcasts, episodes } }（需登录） */
@Serializable
data class FavoritesResponseDto(
    val success: Boolean = false,
    val data: FavoritesDataDto? = null,
    val message: String? = null
)

@Serializable
data class FavoritesDataDto(
    val podcasts: List<FavoriteSeriesDto> = emptyList(),
    val episodes: List<FavoriteEpisodeDto> = emptyList()
) {
    fun toDomain() = FavoritesBundle(
        podcasts = podcasts.map { it.toDomain() },
        episodes = episodes.map { it.toDomain() }
    )
}

/** 收藏的播客系列（对齐 Web FavoriteSeries：author=platform，封面已签名） */
@Serializable
data class FavoriteSeriesDto(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val thumbnailUrl: String? = null,
    val category: List<TagDto> = emptyList(),
    val episodeCount: Int = 0,
    val plays: Int = 0,
    val followers: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.FavoriteSeries(
        id = id,
        title = title,
        author = author,
        thumbnailUrl = thumbnailUrl,
        category = category.map { it.toDomain() },
        episodeCount = episodeCount,
        plays = plays,
        followers = followers
    )
}

/** 收藏的单集（对齐 Web FavoriteEpisode：category=播客名，date/duration 服务端已格式化） */
@Serializable
data class FavoriteEpisodeDto(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    /** 所属播客平台（移动端卡片展示；可能为空） */
    val platform: String = "",
    val thumbnailUrl: String? = null,
    val category: String = "",
    val date: String = "",
    val duration: String = "",
    val playCount: Int = 0,
    /** 该单集的总收藏数（服务端 _count.episode_favorites 聚合） */
    val favoriteCount: Int = 0,
    val podcastId: String = ""
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.FavoriteEpisode(
        id = id,
        title = title,
        author = author,
        platform = platform,
        thumbnailUrl = thumbnailUrl,
        category = category,
        date = date,
        duration = duration,
        playCount = playCount,
        favoriteCount = favoriteCount,
        podcastId = podcastId
    )
}
