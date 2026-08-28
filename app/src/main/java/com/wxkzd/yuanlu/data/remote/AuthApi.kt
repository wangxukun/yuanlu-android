package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.core.network.ApiResponse
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.data.remote.dto.LoginResponseData
import com.wxkzd.yuanlu.data.remote.dto.SmsSendRequest
import com.wxkzd.yuanlu.data.remote.dto.SmsSendResponse
import com.wxkzd.yuanlu.data.remote.dto.UserProfileDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/auth/mobile/token")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponseData>

    /** 业务失败也返回 HTTP 200（requireCaptcha 标识滑块风控） */
    @POST("api/auth/sms/send")
    suspend fun sendSmsCode(@Body request: SmsSendRequest): SmsSendResponse

    /** 会话态接口，Bearer 由 AuthInterceptor 注入；返回裸对象 */
    @GET("api/user/profile")
    suspend fun userProfile(): UserProfileDto
}
