package com.wxkzd.yuanlu.feature.learningpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.LearningPathSummary
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 列表页双 Tab（对齐 Web LearningPathsClient 的 my-paths / official） */
enum class LearningPathsTab(val label: String) {
    MINE("我的集合"),
    DISCOVER("发现")
}

data class LearningPathsUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val activeTab: LearningPathsTab = LearningPathsTab.MINE,
    val searchQuery: String = "",
    val myPaths: List<LearningPathSummary> = emptyList(),
    val publicPaths: List<LearningPathSummary> = emptyList(),
    /** 创建路径提交在途（弹窗按钮转圈/防重复提交） */
    val isSubmitting: Boolean = false
) {
    val activePaths: List<LearningPathSummary>
        get() = if (activeTab == LearningPathsTab.DISCOVER) publicPaths else myPaths

    /** 本地搜索（对齐 Web：按路径名忽略大小写包含匹配） */
    val filteredPaths: List<LearningPathSummary>
        get() = activePaths.filter {
            it.pathName.lowercase().contains(searchQuery.trim().lowercase())
        }
}

/**
 * 「学习路径」列表页（复刻 Web /library/learning-paths）：
 * 我的集合/发现双 Tab + 本地搜索 + 创建路径弹窗；
 * 我的集合与发现并行拉取（对齐 Web Promise.all），
 * LearningPathCenter 驱动跨页静默刷新（详情页变更后返回列表自动同步）。
 */
@HiltViewModel
class LearningPathsViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val learningPathCenter: LearningPathCenter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearningPathsUiState())
    val uiState: StateFlow<LearningPathsUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        load()
        // 详情页（或本页）任一路径变更后静默刷新，保留当前 Tab 与搜索词
        viewModelScope.launch {
            learningPathCenter.revision.drop(1).collect {
                if (!_uiState.value.isLoading) load(silent = true)
            }
        }
    }

    fun selectTab(tab: LearningPathsTab) {
        if (_uiState.value.activeTab == tab) return
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        load(silent = true)
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        load()
    }

    /**
     * 创建路径（创建弹窗提交）：成功后回调 [onCreated] 关闭弹窗并清空表单，
     * 再经 LearningPathCenter 通知列表静默刷新（此时应已切到「我的集合」语义）。
     */
    fun createPath(
        pathName: String,
        description: String?,
        isPublic: Boolean,
        onCreated: () -> Unit
    ) {
        if (_uiState.value.isSubmitting) return
        val name = pathName.trim()
        if (name.isEmpty()) {
            _toast.value = "请输入路径名称"
            return
        }
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = contentRepository.createLearningPath(name, description, isPublic)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.value = "路径已创建"
                    onCreated()
                    learningPathCenter.notifyChanged()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.value = "网络连接失败，请稍后重试"
                }
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    private fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
            val (mineResult, publicResult) = coroutineScope {
                val mine = async { contentRepository.getMyLearningPaths() }
                val public = async { contentRepository.getPublicLearningPaths() }
                mine.await() to public.await()
            }
            val errorMessage = when {
                mineResult is Result.Error -> mineResult.message
                publicResult is Result.Error -> publicResult.message
                mineResult is Result.NetworkError || publicResult is Result.NetworkError -> "网络连接失败"
                else -> null
            }
            when {
                errorMessage == null -> _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        myPaths = (mineResult as Result.Success).data,
                        publicPaths = (publicResult as Result.Success).data
                    )
                }
                // 静默刷新失败保留旧数据，仅提示（不打断浏览）
                silent -> {
                    _uiState.update { it.copy(isRefreshing = false) }
                    _toast.value = errorMessage
                }
                else -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = errorMessage)
                }
            }
        }
    }
}
