package com.wxkzd.yuanlu.feature.learningpath

import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.LearningPathEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 详情页播放队列纯逻辑测试（LearningPathDetailViewModel 无法在 JVM 上构造——
 * 依赖 ExoPlayer，与 PlayerViewModel 同理不做实例化测试；核心规则提取为伴生函数在此覆盖）。
 */
class LearningPathDetailLogicTest {

    private fun item(id: String, exclusive: Boolean = false) = LearningPathEpisode(
        itemId = id.hashCode(),
        order = 0,
        episode = Episode(
            episodeid = id,
            title = id,
            isExclusive = exclusive,
            duration = 600
        )
    )

    @Test
    fun `free user queue drops exclusive episodes and reports skipped count`() {
        val items = listOf(item("a"), item("b", exclusive = true), item("c"))

        val (queue, skipped) = LearningPathDetailViewModel.buildPlayQueue(items, role = "USER")

        assertEquals(listOf("a", "c"), queue.map { it.episodeid })
        assertEquals(1, skipped)
    }

    @Test
    fun `premium and admin keep exclusive episodes`() {
        val items = listOf(item("a"), item("b", exclusive = true))

        val (premiumQueue, premiumSkipped) = LearningPathDetailViewModel.buildPlayQueue(items, "PREMIUM")
        val (adminQueue, adminSkipped) = LearningPathDetailViewModel.buildPlayQueue(items, "ADMIN")

        assertEquals(2, premiumQueue.size)
        assertEquals(0, premiumSkipped)
        assertEquals(2, adminQueue.size)
        assertEquals(0, adminSkipped)
    }

    @Test
    fun `all-exclusive path for free user yields empty queue`() {
        val items = listOf(item("a", exclusive = true), item("b", exclusive = true))

        val (queue, skipped) = LearningPathDetailViewModel.buildPlayQueue(items, role = null)

        assertTrue(queue.isEmpty())
        assertEquals(2, skipped)
    }

    @Test
    fun `queue order follows path item order`() {
        val items = listOf(item("3"), item("1"), item("2"))

        val (queue, _) = LearningPathDetailViewModel.buildPlayQueue(items, "USER")

        assertEquals(listOf("3", "1", "2"), queue.map { it.episodeid })
    }

    @Test
    fun `resume position skips intro and treats near-finish as restart`() {
        assertEquals(0L, LearningPathDetailViewModel.resumePositionMs(Episode("e", "t", duration = 600, progressSeconds = 20)))
        assertEquals(120_000L, LearningPathDetailViewModel.resumePositionMs(Episode("e", "t", duration = 600, progressSeconds = 120)))
        // 距结尾 15s 内视为已听完，从头播
        assertEquals(0L, LearningPathDetailViewModel.resumePositionMs(Episode("e", "t", duration = 600, progressSeconds = 590)))
    }
}
