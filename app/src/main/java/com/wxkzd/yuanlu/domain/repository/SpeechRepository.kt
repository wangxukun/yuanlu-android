package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechPracticeData

/** 语音评测（对齐 Web /api/speech 路由族） */
interface SpeechRepository {

    /** GET api/speech/practice-data?id=：单集字幕 + 签名音频直链 + 历史记录 + 试用模式标记 */
    suspend fun getPracticeData(episodeId: String): Result<SpeechPracticeData>

    /**
     * POST api/speech/evaluate：提交 16kHz mono WAV（裸字节，端内转 base64）。
     * 返回结果不含 userAudioPath（本地录音文件路径由 ViewModel 补齐）。
     */
    suspend fun evaluate(
        episodeId: String,
        subtitleId: Int,
        targetText: String,
        wavBytes: ByteArray
    ): Result<SpeechEvalResult>
}
