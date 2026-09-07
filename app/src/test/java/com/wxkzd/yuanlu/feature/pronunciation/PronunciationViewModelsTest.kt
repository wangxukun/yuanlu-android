package com.wxkzd.yuanlu.feature.pronunciation

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.LeaderboardEntry
import com.wxkzd.yuanlu.domain.model.LeaderboardMetric
import com.wxkzd.yuanlu.domain.model.LeaderboardPeriod
import com.wxkzd.yuanlu.domain.model.MyLeaderboardRank
import com.wxkzd.yuanlu.domain.model.SpeechLeaderboard
import com.wxkzd.yuanlu.domain.model.SpeechNotebook
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 弱项本主页与达人榜 VM：四象限缓存切换 / 错误态 / 重入刷新 */
@OptIn(ExperimentalCoroutinesApi::class)
class PronunciationViewModelsTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun board(
        period: LeaderboardPeriod,
        metric: LeaderboardMetric,
        nickname: String
    ) = SpeechLeaderboard(
        period = period,
        metric = metric,
        entries = listOf(LeaderboardEntry("u1", nickname, "", evalCount = 9, avgScore = 88)),
        me = MyLeaderboardRank(rank = 2, evalCount = 9, avgScore = 88)
    )

    @Test
    fun `notebook loads and derives trial lock count`() = runTest(dispatcher) {
        val repo = object : SpeechRepository {
            override suspend fun getPracticeData(episodeId: String) =
                error("not used")
            override suspend fun evaluate(
                episodeId: String, subtitleId: Int, targetText: String, wavBytes: ByteArray
            ) = error("not used")
            override suspend fun getNotebook() = Result.Success(
                SpeechNotebook(isPremium = false, totalErrors = 5)
            )
            override suspend fun getWeakErrors() = error("not used")
            override suspend fun getLeaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric) =
                error("not used")
        }
        val vm = PronunciationNotebookViewModel(repo)
        runCurrent()

        assertFalse(vm.uiState.value.isLoading)
        assertEquals(5, vm.uiState.value.notebook!!.totalErrors)
        // 非会员：空 errors 切片 + 总量 5 → 锁定 5 条
        assertEquals(5, vm.uiState.value.notebook!!.lockedCount)

        // 首次进入（init 加载刚完成）重入不重复拉取；此后重入触发刷新
        vm.onReenter()
        runCurrent()
        // 无新增可观测行为，仅验证不崩溃且状态保持
        assertEquals(5, vm.uiState.value.notebook!!.totalErrors)
    }

    @Test
    fun `leaderboard caches quadrants and switches without refetch`() = runTest(dispatcher) {
        var fetchCount = 0
        val repo = object : SpeechRepository {
            override suspend fun getPracticeData(episodeId: String) = error("not used")
            override suspend fun evaluate(
                episodeId: String, subtitleId: Int, targetText: String, wavBytes: ByteArray
            ) = error("not used")
            override suspend fun getNotebook() = error("not used")
            override suspend fun getWeakErrors() = error("not used")
            override suspend fun getLeaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric): Result<SpeechLeaderboard> {
                fetchCount++
                return Result.Success(board(period, metric, "用户$fetchCount"))
            }
        }
        val vm = SpeechLeaderboardViewModel(repo)
        runCurrent()

        // 初始象限：近7天 × 平均分榜
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(LeaderboardPeriod.WEEKLY, vm.uiState.value.period)
        assertEquals(LeaderboardMetric.SCORE, vm.uiState.value.metric)
        assertEquals("用户1", vm.uiState.value.current!!.entries.first().nickname)
        assertEquals(2, vm.uiState.value.current!!.me!!.rank)
        assertEquals(1, fetchCount)

        // 切换维度 → 勤奋榜拉取
        vm.selectMetric(LeaderboardMetric.COUNT)
        runCurrent()
        assertEquals(LeaderboardMetric.COUNT, vm.uiState.value.metric)
        assertEquals("用户2", vm.uiState.value.current!!.entries.first().nickname)
        assertEquals(2, fetchCount)

        // 切回平均分榜 → 命中缓存不重新请求
        vm.selectMetric(LeaderboardMetric.SCORE)
        runCurrent()
        assertEquals("用户1", vm.uiState.value.current!!.entries.first().nickname)
        assertEquals(2, fetchCount)

        // 切换周期 → 今日 × 平均分榜拉取（第 3 次）
        vm.selectPeriod(LeaderboardPeriod.DAILY)
        runCurrent()
        assertEquals(LeaderboardPeriod.DAILY, vm.uiState.value.period)
        assertEquals("用户3", vm.uiState.value.current!!.entries.first().nickname)
        assertEquals(3, fetchCount)

        // 规则文案随维度切换（顶栏副标题依据）
        assertEquals("平均综合分（≥5次评测）", LeaderboardMetric.SCORE.ruleText)
        assertEquals("练习次数", LeaderboardMetric.COUNT.ruleText)
    }

    @Test
    fun `leaderboard surfaces error and retry recovers`() = runTest(dispatcher) {
        var fail = true
        val repo = object : SpeechRepository {
            override suspend fun getPracticeData(episodeId: String) = error("not used")
            override suspend fun evaluate(
                episodeId: String, subtitleId: Int, targetText: String, wavBytes: ByteArray
            ) = error("not used")
            override suspend fun getNotebook() = error("not used")
            override suspend fun getWeakErrors() = error("not used")
            override suspend fun getLeaderboard(period: LeaderboardPeriod, metric: LeaderboardMetric) =
                if (fail) Result.Error(401, "请先登录后查看排行榜")
                else Result.Success(board(period, metric, "ok"))
        }
        val vm = SpeechLeaderboardViewModel(repo)
        runCurrent()

        assertNull(vm.uiState.value.current)
        assertEquals("请先登录后查看排行榜", vm.uiState.value.loadError)

        fail = false
        vm.retry()
        runCurrent()
        assertNull(vm.uiState.value.loadError)
        assertEquals("ok", vm.uiState.value.current!!.entries.first().nickname)
    }
}
