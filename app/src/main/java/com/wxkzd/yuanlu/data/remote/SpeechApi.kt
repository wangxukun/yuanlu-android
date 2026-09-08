package com.wxkzd.yuanlu.data.remote

import com.wxkzd.yuanlu.data.remote.dto.ErrorsResponseDto
import com.wxkzd.yuanlu.data.remote.dto.EvaluateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.EvaluateResponseDto
import com.wxkzd.yuanlu.data.remote.dto.LeaderboardResponseDto
import com.wxkzd.yuanlu.data.remote.dto.NotebookResponseDto
import com.wxkzd.yuanlu.data.remote.dto.PracticeDataResponseDto
import com.wxkzd.yuanlu.data.remote.dto.SpeechDetailResponseDto
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

    /** 信封：data = OSS 上的有道明细 JSON（words/phonemes）；重进页面恢复逐词诊断用；404 = 无明细 */
    @GET("api/speech/detail")
    suspend fun getSpeechDetail(
        @Query("id") recognitionId: Long,
        @Query("t") timestamp: Long = System.currentTimeMillis()
    ): SpeechDetailResponseDto

    /** 信封：data = { isPremium, weakThreshold, profile, phonemeStats, totalErrors, errors } */
    @GET("api/speech/notebook")
    suspend fun getNotebook(): NotebookResponseDto

    /** 信封：data = 弱项句子全量（含音频直链与字幕补齐）；403 = PRO 会员功能 */
    @GET("api/speech/errors")
    suspend fun getWeakErrors(): ErrorsResponseDto

    /** 信封：data = { period, metric, entries, me }；401 = 未登录 */
    @GET("api/speech/leaderboard")
    suspend fun getLeaderboard(
        @Query("period") period: String,
        @Query("metric") metric: String
    ): LeaderboardResponseDto
}
