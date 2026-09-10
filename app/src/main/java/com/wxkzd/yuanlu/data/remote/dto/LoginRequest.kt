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

/**
 * scene 不给默认值：NetworkModule 的 Json 未开 encodeDefaults（kotlinx 默认 false），
 * 带默认值的属性序列化时会被整体省略，导致后端 /api/auth/sms/send 因缺 scene
 * 返回「参数不完整」。调用方必须显式传参。
 */
@Serializable
data class SmsSendRequest(
    val phone: String,
    val scene: String
)

// ---------- 邮箱注册（对齐 Web /api/auth/sign-up 三步流程） ----------

/** POST api/auth/send-verification-code：发送邮箱注册验证码（5 分钟有效） */
@Serializable
data class EmailCodeSendRequest(val email: String)

/** POST api/auth/verify-code：校验邮箱验证码 */
@Serializable
data class EmailCodeVerifyRequest(val email: String, val code: String)

/** POST api/auth/sign-up：邮箱 + 密码创建账号 */
@Serializable
data class SignUpRequest(val email: String, val password: String)

/** 注册三接口的裸响应 { success, message }；业务失败随 4xx/5xx 由 errorMessage() 解析 message */
@Serializable
data class AuthActionResponse(
    val success: Boolean = false,
    val message: String? = null
)

/**
 * POST api/auth/sms/send 的响应：业务失败也返回 HTTP 200，
 * 由 requireCaptcha 标识阿里云滑块风控（原生端降级提示改用邮箱登录）。
 * code 为后端业务标识字符串（RATE_LIMITED/CAPTCHA_REQUIRED/SEND_FAILED），非 HTTP 状态码。
 */
@Serializable
data class SmsSendResponse(
    val success: Boolean = false,
    val code: String? = null,
    val error: String? = null,
    val requireCaptcha: Boolean = false
)

// ---------- 账号与安全（对齐 Web AccountSecurityTab 的绑定/注销流程） ----------

/** POST api/auth/sms/bind：绑定手机号（scene=BIND 验证码） */
@Serializable
data class BindPhoneRequest(val phone: String, val code: String)

/** POST api/auth/bind-email/send：发送绑定邮箱验证码（5 分钟有效） */
@Serializable
data class BindEmailCodeRequest(val email: String)

/** POST api/auth/bind-email/confirm：绑定邮箱并写入登录密码 */
@Serializable
data class BindEmailConfirmRequest(val email: String, val code: String, val password: String)

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
        avatarFileName = avatarFileName,
        bio = bio,
        learnLevel = learnLevel,
        email = user?.email,
        phone = user?.phone,
        role = user?.role,
        createAt = user?.createAt,
        dailyStudyGoalMins = dailyStudyGoalMins,
        weeklyListeningGoalHours = weeklyListeningGoalHours,
        weeklyWordsGoal = weeklyWordsGoal
    )
}
