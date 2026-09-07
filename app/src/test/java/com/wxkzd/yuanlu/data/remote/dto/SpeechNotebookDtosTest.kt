package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 发音弱项族接口 DTO 解析：notebook 聚合 / errors 弱项列表 / leaderboard 达人榜。
 * 重点覆盖 Prisma 均值小数（avgOverall 83.4 → 83）、试用切片字段与字幕补齐字段缺省。
 */
class SpeechNotebookDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `notebook parses profile stats and trial-sliced errors`() {
        val payload = """
            {
              "success": true,
              "data": {
                "isPremium": false,
                "weakThreshold": 80,
                "profile": {
                  "evalCount": 128,
                  "avgOverall": 83.4,
                  "avgAccuracy": 84.1,
                  "avgFluency": 81.2,
                  "avgIntegrity": 85.9,
                  "avgSpeed": 108.6,
                  "speedFitScore": 97.0,
                  "cefrLevel": "B2"
                },
                "phonemeStats": [
                  { "phoneme": "θ", "avgScore": 45, "count": 12, "lowScoreCount": 9 },
                  { "phoneme": "ð", "avgScore": 52, "count": 8, "lowScoreCount": 5 }
                ],
                "totalErrors": 6,
                "errors": [
                  {
                    "recognitionid": 9001,
                    "episodeid": "ep-1",
                    "episode": { "title": "Climate Change", "coverUrl": "https://oss/signed.jpg" },
                    "targetText": "The weather is quite changing.",
                    "targetStartTime": 12,
                    "accuracyScore": 35.2,
                    "overallScore": 41.7,
                    "speed": 96.3,
                    "recognitionDate": "2026-09-05T10:00:00.000Z",
                    "unknownField": true
                  }
                ]
              }
            }
        """.trimIndent()

        val domain = json.decodeFromString<NotebookResponseDto>(payload).data!!.toDomain()

        // 画像：小数均值不取整入库（展示层再 round），CEFR 透传
        assertEquals(128, domain.profile.evalCount)
        assertEquals(83.4, domain.profile.avgOverall!!, 0.001)
        assertEquals("B2", domain.profile.cefrLevel)
        // 音素统计：均分升序保留（最弱在前）
        assertEquals("θ", domain.phonemeStats.first().phoneme)
        assertEquals(9, domain.phonemeStats.first().lowScoreCount)
        // 试用切片：非会员 errors 为切片（本例 1 条）/ 总量 6 → 锁定 5 条
        assertFalse(domain.isPremium)
        assertEquals(6, domain.totalErrors)
        assertEquals(1, domain.errors.size)
        assertEquals(5, domain.lockedCount)
        // 弱项记录：得分四舍五入、整体封面/标题透传
        val record = domain.errors.first()
        assertEquals(42, record.lastScore)
        assertEquals(35, record.accuracyScore)
        assertEquals("Climate Change", record.episodeTitle)
        assertEquals("/θ/", domain.weakestPhoneme)
    }

    @Test
    fun `weak record builds card subtitle with fallback end`() {
        val payload = """
            {
              "success": true,
              "data": [
                {
                  "recognitionid": 5,
                  "episodeid": "ep-2",
                  "episode": { "title": "T", "audioUrl": "https://oss/audio.mp3" },
                  "targetText": "Hello world",
                  "targetStartTime": 7,
                  "subtitleTextCn": "你好世界",
                  "subtitleId": 88,
                  "accuracyScore": 60.0,
                  "overallScore": 65.4
                }
              ]
            }
        """.trimIndent()

        val records = json.decodeFromString<ErrorsResponseDto>(payload).data.map { it.toDomain() }
        val record = records.first()

        // Web mockSubtitle 口径：id=subtitleId、无 subtitleEnd 时 start+3 兜底
        val subtitle = record.toSubtitle()
        assertEquals(88, subtitle.id)
        assertEquals("Hello world", subtitle.textEn)
        assertEquals("你好世界", subtitle.textCn)
        assertEquals(7.0, subtitle.start, 0.001)
        assertEquals(10.0, subtitle.end, 0.001)
        // "最近得分"入口匹配口径所需字段
        val practice = record.toPracticeRecord()
        assertEquals(88, practice.subtitleId)
        assertEquals(65, practice.bestScore)
        assertEquals("Hello world", practice.targetText)
    }

    @Test
    fun `leaderboard parses entries and my rank with defaults`() {
        val payload = """
            {
              "success": true,
              "data": {
                "period": "weekly",
                "metric": "count",
                "entries": [
                  { "userid": "u1", "nickname": "学霸", "avatar": "https://oss/a.jpg", "evalCount": 42, "avgScore": 91 },
                  { "userid": "u2", "nickname": "用户_8000", "avatar": "", "evalCount": 30, "avgScore": 85 }
                ],
                "me": { "rank": 1, "evalCount": 42, "avgScore": 91 }
              }
            }
        """.trimIndent()

        val domain = json.decodeFromString<LeaderboardResponseDto>(payload).data!!.toDomain()

        assertEquals(
            com.wxkzd.yuanlu.domain.model.LeaderboardPeriod.WEEKLY,
            domain.period
        )
        assertEquals(com.wxkzd.yuanlu.domain.model.LeaderboardMetric.COUNT, domain.metric)
        assertEquals(2, domain.entries.size)
        assertEquals("学霸", domain.entries.first().nickname)
        assertEquals(1, domain.me!!.rank)
        assertEquals(91, domain.me.avgScore)
    }

    @Test
    fun `leaderboard tolerates null me and defaults period metric`() {
        val payload = """{"success":true,"data":{"entries":[]}}"""
        val domain = json.decodeFromString<LeaderboardResponseDto>(payload).data!!.toDomain()

        // 后端默认 weekly/score；未上榜 me 为 null
        assertEquals(com.wxkzd.yuanlu.domain.model.LeaderboardPeriod.WEEKLY, domain.period)
        assertEquals(com.wxkzd.yuanlu.domain.model.LeaderboardMetric.SCORE, domain.metric)
        assertTrue(domain.entries.isEmpty())
        assertNull(domain.me)
    }
}
