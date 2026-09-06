package com.wxkzd.yuanlu.feature.favorites

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.domain.model.FavoriteEpisode
import com.wxkzd.yuanlu.domain.model.FavoritesBundle
import com.wxkzd.yuanlu.domain.model.FavoriteSeries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun series(id: String, title: String = id, author: String = "Spotify") = FavoriteSeries(
        id = id,
        title = title,
        author = author,
        episodeCount = 10,
        plays = 1200,
        followers = 30
    )

    private fun episode(id: String, title: String = id, author: String = "Daily Boost") = FavoriteEpisode(
        id = id,
        title = title,
        author = author,
        platform = "Spotify",
        category = "Daily Boost",
        date = "2026/8/1",
        duration = "10:12",
        playCount = 4321,
        favoriteCount = 7,
        podcastId = "p1"
    )

    @Test
    fun `load fills both tabs`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = listOf(series("p1"), series("p2")),
                episodes = listOf(episode("e1"))
            )
        }
        val viewModel = FavoritesViewModel(repo, FavoriteCenter(repo))
        runCurrent()

        assertEquals(2, viewModel.uiState.value.podcasts.size)
        assertEquals(1, viewModel.uiState.value.episodes.size)
        assertEquals(FavoritesTab.PODCASTS, viewModel.uiState.value.activeTab)
        // 收藏数/平台等卡片统计字段随列表透传
        assertEquals(7, viewModel.uiState.value.episodes.first().favoriteCount)
        assertEquals("Spotify", viewModel.uiState.value.episodes.first().platform)
    }

    @Test
    fun `search filters podcasts by title or author case insensitively`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = listOf(series("p1", title = "All Ears English"), series("p2", author = "TED")),
                episodes = emptyList()
            )
        }
        val viewModel = FavoritesViewModel(repo, FavoriteCenter(repo))
        runCurrent()

        viewModel.updateSearchQuery("ears")
        assertEquals(listOf("p1"), viewModel.uiState.value.filteredPodcasts.map { it.id })

        viewModel.updateSearchQuery("ted")
        assertEquals(listOf("p2"), viewModel.uiState.value.filteredPodcasts.map { it.id })

        viewModel.updateSearchQuery("")
        assertEquals(2, viewModel.uiState.value.filteredPodcasts.size)
    }

    @Test
    fun `search filters episodes by title or author`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = emptyList(),
                episodes = listOf(episode("e1", title = "Speak Now"), episode("e2", author = "BBC"))
            )
        }
        val viewModel = FavoritesViewModel(repo, FavoriteCenter(repo))
        runCurrent()

        viewModel.updateSearchQuery("now")
        assertEquals(listOf("e1"), viewModel.uiState.value.filteredEpisodes.map { it.id })

        viewModel.updateSearchQuery("bbc")
        assertEquals(listOf("e2"), viewModel.uiState.value.filteredEpisodes.map { it.id })
    }

    @Test
    fun `remove podcast optimistically filters list and hits server`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = listOf(series("p1"), series("p2")),
                episodes = emptyList()
            )
        }
        val center = FavoriteCenter(repo)
        val viewModel = FavoritesViewModel(repo, center)
        runCurrent()

        viewModel.removePodcast("p1")
        // 乐观移除：立即从列表消失
        assertEquals(listOf("p2"), viewModel.uiState.value.podcasts.map { it.id })

        runCurrent()
        // 服务端删除已发出，全局中心状态翻转为未收藏
        assertEquals(listOf("p1"), repo.removedPodcastFavorites)
        assertEquals(
            false,
            center.knownState(FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, "p1"))
        )
        // revision 变化触发重取，服务端事实回填列表
        assertEquals(listOf("p2"), viewModel.uiState.value.podcasts.map { it.id })
    }

    @Test
    fun `remove episode optimistically filters list and hits server`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = emptyList(),
                episodes = listOf(episode("e1"), episode("e2"))
            )
        }
        val center = FavoriteCenter(repo)
        val viewModel = FavoritesViewModel(repo, center)
        runCurrent()

        viewModel.removeEpisode("e1")
        assertEquals(listOf("e2"), viewModel.uiState.value.episodes.map { it.id })

        runCurrent()
        assertEquals(listOf("e1"), repo.removedEpisodeFavorites)
    }

    @Test
    fun `external favorite change reloads list through revision`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(
                podcasts = listOf(series("p1"), series("p2")),
                episodes = emptyList()
            )
        }
        val center = FavoriteCenter(repo)
        val viewModel = FavoritesViewModel(repo, center)
        runCurrent()
        assertEquals(2, viewModel.uiState.value.podcasts.size)

        // 模拟详情页收藏了新播客后服务端列表变化
        repo.favorites = FavoritesBundle(
            podcasts = listOf(series("p1"), series("p2"), series("p3")),
            episodes = emptyList()
        )
        center.togglePodcast("p3")
        runCurrent()

        assertEquals(3, viewModel.uiState.value.podcasts.size)
    }

    @Test
    fun `failed removal rolls list back via revision reload`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            favorites = FavoritesBundle(podcasts = listOf(series("p1")), episodes = emptyList())
            favoriteMutationError = com.wxkzd.yuanlu.core.network.Result.Error(500, "操作失败")
        }
        val center = FavoriteCenter(repo)
        val viewModel = FavoritesViewModel(repo, center)
        runCurrent()

        viewModel.removePodcast("p1")
        // 乐观移除后失败回滚：revision 触发重取，服务端仍收藏 → 条目恢复（对齐 Web 回滚）
        runCurrent()
        assertEquals(listOf("p1"), viewModel.uiState.value.podcasts.map { it.id })
    }

    @Test
    fun `selectTab switches active tab`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val viewModel = FavoritesViewModel(repo, FavoriteCenter(repo))
        runCurrent()

        viewModel.selectTab(FavoritesTab.EPISODES)
        assertEquals(FavoritesTab.EPISODES, viewModel.uiState.value.activeTab)
    }
}
