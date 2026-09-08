package com.wxkzd.yuanlu.domain.model

import kotlin.math.roundToInt

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
    /** 本轮录音的本地文件路径（会话内即时回放用；重进页面后由云端 userAudioUrl 恢复） */
    val userAudioPath: String? = null,
    /** 历史记录的云端录音签名直链（重进页面后"回放我的发音/我"的播放源） */
    val userAudioUrl: String? = null
) {
    /** 回放音频源：本地文件优先（快、无流量），缺失或已失效时回退云端直链 */
    fun audioSource(): String? = userAudioPath?.takeIf { java.io.File(it).exists() }
        ?.let { java.io.File(it).toURI().toString() }
        ?: userAudioUrl?.takeIf { it.isNotBlank() }
}

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
    val recognitionDate: String,
    /** 深度明细（逐词/音素）的 OSS 地址：重进页面经 /api/speech/detail 拉取回填 */
    val detailUrl: String? = null,
    /** 云端录音签名直链：重进页面后"回放我的发音/我"的播放源 */
    val userAudioUrl: String? = null
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

// ---------- 发音弱项本（对齐 Web /library/pronunciation SSR 页数据组装） ----------

/**
 * 发音能力画像（Web speechProfileService 聚合口径）：五维雷达 + CEFR 等级。
 * null 表示该维度暂无数据（雷达图以 0 计并提示积累中）。
 */
data class SpeechProfile(
    val evalCount: Int = 0,
    val avgOverall: Double? = null,
    val avgAccuracy: Double? = null,
    val avgFluency: Double? = null,
    val avgIntegrity: Double? = null,
    /** 词/分钟 */
    val avgSpeed: Double? = null,
    val speedFitScore: Double? = null,
    val cefrLevel: String? = null
) {
    val hasData: Boolean get() = evalCount > 0

    /** 五维雷达数据点（缺维度以 0 计，均分四舍五入，Web toRadarData 同口径） */
    val radarDims: List<Pair<String, Int>>
        get() = listOf(
            "准确度" to avgAccuracy,
            "流利度" to avgFluency,
            "完整度" to avgIntegrity,
            "语速适配" to speedFitScore,
            "综合表现" to avgOverall
        ).map { (dim, value) -> dim to (value?.roundToInt() ?: 0) }
}

/** 单音素累计统计（user_profile.phonemeStats 展开项，均分升序 = 最弱在前） */
data class PhonemeStat(
    val phoneme: String,
    val avgScore: Int,
    val count: Int,
    val lowScoreCount: Int
)

/**
 * 弱项句子（speech_recognition 最新一次仍低于分数线的记录）：
 * notebook 列表态只保证剧集标题/封面；errors 接口额外补齐音频直链与字幕上下文。
 */
data class WeakSentenceRecord(
    val recognitionid: Long,
    val episodeid: String?,
    val episodeTitle: String? = null,
    val episodeCoverUrl: String? = null,
    val episodeAudioUrl: String? = null,
    val targetText: String,
    val targetStartTime: Int = 0,
    /** errors 接口的字幕补齐（闯关复习卡需要） */
    val subtitleTextCn: String? = null,
    val subtitleWords: List<SubtitleWord>? = null,
    val subtitleEnd: Double? = null,
    val subtitleId: Int? = null,
    val accuracyScore: Int = 0,
    val overallScore: Int? = null,
    val speed: Int? = null,
    val recognitionDate: String = ""
) {
    /** 上次综合得分（Web：Math.round(record.overallScore)） */
    val lastScore: Int get() = overallScore ?: accuracyScore

    /** 闯关复习卡的伪字幕（Web practice 页 mockSubtitle 同口径） */
    fun toSubtitle() = Subtitle(
        id = subtitleId ?: recognitionid.toInt(),
        textEn = targetText,
        textCn = subtitleTextCn,
        start = targetStartTime.toDouble(),
        end = subtitleEnd ?: (targetStartTime + 3.0),
        words = subtitleWords
    )

    /** "最近得分"入口的历史记录形态（匹配口径：subtitleId 相同 || 文本+起点差 < 0.5s） */
    fun toPracticeRecord() = SpeechPracticeRecord(
        recognitionid = recognitionid,
        accuracyScore = accuracyScore,
        overallScore = overallScore,
        fluencyScore = null,
        integrityScore = null,
        speed = speed,
        targetText = targetText,
        targetStartTime = targetStartTime,
        subtitleId = subtitleId,
        recognitionDate = recognitionDate
    )
}

/** GET api/speech/notebook 的领域形状（弱项本主页一次拉齐） */
data class SpeechNotebook(
    val isPremium: Boolean = false,
    val weakThreshold: Int = 80,
    val profile: SpeechProfile = SpeechProfile(),
    val phonemeStats: List<PhonemeStat> = emptyList(),
    /** 弱项句子总数（非会员 errors 为试用切片，总数用于锁定提示） */
    val totalErrors: Int = 0,
    val errors: List<WeakSentenceRecord> = emptyList()
) {
    /** 被锁定的弱项句子数（会员恒为 0） */
    val lockedCount: Int get() = if (isPremium) 0 else (totalErrors - errors.size).coerceAtLeast(0)
    val weakestPhoneme: String? get() = phonemeStats.firstOrNull()?.let { "/${it.phoneme}/" }
    val masteredPhonemeCount: Int get() = phonemeStats.count { it.avgScore >= 85 }
}

// ---------- 发音达人榜（对齐 Web /api/speech/leaderboard） ----------

/** 排行榜周期：weekly=近7天 / daily=今日 */
enum class LeaderboardPeriod(val label: String, val apiValue: String) {
    WEEKLY("近7天", "weekly"),
    DAILY("今日", "daily")
}

/** 排行依据：score=平均综合分（≥5 次评测）/ count=练习次数 */
enum class LeaderboardMetric(val label: String, val apiValue: String, val ruleText: String) {
    SCORE("平均分榜", "score", "平均综合分（≥5次评测）"),
    COUNT("勤奋榜", "count", "练习次数")
}

data class LeaderboardEntry(
    val userid: String,
    val nickname: String,
    val avatar: String,
    val evalCount: Int,
    val avgScore: Int
)

/** 当前用户排名摘要（未上榜时为 null） */
data class MyLeaderboardRank(
    val rank: Int,
    val evalCount: Int,
    val avgScore: Int
)

data class SpeechLeaderboard(
    val period: LeaderboardPeriod,
    val metric: LeaderboardMetric,
    val entries: List<LeaderboardEntry> = emptyList(),
    val me: MyLeaderboardRank? = null
)
