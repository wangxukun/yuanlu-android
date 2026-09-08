package com.wxkzd.yuanlu.data.repository

import android.util.Base64
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.data.remote.SpeechApi
import com.wxkzd.yuanlu.data.remote.dto.EvaluateRequestDto
import com.wxkzd.yuanlu.data.remote.dto.SpeechErrorBodyDto
import com.wxkzd.yuanlu.domain.model.LeaderboardMetric
import com.wxkzd.yuanlu.domain.model.LeaderboardPeriod
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechLeaderboard
import com.wxkzd.yuanlu.domain.model.SpeechNotebook
import com.wxkzd.yuanlu.domain.model.SpeechPracticeData
import com.wxkzd.yuanlu.domain.model.WeakSentenceRecord
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

    override suspend fun getSpeechDetail(recognitionId: Long): Result<SpeechEvalResult> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getSpeechDetail(recognitionId)
                val details = response.data
                if (response.success && details != null) {
                    // 明细缺失的维度由调用方（VM）用历史记录的分数补齐
                    Result.Success(details.toDomain(recognitionId, null))
                } else {
                    Result.Error(0, response.error ?: "暂无逐词明细")
                }
            } catch (e: HttpException) {
                // 404 = 该记录无深度明细（旧记录/上传失败），静默降级由调用方处理
                Result.Error(e.code(), httpMessage(e) ?: "暂无逐词明细")
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

    override suspend fun getNotebook(): Result<SpeechNotebook> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getNotebook()
                val data = response.data
                if (response.success && data != null) {
                    Result.Success(data.toDomain())
                } else {
                    Result.Error(0, response.error ?: "弱项本数据加载失败")
                }
            } catch (e: HttpException) {
                Result.Error(e.code(), httpMessage(e) ?: notebookErrorMessage(e.code()))
            } catch (_: IOException) {
                Result.NetworkError
            }
        }

    override suspend fun getWeakErrors(): Result<List<WeakSentenceRecord>> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getWeakErrors()
                if (response.success) {
                    Result.Success(response.data.map { it.toDomain() })
                } else {
                    Result.Error(0, response.error ?: "弱项句子加载失败")
                }
            } catch (e: HttpException) {
                // 403 = 弱项练习为 PRO 会员功能（UI 层据此渲染锁定态）
                Result.Error(e.code(), httpMessage(e) ?: notebookErrorMessage(e.code()))
            } catch (_: IOException) {
                Result.NetworkError
            }
        }

    override suspend fun getLeaderboard(
        period: LeaderboardPeriod,
        metric: LeaderboardMetric
    ): Result<SpeechLeaderboard> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLeaderboard(period.apiValue, metric.apiValue)
            val data = response.data
            if (response.success && data != null) {
                Result.Success(data.toDomain())
            } else {
                Result.Error(0, response.error ?: "排行榜加载失败")
            }
        } catch (e: HttpException) {
            Result.Error(
                e.code(),
                if (e.code() == 401) "请先登录后查看排行榜" else httpMessage(e) ?: "排行榜加载失败"
            )
        } catch (_: IOException) {
            Result.NetworkError
        }
    }

    /** 弱项族接口的 HTTP 状态码 → 中文文案（403 PRO 门禁保留状态码语义由 UI 分流） */
    private fun notebookErrorMessage(code: Int): String = when (code) {
        401 -> "请先登录后查看"
        403 -> "PRO 会员功能"
        else -> "弱项本数据加载失败"
    }
}
