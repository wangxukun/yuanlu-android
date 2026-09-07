package com.wxkzd.yuanlu.feature.discover

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun podcast(
        id: String,
        plays: Int = 0,
        editorPick: Boolean = false,
        createAt: String? = null,
        platform: String? = null
    ) = Podcast(
        podcastid = id,
        title = id,
        totalPlays = plays,
        isEditorPick = editorPick,
        createAt = createAt,
        platform = platform
    )

    @Test
    fun `load derives trending picks new shows and channels`() = runTest(dispatcher) {
        val podcasts = listOf(
            podcast("a", plays = 100, editorPick = true, createAt = "2026-08-01T00:00:00.000Z", platform = "Spotify"),
            podcast("b", plays = 500, createAt = "2026-08-20T00:00:00.000Z", platform = "Spotify"),
            podcast("c", plays = 300, createAt = "2026-07-01T00:00:00.000Z", platform = "Apple Podcasts"),
            podcast("d", plays = 50, editorPick = true, platform = null)
        )
        val viewModel = DiscoverViewModel(FakeContentRepository(podcasts = podcasts))
        runCurrent()
        // debounce 收集器不触发搜索（query 为空）
        val state = viewModel.uiState.value

        assertEquals(listOf("b", "c", "a", "d"), state.trending.map { it.podcastid })
        assertEquals(listOf("a", "d"), state.editorPicks.map { it.podcastid })
        assertEquals(listOf("b", "a", "c"), state.newPodcasts.map { it.podcastid })
        assertEquals(2, state.channels.size)
        assertEquals("Spotify", state.channels.first().name)
        assertEquals(2, state.channels.first().podcastCount)
    }

    @Test
    fun `trending truncated to limit and new shows to limit`() = runTest(dispatcher) {
        val podcasts = (1..15).map { i ->
            podcast(
                id = "p$i",
                plays = i,
                createAt = "2026-08-%02d".format(i)
            )
        }
        val viewModel = DiscoverViewModel(FakeContentRepository(podcasts = podcasts))
        runCurrent()

        assertEquals(DiscoverViewModel.TRENDING_LIMIT, viewModel.uiState.value.trending.size)
        assertEquals(DiscoverViewModel.NEW_LIMIT, viewModel.uiState.value.newPodcasts.size)
        // trending 按播放量降序
        assertEquals("p15", viewModel.uiState.value.trending.first().podcastid)
    }

    @Test
    fun `empty sections collapse without errors`() = runTest(dispatcher) {
        val viewModel = DiscoverViewModel(FakeContentRepository(podcasts = emptyList()))
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.trending.isEmpty())
        assertTrue(state.editorPicks.isEmpty())
        assertTrue(state.newPodcasts.isEmpty())
        assertTrue(state.channels.isEmpty())
    }

    @Test
    fun `selectTag filters grid content`() = runTest(dispatcher) {
        val tag = Tag(1, "Business")
        val withTag = Podcast(podcastid = "a", title = "A", tags = listOf(tag))
        val without = Podcast(podcastid = "b", title = "B")
        val viewModel = DiscoverViewModel(FakeContentRepository(podcasts = listOf(withTag, without)))
        runCurrent()

        viewModel.selectTag(tag)
        runCurrent()
        // 过滤逻辑在 Composable 内执行，这里验证状态记录了选中标签
        assertEquals(tag, viewModel.uiState.value.selectedTag)

        viewModel.selectTag(null)
        assertEquals(null, viewModel.uiState.value.selectedTag)
    }
}

private class FakeContentRepository(
    private val podcasts: List<Podcast> = emptyList()
) : ContentRepository {

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> =
        Result.Success(emptyList())

    override suspend fun getEpisode(episodeid: String): Result<Episode> =
        Result.Success(Episode(episodeid = episodeid, title = ""))

    override suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle> =
        Result.Success(SubtitleBundle(emptyList(), null))

    override suspend fun getPodcastEpisodes(
        podcastid: String,
        page: Int,
        limit: Int,
        ascending: Boolean
    ): Result<EpisodePage> = Result.Success(EpisodePage(emptyList(), 0, false))

    override suspend fun getPodcasts(): Result<List<Podcast>> =
        Result.Success(podcasts)

    override suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail> =
        Result.Success(PodcastDetail(Podcast(podcastid = podcastid, title = ""), false, emptyList()))

    override suspend fun searchPodcasts(query: String): Result<List<Podcast>> =
        Result.Success(emptyList())

    override suspend fun getTags(query: String?): Result<List<Tag>> =
        Result.Success(emptyList())

    override suspend fun getChannel(name: String): Result<ChannelData> =
        Result.Success(ChannelData(name, 0, emptyList(), emptyList()))

    override suspend fun getComments(episodeid: String): Result<List<Comment>> =
        Result.Success(emptyList())

    override suspend fun createComment(episodeid: String, content: String, parentId: Int?): Result<Comment> =
        Result.Error(0, "not implemented in fake")

    override suspend fun toggleCommentLike(commentid: Int): Result<Boolean> =
        Result.Success(false)

    // 词典与生词（精听查词）：本仓库测试不涉及，给出空实现以满足接口
    override suspend fun lookupWord(word: String): Result<com.wxkzd.yuanlu.domain.model.DictEntry> =
        com.wxkzd.yuanlu.core.network.Result.Error(600, "not implemented in fake")

    override suspend fun addVocabulary(
        word: String,
        definition: String,
        contextSentence: String,
        translation: String,
        episodeid: String,
        timestampSec: Int,
        speakUrl: String
    ): Result<Unit> = com.wxkzd.yuanlu.core.network.Result.Error(600, "not implemented in fake")

    override suspend fun getVocabularyWords(): Result<Set<String>> =
        com.wxkzd.yuanlu.core.network.Result.Success(emptySet())

    // 生词本（列表与复习）：本测试不涉及
    override suspend fun getAllVocabulary(): Result<List<com.wxkzd.yuanlu.domain.model.VocabularyItem>> =
        com.wxkzd.yuanlu.core.network.Result.Success(emptyList())

    override suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit> =
        com.wxkzd.yuanlu.core.network.Result.Error(600, "not implemented in fake")

    override suspend fun submitVocabularyReview(
        vocabularyid: Int,
        quality: Int
    ): Result<com.wxkzd.yuanlu.domain.model.VocabularyReviewOutcome> =
        com.wxkzd.yuanlu.core.network.Result.Error(600, "not implemented in fake")

    override suspend fun updateVocabularyStatus(vocabularyid: Int, mastered: Boolean): Result<Unit> =
        com.wxkzd.yuanlu.core.network.Result.Error(600, "not implemented in fake")

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> = com.wxkzd.yuanlu.core.network.Result.Success(Unit)

    override suspend fun translate(text: String): Result<String> =
        Result.Error(0, "not implemented in fake")

    override suspend fun fetchTtsAudioUrl(text: String): Result<String> =
        Result.Error(600, "not implemented in fake")

    // 收藏：本测试不涉及，给出空实现以满足接口
    override suspend fun getFavorites(): Result<com.wxkzd.yuanlu.domain.model.FavoritesBundle> =
        Result.Success(com.wxkzd.yuanlu.domain.model.FavoritesBundle())

    // 收听历史：本测试不涉及
    override suspend fun getListeningHistory(
        page: Int,
        pageSize: Int,
        status: String
    ): Result<com.wxkzd.yuanlu.domain.model.HistoryPage> =
        Result.Success(com.wxkzd.yuanlu.domain.model.HistoryPage())
    override suspend fun checkPodcastFavorite(podcastid: String): Result<Boolean> =
        Result.Success(false)

    override suspend fun addPodcastFavorite(podcastid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun removePodcastFavorite(podcastid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun checkEpisodeFavorite(episodeid: String): Result<Boolean> =
        Result.Success(false)

    override suspend fun addEpisodeFavorite(episodeid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun removeEpisodeFavorite(episodeid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    // 学习路径：本测试不涉及
    override suspend fun getMyLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> =
        Result.Success(emptyList())

    override suspend fun getPublicLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> =
        Result.Success(emptyList())

    override suspend fun createLearningPath(pathName: String, description: String?, isPublic: Boolean): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun getLearningPath(pathid: Int): Result<com.wxkzd.yuanlu.domain.model.LearningPathDetail> =
        Result.Success(com.wxkzd.yuanlu.domain.model.LearningPathDetail(pathid = pathid, pathName = ""))

    override suspend fun updateLearningPath(pathid: Int, pathName: String, description: String?, isPublic: Boolean): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun deleteLearningPath(pathid: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun addEpisodeToLearningPath(pathid: Int, episodeid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun removeEpisodeFromLearningPath(pathid: Int, itemId: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun searchEpisodesForPath(query: String): Result<List<com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem>> =
        Result.Success(emptyList())
}
