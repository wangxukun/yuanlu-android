package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.auth.SessionClaims
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.remote.dto.BindEmailCodeRequest
import com.wxkzd.yuanlu.data.remote.dto.BindEmailConfirmRequest
import com.wxkzd.yuanlu.data.remote.dto.BindPhoneRequest
import com.wxkzd.yuanlu.data.remote.dto.EmailCodeSendRequest
import com.wxkzd.yuanlu.data.remote.dto.EmailCodeVerifyRequest
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.data.remote.dto.SignUpRequest
import com.wxkzd.yuanlu.data.remote.dto.SmsSendRequest
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.SmsSendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: AuthApi,
    private val tokenStore: TokenStore
) : AuthRepository {

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.login(
                    LoginRequest(type = "password", email = email, password = password)
                )
                if (response.success && response.data != null) {
                    tokenStore.saveToken(response.data.token)
                    Result.Success(Unit)
                } else {
                    Result.Error(response.code ?: 400, response.error ?: "登录失败，请稍后重试")
                }
            } catch (e: HttpException) {
                // 后端登录失败返回 4xx + { success:false, error:"邮箱或密码错误" }，透出真实文案
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "登录失败")
            }
        }
    }

    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.login(
                    LoginRequest(type = "sms", phone = phone, code = code)
                )
                if (response.success && response.data != null) {
                    tokenStore.saveToken(response.data.token)
                    Result.Success(Unit)
                } else {
                    Result.Error(response.code ?: 400, response.error ?: "登录失败，请稍后重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "登录失败")
            }
        }
    }

    override suspend fun logout() {
        tokenStore.clear()
    }

    override suspend fun sendEmailVerificationCode(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendEmailVerificationCode(EmailCodeSendRequest(email))
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, response.message ?: "验证码发送失败，请稍后重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "验证码发送失败")
            }
        }
    }

    override suspend fun signUp(email: String, code: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val verify = api.verifyEmailCode(EmailCodeVerifyRequest(email, code))
                if (!verify.success) {
                    return@withContext Result.Error(400, verify.message ?: "验证码错误")
                }
                val create = api.signUp(SignUpRequest(email, password))
                if (create.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, create.message ?: "注册失败，请稍后重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "注册失败")
            }
        }
    }

    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendSmsCode(SmsSendRequest(phone = phone, scene = "LOGIN"))
                if (response.success) {
                    Result.Success(SmsSendStatus(requireCaptcha = false))
                } else {
                    // 业务失败（含风控/限频）均为 200 + success=false
                    Result.Error(
                        code = 400,
                        message = if (response.requireCaptcha) {
                            "触发安全验证，请改用邮箱登录"
                        } else {
                            response.error ?: "验证码发送失败"
                        }
                    )
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "验证码发送失败")
            }
        }
    }

    override suspend fun getProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                Result.Success(api.userProfile().toDomain())
            } catch (e: HttpException) {
                // 邮箱注册只建 User 行、不建 user_profile 行（Web sign-up 同口径），
                // 首次编辑资料（PUT upsert）前该接口恒 404 "Profile not found"。
                // Web 端「我的」页读 session、「个人中心」页 404 时保留默认资料，
                // 均不受影响；移动端等价兜底 = 用 JWT 会话声明构造最小资料。
                if (e.code() == 404) {
                    tokenStore.getSessionClaims().toFallbackProfile()?.let {
                        return@withContext Result.Success(it)
                    }
                }
                // requireAuth 的 401 响应体自带 "请先登录" 文案，统一走 errorMessage()
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "加载用户信息失败")
            }
        }
    }

    override suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                // 与 Web EditProfileModal 的 FormData 字段一一同构；文本分片用 text/plain
                fun text(value: String) = value.toRequestBody("text/plain".toMediaType())
                val avatarPart = avatarJpeg?.let { bytes ->
                    MultipartBody.Part.createFormData(
                        name = "avatar",
                        filename = "avatar.jpg",
                        body = bytes.toRequestBody("image/jpeg".toMediaType())
                    )
                }
                val response = api.updateProfile(
                    nickname = text(nickname),
                    bio = text(bio),
                    learnLevel = text(learnLevel),
                    dailyStudyGoalMins = text(dailyStudyGoalMins.toString()),
                    weeklyListeningGoalHours = text(weeklyListeningGoalHours.toString()),
                    weeklyWordsGoal = text(weeklyWordsGoal.toString()),
                    avatar = avatarPart
                )
                val updated = response.data
                if (response.success && updated != null) {
                    Result.Success(updated.toDomain())
                } else {
                    Result.Error(400, response.error ?: "更新失败，请重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "更新失败，请重试")
            }
        }
    }

    override suspend fun getStatsOverview(): Result<ProfileStats> {
        return withContext(Dispatchers.IO) {
            try {
                Result.Success(api.statsOverview().toDomain())
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "统计数据加载失败")
            }
        }
    }

    override suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>> {
        return withContext(Dispatchers.IO) {
            try {
                Result.Success(api.weeklyActivity(weekOffset).weeklyActivity.map { it.toDomain() })
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "活动数据加载失败")
            }
        }
    }

    override suspend fun getAchievements(): Result<List<AchievementItem>> {
        return withContext(Dispatchers.IO) {
            try {
                Result.Success(api.achievements().map { it.toDomain() })
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "成就加载失败")
            }
        }
    }

    // ---------- 账号与安全（绑定手机/邮箱 + 注销） ----------

    override suspend fun sendBindPhoneCode(phone: String): Result<SmsSendStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendSmsCode(SmsSendRequest(phone = phone, scene = "BIND"))
                if (response.success) {
                    Result.Success(SmsSendStatus(requireCaptcha = false))
                } else {
                    Result.Error(
                        code = 400,
                        message = if (response.requireCaptcha) {
                            // 原生端无法弹阿里云滑块，提示稍后再试（登录场景才提示改用邮箱登录）
                            "触发安全验证，请稍后再试"
                        } else {
                            response.error ?: "验证码发送失败"
                        }
                    )
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "验证码发送失败")
            }
        }
    }

    override suspend fun bindPhone(phone: String, code: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.bindPhone(BindPhoneRequest(phone, code))
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, response.message ?: "绑定失败，请重试")
                }
            } catch (e: HttpException) {
                // 验证码错误/已占用等业务失败随 400 返回 { success:false, error:"..." }
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "绑定失败，请重试")
            }
        }
    }

    override suspend fun sendBindEmailCode(email: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendBindEmailCode(BindEmailCodeRequest(email))
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, response.message ?: "验证码发送失败，请稍后重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "验证码发送失败")
            }
        }
    }

    override suspend fun bindEmail(email: String, code: String, password: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.bindEmail(BindEmailConfirmRequest(email, code, password))
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, response.message ?: "绑定失败，请重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "绑定失败，请重试")
            }
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.deleteAccount()
                if (response.success) {
                    Result.Success(Unit)
                } else {
                    Result.Error(400, response.message ?: "注销失败，请重试")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "注销失败，请重试")
            }
        }
    }

    /**
     * 解析后端 4xx/5xx 响应体（{ success, error | message }），提取用户可读文案；
     * 解析失败时回退为带状态码的通用提示。读取后关闭 body 防止连接泄漏。
     */
    private fun HttpException.errorMessage(): String {
        val body = try {
            response()?.errorBody()?.string()
        } catch (_: Exception) {
            null
        }
        val parsed = body?.let {
            runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull()
        }
        return parsed?.get("error").asText()
            ?: parsed?.get("message").asText()
            ?: "请求失败（HTTP ${code()}）"
    }

    /** 安全读取 JSON 字段文本：非原始类型（对象/数组/JSON null）返回 null 而非抛异常 */
    private fun JsonElement?.asText(): String? =
        (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
}

/**
 * user_profile 行缺失（404）时的兜底资料：以 JWT 会话声明补齐身份字段，
 * 昵称/简介/水平/头像留空，由 UI 层回退（昵称 → 邮箱前缀，对齐 Web session 展示口径）。
 * 声明里连 userid/email 都没有（理论上不可能：签发时必填）则返回 null 走原错误分支。
 */
internal fun SessionClaims.toFallbackProfile(): UserProfile? {
    if (userid.isNullOrBlank() && email.isNullOrBlank()) return null
    return UserProfile(
        userid = userid.orEmpty(),
        nickname = nickname,
        email = email,
        phone = phone,
        role = role
    )
}
