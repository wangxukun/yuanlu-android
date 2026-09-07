package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * 收听历史相关端点的响应建模（后端 yuanlu 仓库 app/api/user/history）。
 * 数据结构与 Web core/listening-history/dto.ts 的 ListeningHistoryItem 一一映射：
 * author=平台名，category=所属播客名，duration 服务端已格式化为 "M:SS"。
 */

/** GET api/user/history：信封 { success, data: { items, total, hasMore } }（需登录） */
@Serializable
data class HistoryResponseDto(
    val success: Boolean = false,
    val data: HistoryPageDto? = null,
    val message: String? = null
)

@Serializable
data class HistoryPageDto(
    val items: List<HistoryItemDto> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.HistoryPage(
        items = items.map { it.toDomain() },
        total = total,
        hasMore = hasMore
    )
}

@Serializable
data class HistoryItemDto(
    val historyid: Int = 0,
    /** ISO 时间串（UTC），驱动 今天/昨天/更早 分组 */
    val listenAt: String = "",
    /**
     * 后端 Prisma Float（如 101.767）——必须是 Double，Int 遇小数会解析失败
     * （同 EpisodeUserStateDto 的注释警告），domain 侧再四舍五入成整秒
     */
    val progressSeconds: Double = 0.0,
    val isFinished: Boolean = false,
    val episode: HistoryEpisodeDto? = null
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.HistoryItem(
        historyid = historyid,
        listenAt = listenAt,
        progressSeconds = kotlin.math.round(progressSeconds).toInt(),
        isFinished = isFinished,
        episode = episode?.toDomain() ?: com.wxkzd.yuanlu.domain.model.HistoryEpisode()
    )
}

@Serializable
data class HistoryEpisodeDto(
    val id: String = "",
    val title: String = "",
    /** 所属播客平台 */
    val author: String = "",
    /** 所属播客名 */
    val category: String = "",
    val thumbnailUrl: String? = null,
    /** 服务端已格式化的 "M:SS" */
    val duration: String = "",
    val durationSeconds: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.HistoryEpisode(
        id = id,
        title = title,
        author = author,
        category = category,
        thumbnailUrl = thumbnailUrl,
        duration = duration,
        durationSeconds = durationSeconds
    )
}
