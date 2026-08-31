package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.data.remote.dto.SmsSendRequest
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.SmsSendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
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

    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.sendSmsCode(SmsSendRequest(phone = phone))
                if (response.success) {
                    Result.Success(SmsSendStatus(requireCaptcha = false))
                } else {
                    // 业务失败（含风控/限频）均为 200 + success=false
                    Result.Error(
                        code = response.code ?: 400,
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
                // requireAuth 的 401 响应体自带 "请先登录" 文案，统一走 errorMessage()
                Result.Error(e.code(), e.errorMessage())
            } catch (e: IOException) {
                Result.NetworkError
            } catch (e: Exception) {
                Result.Error(600, e.message ?: "加载用户信息失败")
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
