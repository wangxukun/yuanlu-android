package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 学习路径相关端点的响应建模，数据结构与 Web core/learning-path/learning-path.service
 * 的 listWithDetails / listPublic / getById / searchForLearningPath 一一映射。
 *
 * Web 端这些能力走 Server Action（无 REST 路由），Android 侧按下表约定 REST 契约
 * （信封统一 { success, data, message }），后端用 learning-path.service 直接包装即可：
 *
 * - GET    api/learning-paths/mine                 -> data = List<LearningPathSummaryDto>
 * - GET    api/learning-paths/public               -> data = List<LearningPathSummaryDto>
 * - POST   api/learning-paths                      (body: LearningPathUpsertRequestDto)
 * - GET    api/learning-paths/{pathid}             -> data = LearningPathDetailDto
 * - PATCH  api/learning-paths/{pathid}             (body: LearningPathUpsertRequestDto)
 * - DELETE api/learning-paths/{pathid}
 * - POST   api/learning-paths/{pathid}/episodes    (body: AddEpisodeToPathRequestDto)
 * - DELETE api/learning-paths/{pathid}/episodes/{itemId}
 * - GET    api/episode/search-for-path?query=      -> data = List<PathEpisodeSearchDto>
 *
 * 业务失败（如重复添加剧集）以 200 + success=false + message 返回；401/403 走 HTTP 状态码。
 */

/** GET api/learning-paths/mine 与 /public：信封 { success, data: LearningPathSummaryDto[] } */
@Serializable
data class LearningPathsResponseDto(
    val success: Boolean = false,
    val data: List<LearningPathSummaryDto> = emptyList(),
    val message: String? = null
)

@Serializable
data class LearningPathSummaryDto(
    val pathid: Int = 0,
    val pathName: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val itemCount: Int = 0,
    /** 第一集封面的签名 URL（空路径为 null，UI 回退标题首字母占位） */
    val coverUrl: String? = null,
    val creatorName: String = "",
    val creationAt: String? = null,
    /** 已听完集数占比 0..100（服务端按 listening_history 聚合） */
    val progress: Int = 0,
    val isOfficial: Boolean = false
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.LearningPathSummary(
        pathid = pathid,
        pathName = pathName,
        description = description,
        coverUrl = coverUrl,
        isPublic = isPublic,
        itemCount = itemCount,
        creatorName = creatorName,
        creationAt = creationAt,
        progress = progress.coerceIn(0, 100),
        isOfficial = isOfficial
    )
}

/** GET api/learning-paths/{pathid}：信封 { success, data: LearningPathDetailDto } */
@Serializable
data class LearningPathDetailResponseDto(
    val success: Boolean = false,
    val data: LearningPathDetailDto? = null,
    val message: String? = null
)

@Serializable
data class LearningPathDetailDto(
    val pathid: Int = 0,
    /** 创建者 userid（服务端回传，客户端与当前登录用户比对得出 isOwner） */
    val userid: String? = null,
    val pathName: String = "",
    val description: String? = null,
    val isPublic: Boolean = false,
    val creationAt: String? = null,
    /** 第一集封面签名 URL（无剧集为 null） */
    val coverUrl: String? = null,
    val creatorName: String = "",
    val items: List<LearningPathItemDto> = emptyList()
) {
    fun toDomain(isOwner: Boolean) = com.wxkzd.yuanlu.domain.model.LearningPathDetail(
        pathid = pathid,
        pathName = pathName,
        description = description,
        coverUrl = coverUrl,
        isPublic = isPublic,
        userid = userid,
        creatorName = creatorName,
        creationAt = creationAt,
        items = items.map { it.toDomain() },
        isOwner = isOwner
    )
}

@Serializable
data class LearningPathItemDto(
    /** learning_path_items.id：拥有者从路径移除剧集时用 */
    val id: Int = 0,
    val episodeid: String = "",
    /** 路径内顺序（服务端按 order 升序返回） */
    val order: Int = 0,
    val addedAt: String? = null,
    /** 剧集快照：封面/音频均已签名，progressSeconds/isFinished 为当前用户收听态 */
    val episode: EpisodeDto? = null
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.LearningPathEpisode(
        itemId = id,
        order = order,
        episode = episode?.toDomain()
            ?: com.wxkzd.yuanlu.domain.model.Episode(episodeid = episodeid, title = "")
    )
}

/** 创建/编辑路径的请求体（字段对齐 Web CreateLearningPathSchema） */
@Serializable
data class LearningPathUpsertRequestDto(
    val pathName: String,
    val description: String? = null,
    val isPublic: Boolean = false
)

/** 添加剧集到路径的请求体 */
@Serializable
data class AddEpisodeToPathRequestDto(
    val episodeid: String
)

/** 变更类端点（创建/编辑/删除/添加/移除剧集）的信封响应 */
@Serializable
data class LearningPathMutationResponseDto(
    val success: Boolean = false,
    val message: String? = null
)

/** GET api/episodes/search-for-path：信封 { success, data: PathEpisodeSearchDto[] } */
@Serializable
data class PathSearchResponseDto(
    val success: Boolean = false,
    val data: List<PathEpisodeSearchDto> = emptyList(),
    val message: String? = null
)

/** 添加剧集弹窗的搜索结果（对齐 Web episodeService.searchForLearningPath，最多 20 条） */
@Serializable
data class PathEpisodeSearchDto(
    val id: String = "",
    val title: String = "",
    val thumbnailUrl: String? = null,
    /** 所属播客名 */
    val author: String = "",
    val duration: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem(
        episodeid = id,
        title = title,
        thumbnailUrl = thumbnailUrl,
        author = author,
        duration = duration
    )
}
