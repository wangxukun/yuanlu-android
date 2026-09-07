package com.wxkzd.yuanlu.feature.learningpath

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.LearningPathSummary
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

@OptIn(ExperimentalCoroutinesApi::class)
class LearningPathsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun path(pathid: Int, name: String, itemCount: Int = 3, progress: Int = 40) =
        LearningPathSummary(
            pathid = pathid,
            pathName = name,
            description = "desc",
            coverUrl = null,
            isPublic = true,
            itemCount = itemCount,
            creatorName = "wxk",
            progress = progress
        )

    @Test
    fun `initial load fetches mine and public lists in parallel`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            myLearningPaths = Result.Success(listOf(path(1, "词汇学习")))
            publicLearningPaths = Result.Success(listOf(path(2, "别人的路径")))
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.error)
        assertEquals(listOf("词汇学习"), state.myPaths.map { it.pathName })
        assertEquals(listOf("别人的路径"), state.publicPaths.map { it.pathName })
        // 默认 Tab 为「我的集合」
        assertEquals(LearningPathsTab.MINE, state.activeTab)
        assertEquals(1, state.filteredPaths.size)
    }

    @Test
    fun `mine failure surfaces error and blocks content`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            myLearningPaths = Result.Error(401, "请先登录")
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("请先登录", state.error)
    }

    @Test
    fun `network failure on public list maps to network message`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            publicLearningPaths = Result.NetworkError
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        assertEquals("网络连接失败", viewModel.uiState.value.error)
    }

    @Test
    fun `switching tab swaps visible list`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            myLearningPaths = Result.Success(listOf(path(1, "我的路径")))
            publicLearningPaths = Result.Success(listOf(path(2, "公开路径")))
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        viewModel.selectTab(LearningPathsTab.DISCOVER)
        assertEquals("公开路径", viewModel.uiState.value.filteredPaths.single().pathName)
        // 重复选择同一 Tab 幂等
        viewModel.selectTab(LearningPathsTab.DISCOVER)
        assertEquals(LearningPathsTab.DISCOVER, viewModel.uiState.value.activeTab)
    }

    @Test
    fun `search filters active list by name case-insensitively`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            myLearningPaths = Result.Success(
                listOf(path(1, "Vocabulary Learning"), path(2, "每日听力"))
            )
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        viewModel.updateSearchQuery("vocabulary")
        assertEquals(1, viewModel.uiState.value.filteredPaths.size)
        assertEquals("Vocabulary Learning", viewModel.uiState.value.filteredPaths.single().pathName)

        viewModel.updateSearchQuery("  ")
        assertEquals(2, viewModel.uiState.value.filteredPaths.size)
    }

    @Test
    fun `create path submits trimmed name and triggers silent refresh`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            myLearningPaths = Result.Success(listOf(path(1, "旧路径")))
        }
        val center = LearningPathCenter()
        val viewModel = LearningPathsViewModel(repo, center)
        runCurrent()
        assertEquals(1, repo.myLearningPathCalls.size)

        var dialogClosed = false
        viewModel.createPath("  新路径  ", "描述", true) { dialogClosed = true }
        runCurrent()

        // 提交参数：名称已 trim；成功后关闭弹窗 + 提示 + 列表经 center 静默刷新
        assertEquals("新路径", repo.createdLearningPaths.single().first)
        assertTrue(dialogClosed)
        assertEquals("路径已创建", viewModel.toast.value)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertEquals(2, repo.myLearningPathCalls.size)
    }

    @Test
    fun `create path failure keeps dialog open and toasts message`() = runTest(dispatcher) {
        val repo = FakeContentRepository().apply {
            learningPathMutationError = Result.Error(401, "请先登录")
        }
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        var dialogClosed = false
        viewModel.createPath("新路径", null, false) { dialogClosed = true }
        runCurrent()

        assertFalse(dialogClosed)
        assertEquals("请先登录", viewModel.toast.value)
        assertFalse(viewModel.uiState.value.isSubmitting)
        viewModel.consumeToast()
        assertNull(viewModel.toast.value)
    }

    @Test
    fun `blank name is rejected locally without hitting repository`() = runTest(dispatcher) {
        val repo = FakeContentRepository()
        val viewModel = LearningPathsViewModel(repo, LearningPathCenter())
        runCurrent()

        viewModel.createPath("   ", null, false) {}
        runCurrent()

        assertTrue(repo.createdLearningPaths.isEmpty())
        assertEquals("请输入路径名称", viewModel.toast.value)
    }

    @Test
    fun `center revision bump after initial load triggers silent refresh keeping query`() =
        runTest(dispatcher) {
            val repo = FakeContentRepository().apply {
                myLearningPaths = Result.Success(listOf(path(1, "路径A")))
            }
            val center = LearningPathCenter()
            val viewModel = LearningPathsViewModel(repo, center)
            runCurrent()

            viewModel.updateSearchQuery("路径")
            center.notifyChanged()   // 模拟详情页删除/编辑成功
            runCurrent()

            // 静默刷新不打断浏览：无 loading 态、Tab 与搜索词保留
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals("路径", viewModel.uiState.value.searchQuery)
            assertEquals(2, repo.myLearningPathCalls.size)
        }
}
