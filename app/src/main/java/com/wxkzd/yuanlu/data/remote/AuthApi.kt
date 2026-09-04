package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.core.network.ApiResponse
import com.wxkzd.yuanlu.data.remote.dto.AchievementItemDto
import com.wxkzd.yuanlu.data.remote.dto.LoginRequest
import com.wxkzd.yuanlu.data.remote.dto.LoginResponseData
import com.wxkzd.yuanlu.data.remote.dto.ProfileStatsDto
import com.wxkzd.yuanlu.data.remote.dto.ProfileUpdateResponseDto
import com.wxkzd.yuanlu.data.remote.dto.RecentHistoryItemDto
import com.wxkzd.yuanlu.data.remote.dto.SmsSendRequest
import com.wxkzd.yuanlu.data.remote.dto.SmsSendResponse
import com.wxkzd.yuanlu.data.remote.dto.UserProfileDto
import com.wxkzd.yuanlu.data.remote.dto.WeeklyActivityResponseDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

interface AuthApi {
    @POST("api/auth/mobile/token")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponseData>

    /** 业务失败也返回 HTTP 200（requireCaptcha 标识滑块风控） */
    @POST("api/auth/sms/send")
    suspend fun sendSmsCode(@Body request: SmsSendRequest): SmsSendResponse

    /** 会话态接口，Bearer 由 AuthInterceptor 注入；返回裸对象 */
    @GET("api/user/profile")
    suspend fun userProfile(): UserProfileDto

    // ---------- 个人中心（对齐 Web /auth/personal-center） ----------

    /**
     * PUT api/user/profile：multipart 表单（昵称/简介/水平/三项学习目标 + 可选头像文件），
     * 与 Web EditProfileModal 的 FormData 完全同构；avatar 为 null 时 Retrofit 自动省略该分片。
     * 响应信封 { success, data: 更新后的 user_profile 行 }。
     */
    @Multipart
    @PUT("api/user/profile")
    suspend fun updateProfile(
        @Part("nickname") nickname: RequestBody,
        @Part("bio") bio: RequestBody,
        @Part("learnLevel") learnLevel: RequestBody,
        @Part("dailyStudyGoalMins") dailyStudyGoalMins: RequestBody,
        @Part("weeklyListeningGoalHours") weeklyListeningGoalHours: RequestBody,
        @Part("weeklyWordsGoal") weeklyWordsGoal: RequestBody,
        @Part avatar: MultipartBody.Part?
    ): ProfileUpdateResponseDto

    /** 裸对象：旅程概览统计（累计时长/连续天数/词汇量/口语评测） */
    @GET("api/user/stats/overview")
    suspend fun statsOverview(): ProfileStatsDto

    /** 信封 { weeklyActivity }：weekOffset 0=本周 1=上周 */
    @GET("api/user/stats/weekly-activity")
    suspend fun weeklyActivity(@Query("weekOffset") weekOffset: Int): WeeklyActivityResponseDto

    /** 裸数组：成就列表（unlocked=点亮） */
    @GET("api/user/achievements")
    suspend fun achievements(): List<AchievementItemDto>

    /** 裸数组：最近收听（固定 3 条） */
    @GET("api/user/history/recent")
    suspend fun recentHistory(): List<RecentHistoryItemDto>
}
