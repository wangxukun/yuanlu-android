package com.wxkzd.yuanlu.data.remote.dto

import com.wxkzd.yuanlu.domain.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val type: String, // "sms" or "password"
    val phone: String? = null,
    val code: String? = null,
    val email: String? = null,
    val password: String? = null
)

@Serializable
data class LoginResponseData(
    val token: String
)

@Serializable
data class SmsSendRequest(
    val phone: String,
    val scene: String = "LOGIN"
)

/**
 * POST api/auth/sms/send 的响应：业务失败也返回 HTTP 200，
 * 由 requireCaptcha 标识阿里云滑块风控（原生端降级提示改用邮箱登录）。
 */
@Serializable
data class SmsSendResponse(
    val success: Boolean = false,
    val code: Int? = null,
    val error: String? = null,
    val requireCaptcha: Boolean = false
)

/** GET api/user/profile 中嵌套的 User 标量字段（其余字段忽略） */
@Serializable
data class UserRefDto(
    val userid: String = "",
    val email: String = "",
    val phone: String? = null,
    val role: String? = null,
    val createAt: String? = null
)

/** GET api/user/profile：user_profile 行 + 签名头像 + 嵌套 User */
@Serializable
data class UserProfileDto(
    val userid: String = "",
    val nickname: String? = null,
    val bio: String? = null,
    val learnLevel: String? = null,
    val avatarUrl: String? = null,
    val avatarFileName: String? = null,
    val dailyStudyGoalMins: Int? = null,
    val weeklyListeningGoalHours: Int? = null,
    val weeklyWordsGoal: Int? = null,
    @SerialName("User") val user: UserRefDto? = null
) {
    fun toDomain() = UserProfile(
        userid = user?.userid ?: userid,
        nickname = nickname,
        avatarUrl = avatarUrl,
        bio = bio,
        learnLevel = learnLevel,
        email = user?.email,
        phone = user?.phone,
        role = user?.role,
        createAt = user?.createAt
    )
}
