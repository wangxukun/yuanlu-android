package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result

interface AuthRepository {
    suspend fun loginWithPassword(email: String, password: String): Result<Unit>
    suspend fun loginWithSms(phone: String, code: String): Result<Unit>
    suspend fun logout()
}
