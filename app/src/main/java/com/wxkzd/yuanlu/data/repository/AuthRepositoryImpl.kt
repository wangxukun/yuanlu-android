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
                    Result.Error(response.code ?: 400, response.error ?: "Login failed")
                }
            } catch (e: Exception) {
                Result.NetworkError
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
                    Result.Error(response.code ?: 400, response.error ?: "Login failed")
                }
            } catch (e: Exception) {
                Result.NetworkError
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
            } catch (e: Exception) {
                Result.NetworkError
            }
        }
    }

    override suspend fun getProfile(): Result<UserProfile> {
        return withContext(Dispatchers.IO) {
            try {
                Result.Success(api.userProfile().toDomain())
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 401) {
                    Result.Error(401, "请先登录")
                } else {
                    Result.Error(e.code(), "加载用户信息失败")
                }
            } catch (e: Exception) {
                Result.NetworkError
            }
        }
    }
}
