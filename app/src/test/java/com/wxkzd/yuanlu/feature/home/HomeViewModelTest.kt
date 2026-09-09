package com.wxkzd.yuanlu.feature.home

import com.wxkzd.yuanlu.FakeTokenSource
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.EpisodePage
import com.wxkzd.yuanlu.domain.model.HistoryEpisode
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.domain.model.HistoryPage
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.PodcastDetail
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.SubtitleBundle
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.domain.repository.SmsSendStatus
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

    private fun episode(id: String, difficulty: String? = null, podcastid: String? = null) =
        Episode(
            episodeid = id,
            title = "Episode $id",
            difficulty = difficulty,
            podcastid = podcastid
        )

    private fun historyItem(id: Int, title: String) = HistoryItem(
        historyid = id,
        listenAt = "2026-09-01T00:00:00",
        progressSeconds = 100,
        isFinished = false,
        episode = HistoryEpisode(
            id = "ep-$id",
            title = title,
            author = "Apple Podcasts",
            category = "六分钟英语",
            durationSeconds = 600
        )
    )

    private fun nowIsoUtc(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())

    private fun fullWeek(vararg minutes: Int) =
        HomeViewModel.DAY_LABELS.mapIndexed { i, label -> WeeklyActivityItem(label, minutes[i]) }

    @Test
    fun `initial load composes header cards and episode lists`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(
                profile = UserProfile(
                    userid = "u1",
                    nickname = "远路漫漫",
                    bio = "每天听一点",
                    learnLevel = "Beginner",
                    weeklyListeningGoalHours = 2,
                    weeklyWordsGoal = 50
                ),
                stats = ProfileStats(streakDays = 3),
                weekNow = fullWeek(0, 30, 0, 60, 0, 0, 0),
                weekLast = fullWeek(0, 0, 0, 0, 0, 0, 0)
            ),
            contentRepository = FakeContentRepository(
                episodes = listOf(
                    episode("e1", "A1", "p1"),
                    episode("e2", "A2", "p1"),
                    episode("e3", "B1"),
                    episode("e4"),
                    episode("e5"),
                    episode("e6"),
                    episode("e7"),
                    episode("e8"),
                    episode("e9")
                ),
                // list-by-podcastid 富化源：剧集自身签名封面 + 时长/播放量/难度
                podcastEpisodes = mapOf(
                    "p1" to listOf(
                        Episode(
                            episodeid = "e1",
                            title = "Episode e1",
                            coverUrl = "https://cdn.example.com/signed-e1.jpg",
                            duration = 600,
                            playCount = 1200,
                            difficulty = "A1"
                        ),
                        Episode(
                            episodeid = "e2",
                            title = "Episode e2",
                            coverUrl = "https://cdn.example.com/signed-e2.jpg",
                            duration = 480,
                            playCount = 30,
                            difficulty = "A2"
                        )
                    )
                ),
                history = HistoryPage(
                    items = listOf(historyItem(1, "第一条"), historyItem(2, "第二条"), historyItem(3, "第三条"))
                ),
                vocab = listOf(
                    VocabularyItem(vocabularyid = 1, word = "road", addedDate = nowIsoUtc()),
                    VocabularyItem(vocabularyid = 2, word = "far", addedDate = "2020-01-01T00:00:00")
                )
            ),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        // 头部
        assertEquals("远路漫漫", state.displayName)
        assertEquals("每天听一点", state.bio)
        assertEquals(3, state.streakDays)
        // 最近一次收听 + 继续收听
        assertEquals("第一条", state.latestHistory?.episode?.title)
        assertEquals(2, state.continueListening.size)
        // 本周里程：90 分钟 = 1.5h × 5km/h = 7.5km；目标 2h = 10km；还差 30 分钟
        assertEquals(7.5, state.mileage.kmCurrent, 0.001)
        assertEquals(10.0, state.mileage.kmGoal, 0.001)
        assertEquals(30, state.mileage.remainingMins)
        // 上周无数据且本周 >0 → +100%
        assertEquals(100, state.mileage.weeklyProgress)
        // 词汇路标：仅 1 个是本周新增
        assertEquals(1, state.mileage.wordsCurrent)
        assertEquals(50, state.mileage.wordsGoal)
        // 我的路：7 天且仅今天高亮
        assertEquals(7, state.journeyDays.size)
        assertEquals(1, state.journeyDays.count { it.isToday })
        // 为你推荐：Beginner → A1/A2
        assertEquals("Beginner", state.recommendedLevel)
        assertEquals(listOf("e1", "e2"), state.recommended.map { it.episodeid })
        // 剧集富化：封面换为剧集自身签名直链，时长/播放量补齐，且绝不回退播客封面
        assertEquals("https://cdn.example.com/signed-e1.jpg", state.recommended.first().coverUrl)
        assertEquals(null, state.recommended.first().coverFallbackUrl)
        assertEquals(600, state.recommended.first().duration)
        assertEquals(1200, state.recommended.first().playCount)
        // 最新发布：纵向列表固定 4 条
        assertEquals(4, state.latestEpisodes.size)
    }

    @Test
    fun `recommendation falls back to general when level has no match`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(
                profile = UserProfile(userid = "u1", learnLevel = "Advanced")
            ),
            contentRepository = FakeContentRepository(
                episodes = listOf(episode("e1", "A1"), episode("e2", "B1"), episode("e3", "A2"))
            ),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("General", state.recommendedLevel)
        assertEquals(listOf("e1", "e2", "e3"), state.recommended.map { it.episodeid })
    }

    @Test
    fun `journey strip keeps weekday labels when weekly activity missing`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(weekNow = emptyList()),
            contentRepository = FakeContentRepository(episodes = emptyList()),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        val days = viewModel.uiState.value.journeyDays
        assertEquals(HomeViewModel.DAY_LABELS, days.map { it.label })
        assertTrue(days.all { it.minutes == 0 })
        // 周活动缺失时今日按 0 分钟计，未达标状态照常输出
        assertEquals("今日打卡还差 ${HomeViewModel.DEFAULT_DAILY_GOAL_MINS} 分钟", viewModel.uiState.value.checkInStatus)
    }

    @Test
    fun `greeting joins time prefix with nickname`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(profile = UserProfile(userid = "u1", nickname = "远路漫漫")),
            contentRepository = FakeContentRepository(episodes = emptyList()),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        val greeting = viewModel.uiState.value.greeting
        assertTrue(greeting in setOf("早上好，远路漫漫。", "下午好，远路漫漫。", "晚上好，远路漫漫。"))
    }

    @Test
    fun `check-in completes when today minutes reach daily goal`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(
                profile = UserProfile(userid = "u1", dailyStudyGoalMins = 30),
                weekNow = fullWeek(30, 30, 30, 30, 30, 30, 30)
            ),
            contentRepository = FakeContentRepository(episodes = emptyList()),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        assertEquals("今日打卡完成", viewModel.uiState.value.checkInStatus)
    }

    @Test
    fun `check-in shows remaining minutes when goal unmet`() = runTest(dispatcher) {
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(
                profile = UserProfile(userid = "u1", dailyStudyGoalMins = 45),
                weekNow = fullWeek(20, 20, 20, 20, 20, 20, 20)
            ),
            contentRepository = FakeContentRepository(episodes = emptyList()),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()

        assertEquals("今日打卡还差 25 分钟", viewModel.uiState.value.checkInStatus)
    }

    @Test
    fun `section failure is fail-soft while total failure surfaces error`() = runTest(dispatcher) {
        // profile 失败、其余成功：整页可用，昵称走默认
        val partial = HomeViewModel(
            authRepository = FakeAuthRepository(
                profileFailure = Result.Error(500, "profile boom"),
                stats = ProfileStats(streakDays = 2),
                weekNow = fullWeek(0, 0, 0, 0, 0, 0, 0),
                weekLast = fullWeek(0, 0, 0, 0, 0, 0, 0)
            ),
            contentRepository = FakeContentRepository(episodes = listOf(episode("e1"))),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()
        val partialState = partial.uiState.value
        assertNull(partialState.error)
        assertEquals("朋友", partialState.displayName)
        assertEquals(listOf("e1"), partialState.latestEpisodes.map { it.episodeid })

        // 全部失败：网络错误 → 整页错误态
        val all = HomeViewModel(
            authRepository = FakeAuthRepository(allFailure = Result.NetworkError),
            contentRepository = FakeContentRepository(failure = Result.NetworkError),
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()
        val allState = all.uiState.value
        assertNotNull(allState.error)
        assertEquals("网络连接失败", allState.error)
    }

    @Test
    fun `silent refresh keeps content while reloading`() = runTest(dispatcher) {
        val repository = FakeContentRepository(episodes = listOf(episode("e1")))
        val viewModel = HomeViewModel(
            authRepository = FakeAuthRepository(),
            contentRepository = repository,
            tokenSource = FakeTokenSource("jwt-u1")
        )
        runCurrent()
        assertEquals(1, viewModel.uiState.value.latestEpisodes.size)

        viewModel.refresh(silent = true)
        runCurrent()
        // 刷新完成后恢复非转圈态，内容仍在
        assertFalse(viewModel.uiState.value.isRefreshing)
        assertEquals(1, viewModel.uiState.value.latestEpisodes.size)
    }

    @Test
    fun `logout clears cached profile and next login reloads fresh account`() = runTest(dispatcher) {
        val auth = FakeAuthRepository(profile = UserProfile(userid = "u1", nickname = "账号一"))
        val tokens = FakeTokenSource("jwt-u1")
        val viewModel = HomeViewModel(
            authRepository = auth,
            contentRepository = FakeContentRepository(),
            tokenSource = tokens
        )
        runCurrent()
        assertEquals("账号一", viewModel.uiState.value.displayName)

        // 登出：资料/里程/历史等用户缓存全部清空，回到默认态
        tokens.logout()
        runCurrent()
        assertEquals("朋友", viewModel.uiState.value.displayName)
        assertNull(viewModel.uiState.value.latestHistory)
        assertTrue(viewModel.uiState.value.continueListening.isEmpty())

        // 换号登录：自动整页重拉，展示新账号资料而非上一账号残留
        auth.profile = UserProfile(userid = "u2", nickname = "账号二")
        tokens.login("jwt-u2")
        runCurrent()
        assertEquals("账号二", viewModel.uiState.value.displayName)
    }
}

private class FakeAuthRepository(
    var profile: UserProfile? = null,
    private val profileFailure: Result<UserProfile>? = null,
    private val stats: ProfileStats? = null,
    private val weekNow: List<WeeklyActivityItem> = emptyList(),
    private val weekLast: List<WeeklyActivityItem> = emptyList(),
    /** 非空时所有端点统一失败（整页错误态用例） */
    private val allFailure: Result<*>? = null
) : AuthRepository {
    @Suppress("UNCHECKED_CAST")
    private fun <T> result(value: T): Result<T> = (allFailure as? Result<T>) ?: Result.Success(value)

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun logout() = Unit

    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> =
        Result.Error(600, "not implemented in fake")

    override suspend fun sendEmailVerificationCode(email: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun signUp(email: String, code: String, password: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun getProfile(): Result<UserProfile> =
        allFailure?.let { it as Result<UserProfile> } ?: profileFailure
            ?: Result.Success(profile ?: UserProfile(userid = "u1"))

    override suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile> = Result.Error(600, "not implemented in fake")

    override suspend fun getStatsOverview(): Result<ProfileStats> =
        result(stats ?: ProfileStats())

    override suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>> =
        result(if (weekOffset == 0) weekNow else weekLast)

    override suspend fun getAchievements(): Result<List<AchievementItem>> =
        result(emptyList())
}

private class FakeContentRepository(
    private val episodes: List<Episode> = emptyList(),
    private val podcastEpisodes: Map<String, List<Episode>> = emptyMap(),
    private val history: HistoryPage = HistoryPage(),
    private val vocab: List<VocabularyItem> = emptyList(),
    private val failure: Result<*>? = null
) : ContentRepository {

    @Suppress("UNCHECKED_CAST")
    private fun <T> result(value: T): Result<T> = (failure as? Result<T>) ?: Result.Success(value)

    override suspend fun getLatestEpisodes(page: Int, pageSize: Int): Result<List<Episode>> =
        result(episodes)

    override suspend fun getEpisode(episodeid: String): Result<Episode> =
        Result.Success(Episode(episodeid = episodeid, title = ""))

    override suspend fun getSubtitles(episodeid: String): Result<SubtitleBundle> =
        Result.Success(SubtitleBundle(emptyList(), null))

    /** 首页剧集富化源：list-by-podcastid 的签名封面 + 时长/播放量/难度 */
    override suspend fun getPodcastEpisodes(
        podcastid: String,
        page: Int,
        limit: Int,
        ascending: Boolean
    ): Result<EpisodePage> = result(EpisodePage(podcastEpisodes[podcastid].orEmpty(), 0, false))

    override suspend fun getPodcasts(): Result<List<Podcast>> = Result.Success(emptyList())

    override suspend fun getPodcastDetail(podcastid: String): Result<PodcastDetail> =
        Result.Success(PodcastDetail(Podcast(podcastid = podcastid, title = ""), false, emptyList()))

    override suspend fun searchPodcasts(query: String): Result<List<Podcast>> =
        Result.Success(emptyList())

    override suspend fun getTags(query: String?): Result<List<Tag>> = Result.Success(emptyList())

    override suspend fun getChannel(name: String): Result<ChannelData> =
        Result.Success(ChannelData(name, 0, emptyList(), emptyList()))

    override suspend fun getComments(episodeid: String): Result<List<Comment>> =
        Result.Success(emptyList())

    override suspend fun createComment(
        episodeid: String,
        content: String,
        parentId: Int?
    ): Result<Comment> = Result.Error(600, "not implemented in fake")

    override suspend fun toggleCommentLike(commentid: Int): Result<Boolean> =
        Result.Success(false)

    override suspend fun translate(text: String): Result<String> =
        Result.Error(0, "not implemented in fake")

    override suspend fun fetchTtsAudioUrl(text: String): Result<String> =
        Result.Error(600, "not implemented in fake")

    override suspend fun lookupWord(word: String): Result<com.wxkzd.yuanlu.domain.model.DictEntry> =
        Result.Error(600, "not implemented in fake")

    override suspend fun addVocabulary(
        word: String,
        definition: String,
        contextSentence: String,
        translation: String,
        episodeid: String,
        timestampSec: Int,
        speakUrl: String
    ): Result<Unit> = Result.Error(600, "not implemented in fake")

    override suspend fun getVocabularyWords(): Result<Set<String>> =
        Result.Success(emptySet())

    override suspend fun getAllVocabulary(): Result<List<VocabularyItem>> = result(vocab)

    override suspend fun deleteVocabulary(vocabularyid: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun submitVocabularyReview(
        vocabularyid: Int,
        quality: Int
    ): Result<com.wxkzd.yuanlu.domain.model.VocabularyReviewOutcome> =
        Result.Error(600, "not implemented in fake")

    override suspend fun updateVocabularyStatus(
        vocabularyid: Int,
        mastered: Boolean
    ): Result<Unit> = Result.Error(600, "not implemented in fake")

    override suspend fun updateEpisodeProgress(
        episodeid: String,
        progressSeconds: Float,
        isFinished: Boolean
    ): Result<Unit> = Result.Success(Unit)

    override suspend fun getFavorites(): Result<com.wxkzd.yuanlu.domain.model.FavoritesBundle> =
        Result.Success(com.wxkzd.yuanlu.domain.model.FavoritesBundle())

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

    override suspend fun getListeningHistory(
        page: Int,
        pageSize: Int,
        status: String
    ): Result<HistoryPage> = result(history)

    override suspend fun getMyLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> =
        Result.Success(emptyList())

    override suspend fun getPublicLearningPaths(): Result<List<com.wxkzd.yuanlu.domain.model.LearningPathSummary>> =
        Result.Success(emptyList())

    override suspend fun createLearningPath(
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit> = Result.Error(600, "not implemented in fake")

    override suspend fun getLearningPath(pathid: Int): Result<com.wxkzd.yuanlu.domain.model.LearningPathDetail> =
        Result.Success(com.wxkzd.yuanlu.domain.model.LearningPathDetail(pathid = pathid, pathName = ""))

    override suspend fun updateLearningPath(
        pathid: Int,
        pathName: String,
        description: String?,
        isPublic: Boolean
    ): Result<Unit> = Result.Error(600, "not implemented in fake")

    override suspend fun deleteLearningPath(pathid: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun addEpisodeToLearningPath(pathid: Int, episodeid: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun removeEpisodeFromLearningPath(pathid: Int, itemId: Int): Result<Unit> =
        Result.Error(600, "not implemented in fake")

    override suspend fun searchEpisodesForPath(query: String): Result<List<com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem>> =
        Result.Success(emptyList())
}
