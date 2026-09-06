package com.wxkzd.yuanlu.domain.model

// ---------- 语音评测：设置（对齐 Web store/practice-settings-store.ts） ----------

/** 文本模式：原文 / 音标 / 盲读（Web TextMode normal|ipa|blind） */
enum class PracticeTextMode(val label: String) {
    NORMAL("原文"),
    IPA("音标"),
    BLIND("盲读")
}

/** 评测严格度（Web Strictness lenient|standard|strict，偏移量同口径） */
enum class PracticeStrictness(val offset: Int, val label: String) {
    LENIENT(-5, "宽松"),
    STANDARD(0, "标准"),
    STRICT(5, "严格")
}

/**
 * 语音评测设置（字段与默认值逐项对齐 Web practice-settings store）：
 * fontSizeLevel 0/1/2 = 小/中/大；passThreshold/weakThreshold clamp 60..95；
 * maxWords=50 视为不限。
 */
data class PracticeSettings(
    val fontSizeLevel: Int = 1,
    val showTranslation: Boolean = true,
    val showIpa: Boolean = true,
    val textMode: PracticeTextMode = PracticeTextMode.NORMAL,
    val passThreshold: Int = 80,
    val strictness: PracticeStrictness = PracticeStrictness.STANDARD,
    val weakThreshold: Int = 80,
    val minWords: Int = 0,
    val maxWords: Int = 50,
    val onlyUnmastered: Boolean = false,
    val autoAdvance: Boolean = true
) {
    /** 生效过关线 = 过关分数线 + 严格度偏移（Web selectEffectivePassThreshold） */
    val effectivePassThreshold: Int
        get() = (passThreshold + strictness.offset).coerceIn(0, 100)
}

// ---------- 语音评测：结果（对齐有道 ISE 返回经 Web 映射后的形状） ----------

/** 单音素诊断项：phoneme 如 "w"/"ɔ:/"，score 0..100 */
data class SpeechEvalPhoneme(
    val phoneme: String,
    val score: Int
)

/** 逐词评分项：start/end 为相对用户录音的秒数（供"我"的词切片回放） */
data class SpeechEvalWord(
    val word: String,
    val score: Int,
    val start: Double? = null,
    val end: Double? = null,
    val phonemes: List<SpeechEvalPhoneme> = emptyList()
)

/** 一次评测的完整结果（本地录音文件路径由端内补齐，userAudioUrl 为空） */
data class SpeechEvalResult(
    val overallScore: Int,
    val pronunciation: Int,
    val fluency: Int,
    val integrity: Int,
    val speed: Int,
    val words: List<SpeechEvalWord>,
    val recognitionId: Long? = null,
    val userAudioPath: String? = null
)

// ---------- 语音评测：练习数据 ----------

/** 历史评测记录（speech_recognition 表口径，用于只练未掌握过滤与历史对比） */
data class SpeechPracticeRecord(
    val recognitionid: Long,
    val accuracyScore: Int,
    val overallScore: Int?,
    val fluencyScore: Int?,
    val integrityScore: Int?,
    val speed: Int?,
    val targetText: String,
    val targetStartTime: Int,
    val subtitleId: Int?,
    val recognitionDate: String
) {
    /** 该次尝试的综合分（Web：overallScore ?? accuracyScore） */
    val bestScore: Int get() = overallScore ?: accuracyScore
}

/** GET api/speech/practice-data 的领域形状 */
data class SpeechPracticeData(
    val audioUrl: String?,
    val subtitles: List<Subtitle>,
    val records: List<SpeechPracticeRecord>,
    val isTrialMode: Boolean
)

/** 单句评测卡片的阶段状态机 */
enum class EvalPhase { IDLE, RECORDING, EVALUATING, RESULT }
