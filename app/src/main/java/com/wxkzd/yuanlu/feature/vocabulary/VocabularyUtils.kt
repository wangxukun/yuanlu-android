package com.wxkzd.yuanlu.feature.vocabulary

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 生词本纯逻辑（对齐 Web lib/srs.ts 与 useVocabularyNotebook 的过滤排序）：
 * 复习质量常量、Leitner 间隔阶梯、到期判断、日期格式化与列表过滤排序。
 * 保持无副作用以便单元测试。
 */

/** 复习质量（对齐 Web ReviewQuality）：0=忘记 1=模糊 2=认识 3=简单 */
object ReviewQuality {
    const val FORGOT = 0
    const val HARD = 1
    const val GOOD = 2
    const val EASY = 3
}

/** Leitner 间隔阶梯（天）：等级 proficiency -> 复习间隔，与 Web INTERVALS 一一对应 */
private val INTERVALS = intArrayOf(0, 1, 3, 7, 14, 30, 90)

/**
 * SRS 按钮上的「下次间隔」预览（今天/1天/N天），口径与 Web getIntervalLabel 一致：
 * 忘记=今天、模糊=1天、认识/简单=升级后等级对应的阶梯天数（封顶 90 天）。
 */
fun nextIntervalLabel(proficiency: Int, quality: Int): String {
    val days = when (quality) {
        ReviewQuality.FORGOT -> 0
        ReviewQuality.HARD -> 1
        else -> INTERVALS[minOf(proficiency + 1, INTERVALS.size - 1)]
    }
    return when {
        days <= 0 -> "今天"
        days == 1 -> "1天"
        else -> "${days}天"
    }
}

/** ISO 字符串 -> 毫秒；解析失败返回 Long.MAX_VALUE（视为未到期，对齐 Web Invalid Date 比较恒 false） */
fun parseIsoMillis(iso: String?): Long = runCatching {
    Instant.parse(requireNotNull(iso)).toEpochMilli()
}.getOrDefault(Long.MAX_VALUE)

/** 是否到期：nextReviewAt 缺失视为到期（对齐 Web isDue） */
fun isDue(nextReviewAt: String?, nowMillis: Long = System.currentTimeMillis()): Boolean {
    if (nextReviewAt.isNullOrBlank()) return true
    return parseIsoMillis(nextReviewAt) <= nowMillis
}

/** 下次复习日期展示（M/d，对齐 Web formatDate 的 zh-CN 月/日）；无日期返回 N/A */
fun formatReviewDate(nextReviewAt: String?): String {
    val millis = parseIsoMillis(nextReviewAt)
    if (nextReviewAt.isNullOrBlank() || millis == Long.MAX_VALUE) return "N/A"
    val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${date.monthValue}/${date.dayOfMonth}"
}

/** 生词本列表状态 Tab（对齐 Web filterStatus） */
enum class VocabStatusTab(val label: String, val value: String) {
    LEARNING("学习中", "LEARNING"),
    MASTERED("已掌握", "MASTERED")
}

/** 列表排序（对齐 Web sortMethod：复习时间/添加时间/A-Z） */
enum class VocabSortMethod(val label: String) {
    REVIEW("复习时间"),
    ADDED("添加时间"),
    ALPHA("A-Z")
}

/** 按状态 Tab + 搜索词过滤并排序（搜索匹配单词或释义，口径对齐 Web filteredList） */
fun filterAndSortVocabulary(
    items: List<VocabularyItem>,
    tab: VocabStatusTab,
    query: String,
    sort: VocabSortMethod
): List<VocabularyItem> {
    val keyword = query.trim().lowercase()
    val filtered = items.filter { item ->
        (item.status.ifBlank { "LEARNING" } == tab.value) &&
            (keyword.isEmpty() ||
                item.word.lowercase().contains(keyword) ||
                item.translation?.contains(keyword) == true)
    }
    return when (sort) {
        VocabSortMethod.REVIEW -> filtered.sortedBy { parseIsoMillis(it.nextReviewAt) }
        VocabSortMethod.ADDED -> filtered.sortedByDescending { parseIsoMillis(it.addedDate) }
        VocabSortMethod.ALPHA -> filtered.sortedBy { it.word.lowercase() }
    }
}

/** 生词本的单词发音兜底：有道 dictvoice 合成地址（词典未收录/无 speakUrl 时使用） */
fun youdaoDictVoiceUrl(word: String, us: Boolean = true): String {
    val encoded = java.net.URLEncoder.encode(word, "UTF-8")
    return "https://dict.youdao.com/dictvoice?audio=$encoded&type=${if (us) 2 else 1}"
}

/**
 * 原声出处句子高亮（对齐 Web renderContext）：大小写不敏感地给目标词加色加粗。
 * 词边界匹配失败时（如带连字符的复合词）退化为包含匹配。
 */
fun buildHighlightedContext(
    sentence: String,
    word: String,
    baseColor: Color,
    highlightColor: Color
): AnnotatedString {
    if (word.isBlank()) return AnnotatedString(sentence)
    val regex = Regex("\\b${Regex.escape(word)}\\b", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(sentence).toList()
        .ifEmpty {
            // 兜底：包含匹配（复合词/词边界争议时仍能命中）
            Regex(Regex.escape(word), RegexOption.IGNORE_CASE).findAll(sentence).toList()
        }
    if (matches.isEmpty()) return AnnotatedString(sentence)
    return buildAnnotatedString {
        var cursor = 0
        for (match in matches) {
            append(sentence.substring(cursor, match.range.first))
            pushStyle(SpanStyle(color = highlightColor, fontWeight = FontWeight.Bold))
            append(sentence.substring(match.range))
            pop()
            cursor = match.range.last + 1
        }
        append(sentence.substring(cursor))
        addStyle(SpanStyle(color = baseColor), 0, length)
    }
}
