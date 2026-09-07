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
}
