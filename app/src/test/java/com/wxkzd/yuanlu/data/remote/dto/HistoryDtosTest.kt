package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** 历史接口 DTO 解析：重点覆盖 Prisma Float 小数进度（progressSeconds: 101.767） */
class HistoryDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `parses fractional progress seconds and unknown fields`() {
        val payload = """
            {
              "success": true,
              "data": {
                "items": [
                  {
                    "historyid": 323,
                    "listenAt": "2026-09-06T08:30:00.123Z",
                    "progressSeconds": 101.767,
                    "isFinished": false,
                    "episode": {
                      "id": "ep1",
                      "title": "Climate change",
                      "author": "BBC Learning English",
                      "category": "6 Minute English",
                      "thumbnailUrl": "https://oss/cover.jpg",
                      "duration": "6:12",
                      "durationSeconds": 372,
                      "level": "General"
                    }
                  }
                ],
                "total": 1,
                "hasMore": false
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<HistoryResponseDto>(payload)
        val page = dto.data!!.toDomain()

        assertEquals(1, page.items.size)
        // 小数进度四舍五入为整秒
        assertEquals(102, page.items.first().progressSeconds)
        assertEquals("6 Minute English", page.items.first().episode.category)
        assertEquals(false, page.items.first().isFinished)
        assertEquals(false, page.hasMore)
    }

    @Test
    fun `integer progress seconds still parse`() {
        val payload = """
            {"success":true,"data":{"items":[{"historyid":1,"listenAt":"2026-09-06T01:00:00.000Z",
            "progressSeconds":60,"isFinished":true,
            "episode":{"id":"e","title":"t","author":"a","category":"c",
            "duration":"1:00","durationSeconds":60}}],"total":1,"hasMore":true}}
        """.trimIndent()

        val page = json.decodeFromString<HistoryResponseDto>(payload).data!!.toDomain()
        assertEquals(60, page.items.first().progressSeconds)
        assertEquals(true, page.items.first().isFinished)
        assertEquals(true, page.hasMore)
    }
}
