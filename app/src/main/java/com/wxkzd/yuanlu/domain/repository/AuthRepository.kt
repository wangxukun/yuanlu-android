package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.UserProfile

/** 短信验证码发送结果：requireCaptcha=true 表示触发阿里云滑块风控 */
data class SmsSendStatus(
    val requireCaptcha: Boolean = false
)

interface AuthRepository {
    suspend fun loginWithPassword(email: String, password: String): Result<Unit>
    suspend fun loginWithSms(phone: String, code: String): Result<Unit>
    suspend fun logout()

    /** 发送登录短信验证码（60s 限频由后端控制） */
    suspend fun sendSmsCode(phone: String): Result<SmsSendStatus>

    /** 当前登录用户资料 */
    suspend fun getProfile(): Result<UserProfile>
}
