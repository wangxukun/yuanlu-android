package com.wxkzd.yuanlu.feature.pronunciation

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 得分档位（对齐 Web 列表徽章：≥80 绿 / ≥60 黄 / 其余红） */
enum class ScoreTone { GOOD, MID, BAD }

/** 纯逻辑工具（JVM 可测）：日期中文化 + 得分档位 + 雷达数据派生 */
object PronunciationUtils {

    /**
     * ISO 时间 → "2026年9月7日"（Web toLocaleDateString("zh-CN", {year:'numeric',month:'long',day:'numeric'})）。
     * 解析失败回退原串前 10 位日期。
     */
    fun formatZhDate(iso: String?): String {
        if (iso.isNullOrBlank()) return ""
        val parsed = runCatching {
            OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
        }.recoverCatching {
            LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME).toLocalDate()
        }.recoverCatching {
            LocalDate.parse(iso.take(10))
        }.getOrNull() ?: return iso.take(10)
        return "${parsed.year}年${parsed.monthValue}月${parsed.dayOfMonth}日"
    }

    fun classifyScore(score: Int): ScoreTone = when {
        score >= 80 -> ScoreTone.GOOD
        score >= 60 -> ScoreTone.MID
        else -> ScoreTone.BAD
    }

    /** 薄弱音素雷达数据：最弱前 6 项（Web stats.slice(0,6)），标签包 /音素/ */
    fun phonemeRadarPoints(stats: List<com.wxkzd.yuanlu.domain.model.PhonemeStat>, limit: Int = 6) =
        stats.take(limit).map { "/${it.phoneme}/" to it.avgScore.toFloat() }

    /** 音标弱读变体提示段（定冠词等）："/ðə/ (before vowels: /ði/)" 中的括号标注 */
    private val beforeVowelsNote = Regex("\\s*\\(before vowels:[^)]*\\)", RegexOption.IGNORE_CASE)

    private val whitespace = Regex("\\s+")

    /**
     * 清洗词典音标用于音标文本模式逐词拼接展示：
     * 1) 移除定冠词弱读变体提示 "(before vowels: /ði/)"（连同前导空白，避免留尾空格）；
     * 2) 移除所有斜杠分隔符（含复合标注内残留的孤立斜杠）；
     * 3) 压缩连续空格并 trim。
     * 例："/ðə/ (before vowels: /ði/)" → "ðə"；"/sʌm/" → "sʌm"；"/gəʊ / " → "gəʊ"。
     */
    fun stripIpaSlashes(ipa: String): String = ipa
        .replace(beforeVowelsNote, "")
        .replace("/", "")
        .replace(whitespace, " ")
        .trim()

    /**
     * 音标模式的取词键口径：按空白切分 → 剥离词上首尾标点（保留撇号）→ 小写 → 去重。
     * 与卡片渲染键（SpeechEvalCard.cleanWordKey）一致——缓存键必须与渲染键同口径，
     * 否则 "world." 会以带句点的键入缓存、以 "world" 查不到而回退显示原词；
     * 返回值同时用作词典查询入参（Web SpeechEvaluationCard 以小写词取 /api/dict 同口径）。
     */
    fun ipaLookupKeys(text: String): List<String> = text
        .split(whitespace)
        .map { it.trim { !it.isLetter() && it != '\'' }.lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
}
