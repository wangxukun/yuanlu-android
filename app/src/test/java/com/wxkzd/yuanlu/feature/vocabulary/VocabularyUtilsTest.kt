package com.wxkzd.yuanlu.feature.vocabulary

import androidx.compose.ui.graphics.Color
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class VocabularyUtilsTest {

    // ---------- nextIntervalLabel：Leitner 间隔阶梯预演 ----------

    @Test
    fun `forgot is always today, hard is always one day`() {
        assertEquals("今天", nextIntervalLabel(0, ReviewQuality.FORGOT))
        assertEquals("今天", nextIntervalLabel(5, ReviewQuality.FORGOT))
        assertEquals("1天", nextIntervalLabel(0, ReviewQuality.HARD))
        assertEquals("1天", nextIntervalLabel(4, ReviewQuality.HARD))
    }

    @Test
    fun `good and easy follow the interval ladder`() {
        // p+1 后取阶梯：0→1天 1→3天 2→7天 3→14天
        assertEquals("1天", nextIntervalLabel(0, ReviewQuality.GOOD))
        assertEquals("3天", nextIntervalLabel(1, ReviewQuality.GOOD))
        assertEquals("7天", nextIntervalLabel(2, ReviewQuality.EASY))
        assertEquals("14天", nextIntervalLabel(3, ReviewQuality.GOOD))
    }

    @Test
    fun `interval caps at 90 days`() {
        assertEquals("90天", nextIntervalLabel(9, ReviewQuality.GOOD))
        assertEquals("90天", nextIntervalLabel(100, ReviewQuality.EASY))
    }

    // ---------- isDue / parseIsoMillis / formatReviewDate ----------

    @Test
    fun `missing or past date is due, future date is not`() {
        val past = Instant.now().minus(1, ChronoUnit.DAYS).toString()
        val future = Instant.now().plus(1, ChronoUnit.DAYS).toString()
        assertTrue(isDue(null))
        assertTrue(isDue(""))
        assertTrue(isDue(past))
        assertFalse(isDue(future))
    }

    @Test
    fun `unparseable date is treated as not due`() {
        assertFalse(isDue("not a date"))
    }

    @Test
    fun `epoch parses to zero and blanks format as NA`() {
        assertEquals(0L, parseIsoMillis("1970-01-01T00:00:00Z"))
        assertEquals("N/A", formatReviewDate(null))
        assertEquals("N/A", formatReviewDate("garbage"))
    }

    // ---------- filterAndSortVocabulary ----------

    private fun item(
        id: Int,
        word: String,
        status: String = "LEARNING",
        nextReviewAt: String? = null,
        addedDate: String? = null,
        translation: String? = null
    ) = VocabularyItem(
        vocabularyid = id,
        word = word,
        translation = translation,
        status = status,
        nextReviewAt = nextReviewAt,
        addedDate = addedDate
    )

    private val sample = listOf(
        item(1, "apple", addedDate = "2026-08-01T00:00:00Z", nextReviewAt = "2026-09-05T00:00:00Z", translation = "苹果"),
        item(2, "banana", status = "MASTERED", addedDate = "2026-08-03T00:00:00Z"),
        item(3, "cherry", addedDate = "2026-08-02T00:00:00Z", nextReviewAt = "2026-09-01T00:00:00Z")
    )

    @Test
    fun `filters by status tab`() {
        val learning = filterAndSortVocabulary(sample, VocabStatusTab.LEARNING, "", VocabSortMethod.REVIEW)
        assertEquals(listOf("cherry", "apple"), learning.map { it.word })
        val mastered = filterAndSortVocabulary(sample, VocabStatusTab.MASTERED, "", VocabSortMethod.REVIEW)
        assertEquals(listOf("banana"), mastered.map { it.word })
    }

    @Test
    fun `search matches word or translation`() {
        val byWord = filterAndSortVocabulary(sample, VocabStatusTab.LEARNING, "APP", VocabSortMethod.REVIEW)
        assertEquals(listOf("apple"), byWord.map { it.word })
        val byTranslation = filterAndSortVocabulary(sample, VocabStatusTab.LEARNING, "苹果", VocabSortMethod.REVIEW)
        assertEquals(listOf("apple"), byTranslation.map { it.word })
    }

    @Test
    fun `sort by added date descending or alphabetically`() {
        val added = filterAndSortVocabulary(sample, VocabStatusTab.LEARNING, "", VocabSortMethod.ADDED)
        assertEquals(listOf("cherry", "apple"), added.map { it.word })
        val alpha = filterAndSortVocabulary(sample, VocabStatusTab.LEARNING, "", VocabSortMethod.ALPHA)
        assertEquals(listOf("apple", "cherry"), alpha.map { it.word })
    }

    // ---------- youdaoDictVoiceUrl ----------

    @Test
    fun `dictvoice url encodes word and switches accent type`() {
        assertEquals(
            "https://dict.youdao.com/dictvoice?audio=well-known&type=2",
            youdaoDictVoiceUrl("well-known")
        )
        assertTrue(youdaoDictVoiceUrl("hello", us = false).endsWith("type=1"))
    }

    // ---------- buildHighlightedContext ----------

    @Test
    fun `context highlight keeps text intact and marks the target word`() {
        val sentence = "The quick brown fox jumps over the lazy dog."
        val result = buildHighlightedContext(sentence, "fox", Color.Black, Color.Red)
        assertEquals(sentence, result.text)
        // 排除覆盖全文的基础色 span，剩下的高亮段应精确命中目标词
        val highlights = result.spanStyles.filter { it.start != 0 || it.end != sentence.length }
        assertEquals(1, highlights.size)
        assertEquals("fox", sentence.substring(highlights.first().start, highlights.first().end))
    }

    @Test
    fun `no match leaves sentence plain`() {
        val sentence = "Nothing to see here."
        val result = buildHighlightedContext(sentence, "fox", Color.Black, Color.Red)
        assertEquals(sentence, result.text)
        assertTrue(result.spanStyles.isEmpty())
    }
}
