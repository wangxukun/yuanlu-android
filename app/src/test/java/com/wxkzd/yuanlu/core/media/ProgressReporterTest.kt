package com.wxkzd.yuanlu.core.media

import androidx.media3.common.Player
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.DictEntry
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * ProgressReporter 状态机测试：周期防抖 / 暂停冲刷 / 结尾完成 / 播完一次性 /
 * 切集冲刷 / 游客跳过 / 重复暂停去重（对齐 Web useSaveProgress 行为）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressReporterTest {

    private val token = MutableStateFlow<String?>("jwt-token")
    private lateinit var repo: RecordingRepository

    private fun episode(id: String) = Episode(episodeid = id, title = id, duration = 600)

    @Test
    fun `播放中每15秒上报一次`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(currentEpisode = episode("e1"), isPlaying = true, currentPosition = 1_000)
        runCurrent()
        assertEquals(0, repo.calls.size)

        state.value = state.value.copy(currentPosition = 10_000)
        runCurrent()
        assertEquals(0, repo.calls.size)

        state.value = state.value.copy(currentPosition = 16_000)
        runCurrent()
        assertEquals(listOf(Triple("e1", 16f, false)), repo.calls)

        state.value = state.value.copy(currentPosition = 40_000)
        runCurrent()
        assertEquals(2, repo.calls.size)
    }

    @Test
    fun `暂停时立即上报未完成进度`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(currentEpisode = episode("e1"), isPlaying = true, currentPosition = 5_000)
        runCurrent()
        assertEquals(0, repo.calls.size)

        state.value = state.value.copy(isPlaying = false, currentPosition = 6_000)
        runCurrent()
        assertEquals(listOf(Triple("e1", 6f, false)), repo.calls)
    }

    @Test
    fun `接近结尾暂停标记为已完成`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(
            currentEpisode = episode("e1"),
            isPlaying = false,
            currentPosition = 597_000,
            duration = 600_000
        )
        runCurrent()
        assertEquals(listOf(Triple("e1", 597f, true)), repo.calls)
    }

    @Test
    fun `播完上报finished且不重复`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(
            currentEpisode = episode("e1"),
            isPlaying = false,
            currentPosition = 600_000,
            duration = 600_000,
            playbackState = Player.STATE_ENDED
        )
        runCurrent()
        assertEquals(listOf(Triple("e1", 600f, true)), repo.calls)

        // 结束后的重复状态发射不再上报（单曲循环重播也不覆盖 finished）
        state.value = state.value.copy(isPlaying = true, currentPosition = 3_000, playbackState = Player.STATE_READY)
        state.value = state.value.copy(currentPosition = 30_000)
        runCurrent()
        assertEquals(1, repo.calls.size)
    }

    @Test
    fun `切集时冲刷上一集且不覆盖已完成标记`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        // e1 播完（finished=true）
        state.value = PlayerState(
            currentEpisode = episode("e1"),
            isPlaying = false,
            currentPosition = 600_000,
            duration = 600_000,
            playbackState = Player.STATE_ENDED
        )
        runCurrent()

        // 切到 e2：e1 已完成，不冲刷覆盖
        state.value = PlayerState(currentEpisode = episode("e2"), isPlaying = true, currentPosition = 0)
        runCurrent()
        assertEquals(1, repo.calls.size)

        // e2 正常周期上报
        state.value = state.value.copy(currentPosition = 20_000)
        runCurrent()
        assertEquals(listOf(Triple("e1", 600f, true), Triple("e2", 20f, false)), repo.calls)
    }

    @Test
    fun `未完成切集时冲刷上一集最终进度`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(currentEpisode = episode("e1"), isPlaying = true, currentPosition = 8_000)
        runCurrent()
        assertEquals(0, repo.calls.size)

        // 关闭播放器（stop 后状态复位为空）
        state.value = PlayerState()
        runCurrent()
        assertEquals(listOf(Triple("e1", 8f, false)), repo.calls)
    }

    @Test
    fun `游客不上报`() = runTest {
        token.value = null
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(currentEpisode = episode("e1"), isPlaying = true, currentPosition = 20_000)
        state.value = state.value.copy(isPlaying = false, currentPosition = 21_000)
        runCurrent()
        assertEquals(0, repo.calls.size)
    }

    @Test
    fun `周期上报后原地暂停不重复上报`() = runTest {
        val state = MutableStateFlow(PlayerState())
        val reporter = ProgressReporter(state, token, RecordingRepository().also { repo = it }, backgroundScope)
        reporter.start()

        state.value = PlayerState(currentEpisode = episode("e1"), isPlaying = true, currentPosition = 16_000)
        runCurrent()
        assertEquals(1, repo.calls.size)

        state.value = state.value.copy(isPlaying = false)
        runCurrent()
        assertEquals(1, repo.calls.size)
    }
}

/** 只记录 updateEpisodeProgress 调用；其余方法本测试不会触达 */
private class RecordingRepository : ContentRepository {
    val calls = mutableListOf<Triple<String, Float, Boolean>>()

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> {
        calls += Triple(episodeid, progressSeconds, isFinished)
        return Result.Success(Unit)
    }

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> = error("unused")
    override suspend fun getEpisode(episodeid: String): Result<Episode> = error("unused")
    override suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle> = error("unused")
    override suspend fun getPodcastEpisodes(podcastid: String, page: Int, limit: Int, ascending: Boolean): Result<EpisodePage> = error("unused")
    override suspend fun getPodcasts(): Result<List<Podcast>> = error("unused")
    override suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail> = error("unused")
    override suspend fun searchPodcasts(query: String): Result<List<Podcast>> = error("unused")
    override suspend fun getTags(query: String?): Result<List<Tag>> = error("unused")
    override suspend fun getChannel(name: String): Result<ChannelData> = error("unused")
    override suspend fun getComments(episodeid: String): Result<List<Comment>> = error("unused")
    override suspend fun createComment(episodeid: String, content: String, parentId: Int?): Result<Comment> = error("unused")
    override suspend fun toggleCommentLike(commentid: Int): Result<Boolean> = error("unused")
    override suspend fun translate(text: String): Result<String> = error("unused")
    override suspend fun lookupWord(word: String): Result<DictEntry> = error("unused")
    override suspend fun addVocabulary(word: String, definition: String, contextSentence: String, translation: String, episodeid: String, timestampSec: Int, speakUrl: String): Result<Unit> = error("unused")
    override suspend fun getVocabularyWords(): Result<Set<String>> = error("unused")

    // 生词本（列表与复习）：本测试不涉及
    override suspend fun getAllVocabulary(): Result<List<com.wxkzd.yuanlu.domain.model.VocabularyItem>> = error("unused")
    override suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit> = error("unused")
    override suspend fun submitVocabularyReview(vocabularyid: Int, quality: Int): Result<com.wxkzd.yuanlu.domain.model.VocabularyReviewOutcome> = error("unused")
    override suspend fun updateVocabularyStatus(vocabularyid: Int, mastered: Boolean): Result<Unit> = error("unused")
    override suspend fun fetchTtsAudioUrl(text: String): Result<String> = error("unused")

    // 收藏：本测试不涉及
    override suspend fun getFavorites(): Result<com.wxkzd.yuanlu.domain.model.FavoritesBundle> = error("unused")
    // 收听历史：本测试不涉及
    override suspend fun getListeningHistory(
        page: Int,
        pageSize: Int,
        status: String
    ): Result<com.wxkzd.yuanlu.domain.model.HistoryPage> =
        Result.Success(com.wxkzd.yuanlu.domain.model.HistoryPage())
    override suspend fun checkPodcastFavorite(podcastid: String): Result<Boolean> = error("unused")
    override suspend fun addPodcastFavorite(podcastid: String): Result<Unit> = error("unused")
    override suspend fun removePodcastFavorite(podcastid: String): Result<Unit> = error("unused")
    override suspend fun checkEpisodeFavorite(episodeid: String): Result<Boolean> = error("unused")
    override suspend fun addEpisodeFavorite(episodeid: String): Result<Unit> = error("unused")
    override suspend fun removeEpisodeFavorite(episodeid: String): Result<Unit> = error("unused")

    // 学习路径：本测试不涉及
    override suspend fun getMyLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> = error("unused")
    override suspend fun getPublicLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> = error("unused")
    override suspend fun createLearningPath(pathName: String, description: String?, isPublic: Boolean): Result<Unit> = error("unused")
    override suspend fun getLearningPath(pathid: Int): Result<com.wxkzd.yuanlu.domain.model.LearningPathDetail> = error("unused")
    override suspend fun updateLearningPath(pathid: Int, pathName: String, description: String?, isPublic: Boolean): Result<Unit> = error("unused")
    override suspend fun deleteLearningPath(pathid: Int): Result<Unit> = error("unused")
    override suspend fun addEpisodeToLearningPath(pathid: Int, episodeid: String): Result<Unit> = error("unused")
    override suspend fun removeEpisodeFromLearningPath(pathid: Int, itemId: Int): Result<Unit> = error("unused")
    override suspend fun searchEpisodesForPath(query: String): Result<List<com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem>> = error("unused")
}
