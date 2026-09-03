package com.wxkzd.yuanlu.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 封面 URL 有效性过滤与详情页兜底链（2023-05-03 前老剧集封面空白修复）。
 */
class CoverUrlTest {

    companion object {
        private const val SIGNED_EPISODE_COVER =
            "https://wxkzd.oss-cn-beijing.aliyuncs.com/yuanlu/podcastes/episodes/covers/1776432969557_o9w8x4m5u1x.jpg?OSSAccessKeyId=LTAI5tH8MQqhgrguyUkkAgoJ&Expires=1788451873&Signature=jbsZXZlpQSmH54MNyDmXG25ExXU%3D"
        private const val UNSIGNED_EPISODE_COVER =
            "https://wxkzd.oss-cn-beijing.aliyuncs.com/yuanlu/podcastes/episodes/covers/1776432969557_o9w8x4m5u1x.jpg"
        private const val SIGNED_PODCAST_COVER =
            "https://wxkzd.oss-cn-beijing.aliyuncs.com/yuanlu/podcastes/covers/1786334071318_wi85mhkb9os.jpg?OSSAccessKeyId=LTAI5tH8MQqhgrguyUkkAgoJ&Expires=1788451873&Signature=jbsZXZlpQSmH54MNyDmXG25ExXU%3D"
    }

    // ---------- isLoadableCoverUrl：空值/占位/非法 ----------

    @Test
    fun `null blank and default cover are rejected`() {
        assertFalse(isLoadableCoverUrl(null))
        assertFalse(isLoadableCoverUrl(""))
        assertFalse(isLoadableCoverUrl("   "))
        assertFalse(isLoadableCoverUrl(DEFAULT_COVER))
    }

    @Test
    fun `non http and malformed urls are rejected`() {
        assertFalse(isLoadableCoverUrl("ftp://example.com/a.jpg"))
        assertFalse(isLoadableCoverUrl("/yuanlu/covers/a.jpg"))
        assertFalse(isLoadableCoverUrl("not a url"))
        assertFalse(isLoadableCoverUrl("javascript:alert(1)"))
    }

    @Test
    fun `signed oss url is loadable`() {
        assertTrue(isLoadableCoverUrl(SIGNED_EPISODE_COVER))
        assertTrue(isLoadableCoverUrl(SIGNED_PODCAST_COVER))
    }

    @Test
    fun `unsigned private oss url is rejected because it always returns 403`() {
        assertFalse(isLoadableCoverUrl(UNSIGNED_EPISODE_COVER))
    }

    @Test
    fun `non oss https url never requires a signature`() {
        assertTrue(isLoadableCoverUrl("https://cdn.example.com/a.jpg"))
        assertTrue(isLoadableCoverUrl("http://10.0.2.2:3000/a.jpg"))
    }

    // ---------- resolveEpisodeCoverUrl：episode → podcast → null 兜底链 ----------

    @Test
    fun `episode cover wins when loadable`() {
        assertEquals(
            SIGNED_EPISODE_COVER,
            resolveEpisodeCoverUrl(SIGNED_EPISODE_COVER, SIGNED_PODCAST_COVER)
        )
    }

    @Test
    fun `falls back to podcast cover when episode cover is missing or unloadable`() {
        assertEquals(SIGNED_PODCAST_COVER, resolveEpisodeCoverUrl(null, SIGNED_PODCAST_COVER))
        assertEquals(SIGNED_PODCAST_COVER, resolveEpisodeCoverUrl("", SIGNED_PODCAST_COVER))
        assertEquals(SIGNED_PODCAST_COVER, resolveEpisodeCoverUrl(UNSIGNED_EPISODE_COVER, SIGNED_PODCAST_COVER))
        assertEquals(SIGNED_PODCAST_COVER, resolveEpisodeCoverUrl(DEFAULT_COVER, SIGNED_PODCAST_COVER))
    }

    @Test
    fun `returns null when both covers unusable so CoverImage renders letter placeholder`() {
        assertEquals(null, resolveEpisodeCoverUrl(null, null))
        assertEquals(null, resolveEpisodeCoverUrl("", ""))
        assertEquals(null, resolveEpisodeCoverUrl(UNSIGNED_EPISODE_COVER, null))
        assertEquals(null, resolveEpisodeCoverUrl(UNSIGNED_EPISODE_COVER, "not a url"))
    }

    // ---------- coverCandidates：加载期逐级回退候选链 ----------

    @Test
    fun `keeps order, filters invalid and dedupes`() {
        assertEquals(
            listOf(SIGNED_EPISODE_COVER, SIGNED_PODCAST_COVER),
            coverCandidates(SIGNED_EPISODE_COVER, SIGNED_PODCAST_COVER)
        )
        // 主封面无效（未签名/空/占位）时跳过，专辑封面成为唯一候选
        assertEquals(
            listOf(SIGNED_PODCAST_COVER),
            coverCandidates(UNSIGNED_EPISODE_COVER, null, SIGNED_PODCAST_COVER)
        )
        // 完全相同的候选去重，避免同 URL 失败后重复加载
        assertEquals(
            listOf(SIGNED_PODCAST_COVER),
            coverCandidates(SIGNED_PODCAST_COVER, SIGNED_PODCAST_COVER)
        )
    }

    @Test
    fun `empty when all candidates unusable`() {
        assertTrue(coverCandidates(null, "", DEFAULT_COVER, UNSIGNED_EPISODE_COVER).isEmpty())
    }
}
