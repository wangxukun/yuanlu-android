package com.wxkzd.yuanlu.data.remote.dto

import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import kotlinx.serialization.Serializable

// ---------- 个人中心（对齐 Web /auth/personal-center 的四个数据接口） ----------

/** GET api/user/stats/overview：裸对象（totalHours 后端 toFixed(1) 保留一位小数） */
@Serializable
data class ProfileStatsDto(
    val totalHours: Double = 0.0,
    val streakDays: Int = 0,
    val wordsLearned: Int = 0,
    val speechEvalCount: Int = 0,
    val speechHighScoreCount: Int = 0
) {
    fun toDomain() = ProfileStats(
        totalHours = totalHours,
        streakDays = streakDays,
        wordsLearned = wordsLearned,
        speechEvalCount = speechEvalCount,
        speechHighScoreCount = speechHighScoreCount
    )
}

/** GET api/user/stats/weekly-activity：信封 { weeklyActivity: [{ day, minutes }] } */
@Serializable
data class WeeklyActivityResponseDto(
    val weeklyActivity: List<WeeklyActivityItemDto> = emptyList()
)

@Serializable
data class WeeklyActivityItemDto(
    val day: String = "",
    val minutes: Int = 0
) {
    fun toDomain() = WeeklyActivityItem(day = day, minutes = minutes)
}

/** GET api/user/achievements：裸数组 */
@Serializable
data class AchievementItemDto(
    val key: String = "",
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val unlocked: Boolean = false,
    val unlockedAt: String? = null
) {
    fun toDomain() = AchievementItem(
        key = key,
        name = name,
        description = description,
        icon = icon,
        unlocked = unlocked,
        unlockedAt = unlockedAt
    )
}

/** PUT api/user/profile 的响应信封：data = 更新后的 user_profile 行（签名头像） */
@Serializable
data class ProfileUpdateResponseDto(
    val success: Boolean = false,
    val data: UserProfileDto? = null,
    val error: String? = null
)

// ---------- 每日学习时长心跳（对齐 Web GlobalAudio 批量上报） ----------

/** POST api/auth/update-activity 请求体：seconds = 增量收听秒数（服务端取整累加） */
@Serializable
data class UpdateActivityRequestDto(
    val seconds: Int
)

/** 裸 { ok: true } 响应 */
@Serializable
data class UpdateActivityResponseDto(
    val ok: Boolean = false
)
