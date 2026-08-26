package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.domain.repository.AuthRepository
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
}
