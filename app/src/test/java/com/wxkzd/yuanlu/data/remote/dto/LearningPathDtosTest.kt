package com.wxkzd.yuanlu.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 学习路径 DTO 解析：信封/摘要/详情（嵌套剧集 + 播客名 + 收听态）/搜索结果 */
class LearningPathDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `parses summary list envelope with progress and cover`() {
        val payload = """
            {
              "success": true,
              "data": [
                {
                  "pathid": 7,
                  "pathName": "词汇学习",
                  "description": "从入门到进阶",
                  "isPublic": true,
                  "itemCount": 12,
                  "coverUrl": "https://oss/signed.jpg",
                  "creatorName": "wxk",
                  "creationAt": "2026-09-01T08:00:00.000Z",
                  "progress": 66,
                  "isOfficial": false
                }
              ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<LearningPathsResponseDto>(payload)

        assertTrue(dto.success)
        val domain = dto.data.single().toDomain()
        assertEquals(7, domain.pathid)
        assertEquals("词汇学习", domain.pathName)
        assertEquals(12, domain.itemCount)
        assertEquals(66, domain.progress)
        assertTrue(domain.isPublic)
        assertFalse(domain.isOfficial)
    }

    @Test
    fun `parses detail with items episode snapshot and listening state`() {
        val payload = """
            {
              "success": true,
              "data": {
                "pathid": 7,
                "userid": "user-1",
                "pathName": "词汇学习",
                "description": null,
                "isPublic": false,
                "coverUrl": "https://oss/first-episode.jpg",
                "creatorName": "wxk",
                "items": [
                  {
                    "id": 101,
                    "episodeid": "ep-1",
                    "order": 0,
                    "addedAt": "2026-09-01T08:00:00.000Z",
                    "episode": {
                      "episodeid": "ep-1",
                      "title": "Climate change",
                      "coverUrl": "https://oss/cover.jpg",
                      "audioUrl": "https://oss/audio.mp3",
                      "duration": 372,
                      "isExclusive": true,
                      "podcast": { "podcastid": "p1", "title": "6 Minute English" },
                      "progressSeconds": 101.767,
                      "isFinished": false
                    }
                  }
                ]
              }
            }
        """.trimIndent()

        val dto = json.decodeFromString<LearningPathDetailResponseDto>(payload)
        val domain = dto.data!!.toDomain(isOwner = true)

        assertEquals("user-1", domain.userid)
        assertTrue(domain.isOwner)
        val item = domain.items.single()
        assertEquals(101, item.itemId)
        assertEquals("ep-1", item.episode.episodeid)
        assertEquals("6 Minute English", item.episode.podcastTitle)
        assertTrue(item.episode.isExclusive)
        // Prisma Float 进度沿 EpisodeDto.toDomain 的 toInt() 口径截断
        assertEquals(101, item.episode.progressSeconds)
        assertFalse(item.episode.isFinished)
    }

    @Test
    fun `detail item without episode snapshot falls back to stub by episodeid`() {
        val payload = """
            {
              "success": true,
              "data": {
                "pathid": 7,
                "pathName": "词汇学习",
                "items": [ { "id": 9, "episodeid": "ep-gone" } ]
              }
            }
        """.trimIndent()

        val domain = json.decodeFromString<LearningPathDetailResponseDto>(payload).data!!.toDomain(false)

        assertEquals("ep-gone", domain.items.single().episode.episodeid)
        assertFalse(domain.isOwner)
    }

    @Test
    fun `mutation envelope carries business failure message`() {
        val payload = """{ "success": false, "message": "该剧集已在列表中" }"""

        val dto = json.decodeFromString<LearningPathMutationResponseDto>(payload)

        assertFalse(dto.success)
        assertEquals("该剧集已在列表中", dto.message)
    }

    @Test
    fun `parses search results envelope`() {
        val payload = """
            {
              "success": true,
              "data": [
                {
                  "id": "ep-2",
                  "title": "How to talk about AI",
                  "thumbnailUrl": "https://oss/ep2.jpg",
                  "author": "The English We Speak",
                  "duration": 180
                }
              ]
            }
        """.trimIndent()

        val domain = json.decodeFromString<PathSearchResponseDto>(payload).data.single().toDomain()

        assertEquals("ep-2", domain.episodeid)
        assertEquals("The English We Speak", domain.author)
        assertEquals(180, domain.duration)
    }

    @Test
    fun `upsert request serializes optional description as null`() {
        val dto = LearningPathUpsertRequestDto(pathName = "听力", description = null, isPublic = true)
        val text = json.encodeToString(LearningPathUpsertRequestDto.serializer(), dto)

        assertTrue(text.contains("\"pathName\":\"听力\""))
        assertTrue(text.contains("\"isPublic\":true"))
    }
}
