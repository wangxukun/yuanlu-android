package com.wxkzd.yuanlu.data.remote.dto

import com.wxkzd.yuanlu.domain.model.SpeechEvalPhoneme
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechEvalWord
import com.wxkzd.yuanlu.domain.model.SpeechPracticeData
import com.wxkzd.yuanlu.domain.model.SpeechPracticeRecord
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.model.SubtitleWord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

// ---------- GET api/speech/practice-data ----------

@Serializable
data class PracticeDataResponseDto(
    val success: Boolean = true,
    val data: PracticeDataDto? = null,
    val error: String? = null
)

@Serializable
data class PracticeDataDto(
    /** DB 行 spread + 签名 URL 覆盖（Web 端 as unknown as Episode），仅取端内需要的字段 */
    val episode: PracticeEpisodeDto? = null,
    val subtitles: List<PracticeSubtitleDto> = emptyList(),
    val previousRecords: List<PracticeRecordDto> = emptyList(),
    val isTrialMode: Boolean = false
) {
    fun toDomain() = SpeechPracticeData(
        audioUrl = episode?.audioUrl?.takeIf { it.isNotBlank() },
        subtitles = subtitles.map { it.toDomain() },
        records = previousRecords.map { it.toDomain() },
        isTrialMode = isTrialMode
    )
}

@Serializable
data class PracticeEpisodeDto(
    val id: String? = null,
    val episodeid: String? = null,
    val title: String? = null,
    /** 3 小时签名 OSS 直链（原声播放用） */
    val audioUrl: String? = null,
    val coverUrl: String? = null
)

/** practice-data 的字幕用 startSeconds/endSeconds 命名（与 /api/episode/subtitles 不同） */
@Serializable
data class PracticeSubtitleDto(
    val id: Int = 0,
    val textEn: String = "",
    val textCn: String? = null,
    val startSeconds: Double = 0.0,
    val endSeconds: Double? = null,
    val speaker: String? = null,
    val words: List<SubtitleWordDto>? = null
) {
    fun toDomain() = Subtitle(
        id = id,
        textEn = textEn,
        textCn = textCn,
        start = startSeconds,
        end = endSeconds ?: (startSeconds + 3.0),
        speaker = speaker,
        words = words?.map { SubtitleWord(it.word, it.start, it.end) }
    )
}

@Serializable
data class PracticeRecordDto(
    val recognitionid: Long = 0,
    val speechText: String? = null,
    val accuracyScore: Double = 0.0,
    val targetText: String? = null,
    val targetStartTime: Int = 0,
    val recognitionDate: String? = null,
    val fluencyScore: Double? = null,
    val integrityScore: Double? = null,
    val overallScore: Double? = null,
    val speed: Double? = null,
    val detailUrl: String? = null,
    val userAudioUrl: String? = null,
    val subtitleId: Int? = null
) {
    fun toDomain() = SpeechPracticeRecord(
        recognitionid = recognitionid,
        accuracyScore = accuracyScore.roundToInt(),
        overallScore = overallScore?.roundToInt(),
        fluencyScore = fluencyScore?.roundToInt(),
        integrityScore = integrityScore?.roundToInt(),
        speed = speed?.roundToInt(),
        targetText = targetText.orEmpty(),
        targetStartTime = targetStartTime,
        subtitleId = subtitleId,
        recognitionDate = recognitionDate.orEmpty()
    )
}

// ---------- POST api/speech/evaluate ----------

@Serializable
data class EvaluateRequestDto(
    val episodeId: String,
    val subtitleId: Int? = null,
    val targetText: String,
    /** WAV 裸字节 base64（无 data: 前缀，端内已转好） */
    val audioBase64: String,
    val rate: Int = 16000
)

@Serializable
data class EvaluateResponseDto(
    val success: Boolean = true,
    val data: EvaluateDataDto? = null,
    val error: String? = null,
    val message: String? = null
)

@Serializable
data class EvaluateDataDto(
    val score: Double? = null,
    /** 有道 ISE 原始 JSON（pronunciation/fluency/integrity/speed/overall + words） */
    val details: YoudaoDetailsDto? = null,
    val recognitionId: Long? = null
)

/** 有道 ISE 返回体（对齐 Web YoudaoResult 接口；数值可能带小数） */
@Serializable
data class YoudaoDetailsDto(
    val pronunciation: Double? = null,
    val fluency: Double? = null,
    val integrity: Double? = null,
    val speed: Double? = null,
    val overall: Double? = null,
    val words: List<YoudaoWordDto>? = null,
    val errorCode: String? = null
) {
    fun toDomain(recognitionId: Long?, fallbackScore: Double?): SpeechEvalResult {
        val words = words.orEmpty().map { it.toDomain() }
        return SpeechEvalResult(
            overallScore = (overall ?: pronunciation ?: fallbackScore ?: 0.0).roundToInt(),
            pronunciation = (pronunciation ?: 0.0).roundToInt(),
            fluency = (fluency ?: 0.0).roundToInt(),
            integrity = (integrity ?: 0.0).roundToInt(),
            speed = speed?.roundToInt() ?: 0,
            words = words,
            recognitionId = recognitionId
        )
    }
}

@Serializable
data class YoudaoWordDto(
    val word: String = "",
    val pronunciation: Double? = null,
    val start: Double? = null,
    val end: Double? = null,
    val phonemes: List<YoudaoPhonemeDto>? = null
) {
    fun toDomain() = SpeechEvalWord(
        word = word,
        score = (pronunciation ?: 0.0).roundToInt(),
        start = start,
        end = end,
        phonemes = phonemes.orEmpty().mapNotNull { ph ->
            val name = ph.phoneme ?: ph.phone ?: return@mapNotNull null
            SpeechEvalPhoneme(name, (ph.score ?: ph.pronunciation ?: 0.0).roundToInt())
        }
    )
}

/** 音素字段双口径：新版 phoneme/score，旧版 phone/pronunciation（Web 同口径兜底） */
@Serializable
data class YoudaoPhonemeDto(
    val phoneme: String? = null,
    val phone: String? = null,
    val score: Double? = null,
    val pronunciation: Double? = null
)

/** 错误响应体（evaluate 配额超限等场景的 message 提取） */
@Serializable
data class SpeechErrorBodyDto(
    val success: Boolean? = null,
    val error: String? = null,
    val message: String? = null
)

// ---------- GET api/speech/notebook（弱项本主页聚合） ----------

@Serializable
data class NotebookResponseDto(
    val success: Boolean = true,
    val data: NotebookDataDto? = null,
    val error: String? = null
)

@Serializable
data class NotebookDataDto(
    val isPremium: Boolean = false,
    val weakThreshold: Int = 80,
    val profile: SpeechProfileDto = SpeechProfileDto(),
    val phonemeStats: List<PhonemeStatDto> = emptyList(),
    val totalErrors: Int = 0,
    /** 会员全量 / 非会员前 3 条试用切片 */
    val errors: List<WeakRecordDto> = emptyList()
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.SpeechNotebook(
        isPremium = isPremium,
        weakThreshold = weakThreshold,
        profile = profile.toDomain(),
        phonemeStats = phonemeStats.map { it.toDomain() },
        totalErrors = totalErrors,
        errors = errors.map { it.toDomain() }
    )
}

/** Web SpeechProfileDto 同构（聚合均值，无数据为 null） */
@Serializable
data class SpeechProfileDto(
    val evalCount: Int = 0,
    val avgOverall: Double? = null,
    val avgAccuracy: Double? = null,
    val avgFluency: Double? = null,
    val avgIntegrity: Double? = null,
    val avgSpeed: Double? = null,
    val speedFitScore: Double? = null,
    val cefrLevel: String? = null
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.SpeechProfile(
        evalCount = evalCount,
        avgOverall = avgOverall,
        avgAccuracy = avgAccuracy,
        avgFluency = avgFluency,
        avgIntegrity = avgIntegrity,
        avgSpeed = avgSpeed,
        speedFitScore = speedFitScore,
        cefrLevel = cefrLevel
    )
}

@Serializable
data class PhonemeStatDto(
    val phoneme: String = "",
    val avgScore: Int = 0,
    val count: Int = 0,
    val lowScoreCount: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.PhonemeStat(phoneme, avgScore, count, lowScoreCount)
}

/** 弱项句子记录（notebook 与 errors 接口共用；episode 仅保证列表页字段） */
@Serializable
data class WeakRecordDto(
    val recognitionid: Long = 0,
    val episodeid: String? = null,
    val episode: WeakEpisodeDto? = null,
    val targetText: String? = null,
    val targetStartTime: Int? = null,
    /** errors 接口的字幕补齐字段 */
    val subtitleTextCn: String? = null,
    val subtitleWords: List<SubtitleWordDto>? = null,
    val subtitleEnd: Double? = null,
    val subtitleId: Int? = null,
    val accuracyScore: Double = 0.0,
    val overallScore: Double? = null,
    val speed: Double? = null,
    val recognitionDate: String? = null
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.WeakSentenceRecord(
        recognitionid = recognitionid,
        episodeid = episodeid,
        episodeTitle = episode?.title,
        episodeCoverUrl = episode?.coverUrl?.takeIf { it.isNotBlank() },
        episodeAudioUrl = episode?.audioUrl?.takeIf { it.isNotBlank() },
        targetText = targetText.orEmpty(),
        targetStartTime = targetStartTime ?: 0,
        subtitleTextCn = subtitleTextCn,
        subtitleWords = subtitleWords?.map { SubtitleWord(it.word, it.start, it.end) },
        subtitleEnd = subtitleEnd,
        subtitleId = subtitleId,
        accuracyScore = accuracyScore.roundToInt(),
        overallScore = overallScore?.roundToInt(),
        speed = speed?.roundToInt(),
        recognitionDate = recognitionDate.orEmpty()
    )
}

@Serializable
data class WeakEpisodeDto(
    val title: String? = null,
    /** 签名后直链；notebook 不下发 audioUrl（闯关复习走 errors 接口） */
    val coverUrl: String? = null,
    val audioUrl: String? = null
)

@Serializable
data class ErrorsResponseDto(
    val success: Boolean = true,
    val data: List<WeakRecordDto> = emptyList(),
    val error: String? = null
)

// ---------- GET api/speech/leaderboard ----------

@Serializable
data class LeaderboardResponseDto(
    val success: Boolean = true,
    val data: LeaderboardDataDto? = null,
    val error: String? = null
)

@Serializable
data class LeaderboardDataDto(
    val period: String? = null,
    val metric: String? = null,
    val entries: List<LeaderboardEntryDto> = emptyList(),
    val me: MyRankDto? = null
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.SpeechLeaderboard(
        period = if (period == "daily") com.wxkzd.yuanlu.domain.model.LeaderboardPeriod.DAILY
        else com.wxkzd.yuanlu.domain.model.LeaderboardPeriod.WEEKLY,
        metric = if (metric == "count") com.wxkzd.yuanlu.domain.model.LeaderboardMetric.COUNT
        else com.wxkzd.yuanlu.domain.model.LeaderboardMetric.SCORE,
        entries = entries.map { it.toDomain() },
        me = me?.toDomain()
    )
}

@Serializable
data class LeaderboardEntryDto(
    val userid: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val evalCount: Int = 0,
    val avgScore: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.LeaderboardEntry(userid, nickname, avatar, evalCount, avgScore)
}

@Serializable
data class MyRankDto(
    val rank: Int = 0,
    val evalCount: Int = 0,
    val avgScore: Int = 0
) {
    fun toDomain() = com.wxkzd.yuanlu.domain.model.MyLeaderboardRank(rank, evalCount, avgScore)
}
