package com.wxkzd.yuanlu.feature.pronunciation

import com.wxkzd.yuanlu.domain.model.PhonemeStat
import com.wxkzd.yuanlu.domain.model.SpeechProfile
import org.junit.Assert.assertEquals
import org.junit.Test

/** 弱项本纯逻辑：日期中文化 / 得分档位 / 雷达数据派生 */
class PronunciationUtilsTest {

    // ---------- formatZhDate（Web toLocaleDateString zh-CN long 口径） ----------

    @Test
    fun `formats iso datetime to zh long date`() {
        assertEquals(
            "2026年9月7日",
            PronunciationUtils.formatZhDate("2026-09-07T01:02:03.456Z")
        )
    }

    @Test
    fun `formats offset datetime with zone conversion`() {
        // 2026-09-06T22:30+08:00 落在 9月6/7日之间（随系统时区漂移），仅断言合法解析形态
        val result = PronunciationUtils.formatZhDate("2026-09-06T22:30:00+08:00")
        assert(Regex("2026年9月[67]日").matches(result)) { "unexpected: $result" }
    }

    @Test
    fun `formats local datetime without zone`() {
        assertEquals(
            "2025年12月31日",
            PronunciationUtils.formatZhDate("2025-12-31T23:59:59")
        )
    }

    @Test
    fun `formats bare date and falls back on garbage`() {
        assertEquals("2026年1月2日", PronunciationUtils.formatZhDate("2026-01-02"))
        assertEquals("not-a-date", PronunciationUtils.formatZhDate("not-a-date"))
        assertEquals("", PronunciationUtils.formatZhDate(null))
        assertEquals("", PronunciationUtils.formatZhDate(""))
    }

    // ---------- classifyScore（Web ≥80 绿 / ≥60 黄 / 其余红） ----------

    @Test
    fun `classifies score tones by web thresholds`() {
        assertEquals(ScoreTone.GOOD, PronunciationUtils.classifyScore(80))
        assertEquals(ScoreTone.GOOD, PronunciationUtils.classifyScore(100))
        assertEquals(ScoreTone.MID, PronunciationUtils.classifyScore(60))
        assertEquals(ScoreTone.MID, PronunciationUtils.classifyScore(79))
        assertEquals(ScoreTone.BAD, PronunciationUtils.classifyScore(59))
        assertEquals(ScoreTone.BAD, PronunciationUtils.classifyScore(0))
    }

    // ---------- 雷达数据派生 ----------

    @Test
    fun `profile radar dims cover five dimensions with zero fallback`() {
        val profile = SpeechProfile(
            evalCount = 3,
            avgAccuracy = 84.6,
            avgFluency = 81.2,
            avgIntegrity = null,
            speedFitScore = 97.4,
            avgOverall = 83.0
        )
        val dims = profile.radarDims
        assertEquals(5, dims.size)
        assertEquals(listOf("准确度", "流利度", "完整度", "语速适配", "综合表现"), dims.map { it.first })
        assertEquals(listOf(85, 81, 0, 97, 83), dims.map { it.second })
        assertEquals(true, profile.hasData)
    }

    @Test
    fun `phoneme radar takes six weakest with slash labels`() {
        val stats = ('a'..'j').mapIndexed { i, c ->
            PhonemeStat(phoneme = c.toString(), avgScore = 90 - i, count = 5, lowScoreCount = 1)
        }
        val points = PronunciationUtils.phonemeRadarPoints(stats)
        assertEquals(6, points.size)
        assertEquals("/a/" to 90f, points.first())
        assertEquals("/f/" to 85f, points.last())
        // 不足 3 项时由 UI 层渲染空态
        assertEquals(2, PronunciationUtils.phonemeRadarPoints(stats.take(2)).size)
    }

    // ---------- stripIpaSlashes（音标文本模式：剥离词典音标斜杠 + 过滤弱读变体提示） ----------

    @Test
    fun `strips slashes wrapping a single word phonetic`() {
        assertEquals("sʌm", PronunciationUtils.stripIpaSlashes("/sʌm/"))
        assertEquals("'pi:pəl", PronunciationUtils.stripIpaSlashes("/'pi:pəl/"))
    }

    @Test
    fun `drops before-vowels annotation together with its slashes`() {
        // 提示段连同前导空白与内部斜杠整体移除，只留主干音标
        assertEquals("ðə", PronunciationUtils.stripIpaSlashes("/ðə/ (before vowels: /ði/)"))
        assertEquals("ðə", PronunciationUtils.stripIpaSlashes("/ðə/  (before vowels: /ði/) "))
        assertEquals("ði", PronunciationUtils.stripIpaSlashes("/ði/(before vowels: /ðiː/)"))
    }

    @Test
    fun `annotation-only phonetic collapses to empty`() {
        assertEquals("", PronunciationUtils.stripIpaSlashes("(before vowels: /ði/)"))
    }

    @Test
    fun `keeps slash-free phonetic unchanged and trims blanks`() {
        assertEquals("laɪk", PronunciationUtils.stripIpaSlashes("laɪk"))
        assertEquals("gəʊ", PronunciationUtils.stripIpaSlashes(" /gəʊ/ "))
    }
}
