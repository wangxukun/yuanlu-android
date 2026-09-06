package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.data.remote.dto.EvaluateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.EvaluateResponseDto
import com.wxkzd.yuanlu.data.remote.dto.PracticeDataResponseDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** 语音评测端点（对齐 Web app/api/speech 路由族，AuthInterceptor 自动带 Bearer） */
interface SpeechApi {

    /** 信封：data = { episode, subtitles, previousRecords, isTrialMode }，需登录 */
    @GET("api/speech/practice-data")
    suspend fun getPracticeData(
        @Query("id") episodeId: String,
        @Query("t") timestamp: Long = System.currentTimeMillis()
    ): PracticeDataResponseDto

    /** 信封：data = { score, details, recognitionId }；403 = EVALUATION_QUOTA_EXCEEDED */
    @POST("api/speech/evaluate")
    suspend fun evaluate(@Body body: EvaluateRequestDto): EvaluateResponseDto
}
