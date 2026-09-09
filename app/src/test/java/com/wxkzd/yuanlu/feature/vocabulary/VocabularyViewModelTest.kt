package com.wxkzd.yuanlu.feature.vocabulary

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.FakeTokenSource
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.VocabularyItem
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

/**
 * 生词本对全局登录态的响应（游客期 VM 已由 AppNavHost 以 Activity 作用域创建）：
 * - Bug1：游客期不发起注定 401 的请求；登录成功后自动拉取，不残留「请先登录」错误态；
 * - Bug2：登出清空列表与复习现场；换号登录自动重拉，绝不展示上一账号数据。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VocabularyViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun word(id: Int, label: String, nextReviewAt: String? = "2099-01-01T00:00:00Z") =
        VocabularyItem(
            vocabularyid = id,
            word = label,
            nextReviewAt = nextReviewAt
        )

    private class FakeVocabRepository : FakeContentRepository() {
        var vocabulary: List<VocabularyItem> = emptyList()
        var failure: Result<List<VocabularyItem>>? = null
        var fetchCount = 0
            private set

        override suspend fun getAllVocabulary(): Result<List<VocabularyItem>> {
            fetchCount++
            return failure ?: Result.Success(vocabulary)
        }
    }

    @Test
    fun `cold start with token loads vocabulary`() = runTest(dispatcher) {
        val repository = FakeVocabRepository().apply { vocabulary = listOf(word(1, "road")) }
        val viewModel = VocabularyViewModel(repository, FakeTokenSource("jwt-u1"))
        runCurrent()

        assertEquals(1, repository.fetchCount)
        assertEquals(listOf("road"), viewModel.uiState.value.vocabulary.map { it.word })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `guest construction skips fetch and login auto loads`() = runTest(dispatcher) {
        val repository = FakeVocabRepository().apply {
            vocabulary = listOf(word(1, "road"))
            failure = Result.Error(401, "请先登录")
        }
        val tokens = FakeTokenSource(null)
        val viewModel = VocabularyViewModel(repository, tokens)
        runCurrent()

        // 游客期：不发起无 token 的 401 请求，错误态不被缓存
        assertEquals(0, repository.fetchCount)

        // 登录成功：数据源恢复可用（模拟拿到新 token 后接口正常），
        // 页面自动整页拉取，无需手点重试
        repository.failure = null
        tokens.login("jwt-u1")
        runCurrent()

        assertEquals(1, repository.fetchCount)
        assertEquals(listOf("road"), viewModel.uiState.value.vocabulary.map { it.word })
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `logout clears list and closes review session`() = runTest(dispatcher) {
        val repository = FakeVocabRepository().apply {
            vocabulary = listOf(
                word(1, "road", nextReviewAt = "2000-01-01T00:00:00Z"),
                word(2, "far", nextReviewAt = "2000-01-01T00:00:00Z")
            )
        }
        val tokens = FakeTokenSource("jwt-u1")
        val viewModel = VocabularyViewModel(repository, tokens)
        runCurrent()
        viewModel.startReview()
        assertTrue(viewModel.uiState.value.isReviewOpen)

        tokens.logout()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.vocabulary.isEmpty())
        assertFalse(state.isReviewOpen)
        assertNull(state.error)
    }

    @Test
    fun `account switch reloads fresh data without stale rows`() = runTest(dispatcher) {
        val repository = FakeVocabRepository().apply { vocabulary = listOf(word(1, "u1-word")) }
        val tokens = FakeTokenSource("jwt-u1")
        val viewModel = VocabularyViewModel(repository, tokens)
        runCurrent()
        assertEquals(listOf("u1-word"), viewModel.uiState.value.vocabulary.map { it.word })

        tokens.logout()
        runCurrent()
        repository.vocabulary = listOf(word(2, "u2-word"))
        tokens.login("jwt-u2")
        runCurrent()

        // 展示的是新账号数据，而非上一账号的缓存行
        assertEquals(listOf("u2-word"), viewModel.uiState.value.vocabulary.map { it.word })
    }
}
