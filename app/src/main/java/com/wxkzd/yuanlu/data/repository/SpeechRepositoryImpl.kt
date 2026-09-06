package com.wxkzd.yuanlu.data.repository

import android.util.Base64
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.SpeechApi
import com.wxkzd.yuanlu.data.remote.dto.EvaluateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.SpeechErrorBodyDto
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechPracticeData
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 语音评测数据源（错误映射口径与 ContentRepositoryImpl 一致） */
@Singleton
class SpeechRepositoryImpl @Inject constructor(
    private val api: SpeechApi
) : SpeechRepository {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    override suspend fun getPracticeData(episodeId: String): Result<SpeechPracticeData> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getPracticeData(episodeId)
                val data = response.data
                if (response.success && data != null) {
                    Result.Success(data.toDomain())
                } else {
                    Result.Error(0, response.error ?: "练习数据加载失败")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), httpMessage(e) ?: "练习数据加载失败")
            } catch (_: IOException) {
                Result.NetworkError
            }
        }

    override suspend fun evaluate(
        episodeId: String,
        subtitleId: Int,
        targetText: String,
        wavBytes: ByteArray
    ): Result<SpeechEvalResult> = withContext(Dispatchers.IO) {
        try {
            val audioBase64 = Base64.encodeToString(wavBytes, Base64.NO_WRAP)
            val response = api.evaluate(
                EvaluateRequestDto(
                    episodeId = episodeId,
                    subtitleId = subtitleId,
                    targetText = targetText,
                    audioBase64 = audioBase64
                )
            )
            val data = response.data
            if (response.success && data != null) {
                val details = data.details
                val result = if (details != null) {
                    details.toDomain(data.recognitionId, data.score)
                } else {
                    // 无逐词明细时退化为总分（Web 同口径兜底）
                    SpeechEvalResult(
                        overallScore = (data.score ?: 0.0).toInt(),
                        pronunciation = (data.score ?: 0.0).toInt(),
                        fluency = 0,
                        integrity = 0,
                        speed = 0,
                        words = emptyList(),
                        recognitionId = data.recognitionId
                    )
                }
                Result.Success(result)
            } else {
                Result.Error(0, response.message ?: response.error ?: "评测失败，请重试")
            }
        } catch (e: HttpException) {
            // 403 EVALUATION_QUOTA_EXCEEDED 时后端在 message 携带升级文案
            Result.Error(e.code(), httpMessage(e) ?: "评测失败，请重试")
        } catch (_: IOException) {
            Result.NetworkError
        }
    }

    /** 解析错误响应体里的 message（配额超限等业务文案），失败回退 null */
    private fun httpMessage(e: HttpException): String? = runCatching {
        e.response()?.errorBody()?.string()?.let { raw ->
            json.decodeFromString(SpeechErrorBodyDto.serializer(), raw).message
        }
    }.getOrNull()
}
