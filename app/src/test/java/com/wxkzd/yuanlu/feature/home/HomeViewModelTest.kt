package com.wxkzd.yuanlu.feature.home

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun episode(id: String) = Episode(episodeid = id, title = "Episode $id")

    @Test
    fun `initial load fetches first page and editor picks`() = runTest(dispatcher) {
        val repository = FakeContentRepository(
            episodesByPage = mapOf(1 to List(20) { episode("p1-$it") }),
            podcasts = listOf(
                Podcast(podcastid = "a", title = "A", isEditorPick = true),
                Podcast(podcastid = "b", title = "B", isEditorPick = false)
            )
        )
        val viewModel = HomeViewModel(repository)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(20, state.episodes.size)
        assertEquals(listOf("a"), state.editorPicks.map { it.podcastid })
        assertEquals(1, state.page)
        assertFalse(state.endReached)
    }

    @Test
    fun `loadMore appends next page until a short page arrives`() = runTest(dispatcher) {
        val repository = FakeContentRepository(
            episodesByPage = mapOf(
                1 to List(20) { episode("p1-$it") },
                2 to List(20) { episode("p2-$it") },
                3 to List(7) { episode("p3-$it") }
            )
        )
        val viewModel = HomeViewModel(repository)
        runCurrent()

        viewModel.loadMore()
        runCurrent()
        var state = viewModel.uiState.value
        assertEquals(40, state.episodes.size)
        assertEquals(2, state.page)
        assertFalse(state.endReached)

        viewModel.loadMore()
        runCurrent()
        state = viewModel.uiState.value
        assertEquals(47, state.episodes.size)
        assertTrue(state.endReached)
    }

    @Test
    fun `loadMore is a no-op after end reached`() = runTest(dispatcher) {
        val repository = FakeContentRepository(
            episodesByPage = mapOf(1 to List(5) { episode("p1-$it") })
        )
        val viewModel = HomeViewModel(repository)
        runCurrent()
        assertTrue(viewModel.uiState.value.endReached)

        viewModel.loadMore()
        runCurrent()
        assertEquals(5, viewModel.uiState.value.episodes.size)
        assertEquals(1, repository.latestEpisodesCalls)
    }

    @Test
    fun `initial error surfaces message`() = runTest(dispatcher) {
        val repository = FakeContentRepository(failure = Result.Error(500, "boom"))
        val viewModel = HomeViewModel(repository)
        runCurrent()

        assertEquals("boom", viewModel.uiState.value.error)
    }
}

private class FakeContentRepository(
    private val episodesByPage: Map<Int, List<Episode>> = emptyMap(),
    private val podcasts: List<Podcast> = emptyList(),
    private val failure: Result.Error? = null
) : ContentRepository {

    var latestEpisodesCalls = 0

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> {
        latestEpisodesCalls++
        failure?.let { return it }
        return Result.Success(episodesByPage[page].orEmpty())
    }

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
        failure ?: Result.Success(podcasts)

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

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> = com.wxkzd.yuanlu.core.network.Result.Success(Unit)

    override suspend fun translate(text: String): Result<String> =
        Result.Error(0, "not implemented in fake")
}
