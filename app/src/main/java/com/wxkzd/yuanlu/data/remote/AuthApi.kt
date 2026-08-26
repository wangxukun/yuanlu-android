package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.core.network.ApiResponse
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.data.remote.dto.LoginResponseData
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/mobile/token")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponseData>
}
