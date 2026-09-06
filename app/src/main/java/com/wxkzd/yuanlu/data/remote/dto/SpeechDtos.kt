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
