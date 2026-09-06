package com.wxkzd.yuanlu.feature.favorites

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.core.network.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteCenterTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggle podcast from favorited calls remove and flips state`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val center = FavoriteCenter(repo)
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1")
        center.seedIfAbsent(key, true)

        center.togglePodcast("p1")
        runCurrent()

        assertEquals(false, center.knownState(key))
        assertEquals(listOf("p1"), repo.removedPodcastFavorites)
        assertTrue(repo.addedPodcastFavorites.isEmpty())
        assertEquals(1, center.revision.value)
    }

    @Test
    fun `toggle podcast from unfavorited calls insert`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val center = FavoriteCenter(repo)
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1")
        center.seedIfAbsent(key, false)

        center.togglePodcast("p1")
        runCurrent()

        assertEquals(true, center.knownState(key))
        assertEquals(listOf("p1"), repo.addedPodcastFavorites)
        assertTrue(repo.removedPodcastFavorites.isEmpty())
    }

    @Test
    fun `toggle episode routes to episode endpoints`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val center = FavoriteCenter(repo)
        center.seedIfAbsent(FavoriteCenter.Key(FavoriteCenter.Target.EPISODE, "e1"), true)

        center.toggleEpisode("e1")
        runCurrent()

        assertEquals(
            false,
            center.knownState(FavoriteCenter.Key(FavoriteCenter.Target.EPISODE, "e1"))
        )
        assertEquals(listOf("e1"), repo.removedEpisodeFavorites)
        assertTrue(repo.removedPodcastFavorites.isEmpty())
    }

    @Test
    fun `remove podcast without prior seed still deletes on server`() = runTest(dispatcher) {
        // 列表页显式删除语义：中心状态未知时也必须走 delete，而不是被 toggle 误判成 insert
        val repo = FakeContentRepository()
        val center = FavoriteCenter(repo)

        center.removePodcast("p1")
        runCurrent()

        assertEquals(listOf("p1"), repo.removedPodcastFavorites)
        assertTrue(repo.addedPodcastFavorites.isEmpty())
        assertEquals(
            false,
            center.knownState(FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1"))
        )
    }

    @Test
    fun `remove episode without prior seed still deletes on server`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val center = FavoriteCenter(repo)

        center.removeEpisode("e1")
        runCurrent()

        assertEquals(listOf("e1"), repo.removedEpisodeFavorites)
        assertTrue(repo.addedEpisodeFavorites.isEmpty())
    }

    @Test
    fun `failed removal clears pending key so retry is possible`() = runTest(dispatcher) {
        // 在途标记必须随协程结束清除，否则后续点击会被静默吞掉
        val repo = FakeContentRepository().apply {
            favoriteMutationError = Result.Error(500, "操作失败")
        }
        val center = FavoriteCenter(repo)

        center.removePodcast("p1")
        runCurrent()
        assertTrue(center.pendingKeys.value.isEmpty())

        // 修复错误后可再次发起删除
        repo.favoriteMutationError = null
        center.removePodcast("p1")
        runCurrent()
        assertEquals(listOf("p1", "p1"), repo.removedPodcastFavorites)
    }

    @Test
    fun `failed mutation rolls back state and bumps revision`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favoriteMutationError = Result.Error(500, "操作失败")
        }
        val center = FavoriteCenter(repo)
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1")
        center.seedIfAbsent(key, true)

        center.togglePodcast("p1")
        runCurrent()

        // 回滚到原状态
        assertEquals(true, center.knownState(key))
        // 回滚同样驱动列表重取
        assertEquals(1, center.revision.value)
    }

    @Test
    fun `network error rolls back state`() = runTest(dispatcher) {
        val repo = object : FakeContentRepository() {
            override suspend fun removePodcastFavorite(podcastid: String): Result<Unit> =
                Result.NetworkError
        }
        val center = FavoriteCenter(repo)
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1")
        center.seedIfAbsent(key, true)

        center.togglePodcast("p1")
        runCurrent()

        assertEquals(true, center.knownState(key))
    }

    @Test
    fun `seedIfAbsent keeps existing value`() {
        val center = FavoriteCenter(FakeContentRepository())
        val key = FavoriteCenter.Key(FavoriteCenter.Target.EPISODE, "e1")
        center.seedIfAbsent(key, true)
        center.seedIfAbsent(key, false)
        assertEquals(true, center.knownState(key))
    }

    @Test
    fun `clear resets known states`() {
        val center = FavoriteCenter(FakeContentRepository())
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1")
        center.seedIfAbsent(key, true)
        center.clear()
        assertNull(center.knownState(key))
    }

    @Test
    fun `unknown key has null known state`() {
        val center = FavoriteCenter(FakeContentRepository())
        assertNull(center.knownState(FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "nope")))
    }
}
