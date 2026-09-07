package com.wxkzd.yuanlu.feature.history

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.domain.model.HistoryEpisode
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.domain.model.HistoryPage
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * 相对当前时刻生成 UTC ISO 串：daysAgo 天前的同一时刻。
     * 时刻固定到本地当天 12:00 再回退天数——避开午夜前后运行时
     * 「今天」经 UTC 往返落到昨天导致的 flaky（本地 00:00 后 UTC 仍是前一天）。
     */
    private fun isoAt(daysAgo: Int): String {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(cal.timeInMillis - TimeZone.getDefault().getOffset(cal.timeInMillis)))
    }

    private fun item(
        historyid: Int,
        daysAgo: Int,
        finished: Boolean = false,
        progress: Int = 60
    ) = HistoryItem(
        historyid = historyid,
        listenAt = isoAt(daysAgo),
        progressSeconds = progress,
        isFinished = finished,
        episode = HistoryEpisode(
            id = "ep$historyid",
            title = "Episode $historyid",
            author = "BBC",
            category = "6 Minute English",
            duration = "6:00",
            durationSeconds = 360
        )
    )

    @Test
    fun `load fetches first page with all status`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            historyPages = mapOf(
                1 to HistoryPage(
                    items = listOf(item(1, 0, finished = true), item(2, 0), item(3, 1)),
                    total = 3,
                    hasMore = false
                )
            )
        }
        val viewModel = HistoryViewModel(repo)
        runCurrent()

        assertEquals(3, viewModel.uiState.value.items.size)
        assertEquals(1, repo.historyCalls.size)
        assertEquals("all", repo.historyCalls.first().third)
    }

    @Test
    fun `groups split into today yesterday and concrete dates`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            historyPages = mapOf(
                1 to HistoryPage(
                    items = listOf(item(1, 0), item(2, 1), item(3, 5), item(4, 0, finished = true)),
                    total = 4,
                    hasMore = false
                )
            )
        }
        val viewModel = HistoryViewModel(repo)
        runCurrent()

        val labels = viewModel.uiState.value.groups.map { it.label }
        assertEquals(listOf("今天", "昨天"), labels.take(2))
        // 更早的数据按具体日期分组："2026年9月1日" 这类格式（月/日无前导零）
        val earlierLabel = labels[2]
        assertTrue(earlierLabel.matches(Regex("\\d{4}年\\d{1,2}月\\d{1,2}日")))
        assertEquals(listOf(1, 4), viewModel.uiState.value.groups[0].items.map { it.historyid })
        assertEquals(listOf(2), viewModel.uiState.value.groups[1].items.map { it.historyid })
        assertEquals(listOf(3), viewModel.uiState.value.groups[2].items.map { it.historyid })
    }

    @Test
    fun `selecting filter resets pagination and requests with status`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            historyPages = mapOf(
                1 to HistoryPage(items = listOf(item(1, 0)), total = 1, hasMore = false)
            )
        }
        val viewModel = HistoryViewModel(repo)
        runCurrent()

        viewModel.selectFilter(HistoryFilter.COMPLETED)
        runCurrent()

        assertEquals(HistoryFilter.COMPLETED, viewModel.uiState.value.filter)
        // 每次过滤切换都是 page=1 的新请求，携带服务端过滤状态
        assertEquals("finished", repo.historyCalls.last().third)
        assertEquals(1, repo.historyCalls.last().first)
    }

    @Test
    fun `loadMore appends next page and stops at end`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            historyPages = mapOf(
                1 to HistoryPage(items = listOf(item(1, 0)), total = 2, hasMore = true),
                2 to HistoryPage(items = listOf(item(2, 3)), total = 2, hasMore = false)
            )
        }
        val viewModel = HistoryViewModel(repo)
        runCurrent()

        viewModel.loadMore()
        runCurrent()
        assertEquals(2, viewModel.uiState.value.items.size)
        assertTrue(viewModel.uiState.value.endReached)

        // 到底后不再发起请求
        val calls = repo.historyCalls.size
        viewModel.loadMore()
        runCurrent()
        assertEquals(calls, repo.historyCalls.size)
    }

    @Test
    fun `progress ratio is bounded`() {
        val item = item(1, 0).copy(progressSeconds = 9999)
        assertEquals(1f, item.progressRatio)
    }
}
