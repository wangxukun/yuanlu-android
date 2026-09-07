package com.wxkzd.yuanlu.domain.repository

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.LeaderboardMetric
import com.wxkzd.yuanlu.domain.model.LeaderboardPeriod
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechLeaderboard
import com.wxkzd.yuanlu.domain.model.SpeechNotebook
import com.wxkzd.yuanlu.domain.model.SpeechPracticeData
import com.wxkzd.yuanlu.domain.model.WeakSentenceRecord

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

    /** GET api/speech/notebook：弱项本主页聚合（画像/音素统计/试用切片弱项列表），需登录 */
    suspend fun getNotebook(): Result<SpeechNotebook>

    /**
     * GET api/speech/errors：弱项句子全量（含签名音频直链与字幕补齐，闯关复习用）。
     * PRO 会员功能，非会员返回 Result.Error(403)。
     */
    suspend fun getWeakErrors(): Result<List<WeakSentenceRecord>>

    /** GET api/speech/leaderboard：发音达人榜（period × metric 四象限），需登录 */
    suspend fun getLeaderboard(
        period: LeaderboardPeriod,
        metric: LeaderboardMetric
    ): Result<SpeechLeaderboard>
}
