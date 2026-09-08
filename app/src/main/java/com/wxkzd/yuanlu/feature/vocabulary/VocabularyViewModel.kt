package com.wxkzd.yuanlu.feature.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 一次复习记录（vocabularyid 去重，重评覆盖旧结果） */
data class ReviewResult(
    val vocabularyid: Int,
    val word: String,
    val quality: Int
)

/**
 * 生词本整体状态：列表管理 + 卡片复习（对齐 Web useVocabularyNotebook 单 Hook 设计，
 * 列表与复习共享同一份 vocabulary 数据，复习打卡后原地更新熟练度/下次复习时间）。
 */
data class VocabularyUiState(
    // ---- 列表 ----
    val isLoading: Boolean = true,
    /** 下拉刷新中（静默重载，保留现有内容，仅顶部转圈） */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val vocabulary: List<VocabularyItem> = emptyList(),
    val filterStatus: VocabStatusTab = VocabStatusTab.LEARNING,
    val searchQuery: String = "",
    val sortMethod: VocabSortMethod = VocabSortMethod.REVIEW,
    val expandedId: Int? = null,
    /** 非空 = 弹出删除确认弹窗（对齐 Web 底部确认 modal） */
    val deletingItem: VocabularyItem? = null,
    val isDeleting: Boolean = false,
    // ---- 卡片复习 ----
    val isReviewOpen: Boolean = false,
    val reviewQueue: List<VocabularyItem> = emptyList(),
    val currentIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isSubmitting: Boolean = false,
    val reviewResults: List<ReviewResult> = emptyList(),
    val reviewFinished: Boolean = false
) {
    /** 统计卡：总计 / 待复习（到期且未掌握）/ 已掌握（对齐 Web stats） */
    val total: Int get() = vocabulary.size
    val dueCount: Int
        get() = vocabulary.count { isDue(it.nextReviewAt) && it.status != "MASTERED" }
    val masteredCount: Int get() = vocabulary.count { it.status == "MASTERED" }

    /** 当前过滤 + 排序后的列表（对齐 Web filteredList） */
    val filteredList: List<VocabularyItem>
        get() = filterAndSortVocabulary(vocabulary, filterStatus, searchQuery, sortMethod)

    /** 当前复习卡（队列取尽为 null） */
    val currentCard: VocabularyItem? get() = reviewQueue.getOrNull(currentIndex)
}

@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(VocabularyUiState())
    val uiState: StateFlow<VocabularyUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() {
        _toast.value = null
    }

    init {
        load()
    }

    /**
     * @param silent true = 下拉刷新（保留现有内容仅转圈）；false = 首次/重试（整页 Loading）
     */
    fun load(silent: Boolean = false) {
        if (silent && _uiState.value.isRefreshing) return
        _uiState.update {
            if (silent) it.copy(isRefreshing = true, error = null)
            else it.copy(isLoading = true, error = null)
        }
        viewModelScope.launch {
            when (val result = contentRepository.getAllVocabulary()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, vocabulary = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, error = "网络连接失败")
                }
            }
        }
    }

    /** 下拉刷新入口 */
    fun refresh() = load(silent = true)

    // ---------- 列表管理 ----------

    fun setSearchQuery(query: String) = _uiState.update { it.copy(searchQuery = query) }

    fun setFilterStatus(tab: VocabStatusTab) = _uiState.update { it.copy(filterStatus = tab) }

    fun setSortMethod(method: VocabSortMethod) = _uiState.update { it.copy(sortMethod = method) }

    fun toggleExpanded(vocabularyid: Int) = _uiState.update {
        it.copy(expandedId = if (it.expandedId == vocabularyid) null else vocabularyid)
    }

    /** 请求删除：弹出确认弹窗 */
    fun requestDelete(item: VocabularyItem) = _uiState.update { it.copy(deletingItem = item) }

    fun dismissDelete() = _uiState.update { it.copy(deletingItem = null, isDeleting = false) }

    fun confirmDelete() {
        val target = _uiState.value.deletingItem ?: return
        if (_uiState.value.isDeleting) return
        _uiState.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            when (val result = contentRepository.deleteVocabulary(target.vocabularyid)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deletingItem = null,
                            vocabulary = it.vocabulary.filterNot { v -> v.vocabularyid == target.vocabularyid },
                            expandedId = if (it.expandedId == target.vocabularyid) null else it.expandedId
                        )
                    }
                    _toast.value = "已从生词本中彻底删除"
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _toast.value = "网络错误，删除失败"
                }
            }
        }
    }

    /** 标记为已掌握 / 重新学习（对齐 Web toggleStatus） */
    fun toggleStatus(item: VocabularyItem) {
        val mastered = item.status != "MASTERED"
        viewModelScope.launch {
            when (val result = contentRepository.updateVocabularyStatus(item.vocabularyid, mastered)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            vocabulary = state.vocabulary.map { v ->
                                if (v.vocabularyid == item.vocabularyid) {
                                    v.copy(status = if (mastered) "MASTERED" else "LEARNING")
                                } else v
                            }
                        )
                    }
                    _toast.value = if (mastered) "已标记为掌握" else "已放回生词本"
                }
                is Result.Error -> _toast.value = result.message
                Result.NetworkError -> _toast.value = "网络错误，保存失败"
            }
        }
    }

    // ---------- 卡片复习 ----------

    /** 开始复习：入队全部到期且未掌握的生词（对齐 Web startReview） */
    fun startReview() {
        val due = _uiState.value.vocabulary.filter { isDue(it.nextReviewAt) && it.status != "MASTERED" }
        if (due.isEmpty()) return
        _uiState.update {
            it.copy(
                isReviewOpen = true,
                reviewQueue = due,
                currentIndex = 0,
                isFlipped = false,
                reviewResults = emptyList(),
                reviewFinished = false
            )
        }
    }

    fun flipCard() = _uiState.update { it.copy(isFlipped = !it.isFlipped) }

    /**
     * 提交一次复习评价（0=忘记 1=模糊 2=认识 3=简单）：
     * 服务端按 Leitner 算法更新后回写本地列表，然后自动进入下一张；
     * 最后一张提交完进入总结页（对齐 Web handleSRS）。
     */
    fun submitReview(quality: Int) {
        val state = _uiState.value
        val card = state.currentCard ?: return
        if (state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = contentRepository.submitVocabularyReview(card.vocabularyid, quality)) {
                is Result.Success -> {
                    val outcome = result.data
                    _uiState.update { s ->
                        val vocabulary = s.vocabulary.map { v ->
                            if (v.vocabularyid == outcome.vocabularyid) {
                                v.copy(proficiency = outcome.proficiency, nextReviewAt = outcome.nextReviewAt)
                            } else v
                        }
                        val queue = s.reviewQueue.map { v ->
                            if (v.vocabularyid == outcome.vocabularyid) {
                                v.copy(proficiency = outcome.proficiency, nextReviewAt = outcome.nextReviewAt)
                            } else v
                        }
                        // 同词重评覆盖旧结果（回滑重测场景）
                        val results = s.reviewResults
                            .filterNot { it.vocabularyid == card.vocabularyid } +
                            ReviewResult(card.vocabularyid, card.word, quality)
                        val isLast = s.currentIndex >= s.reviewQueue.size - 1
                        s.copy(
                            vocabulary = vocabulary,
                            reviewQueue = queue,
                            reviewResults = results,
                            isSubmitting = false,
                            isFlipped = if (isLast) s.isFlipped else false,
                            currentIndex = if (isLast) s.currentIndex else s.currentIndex + 1,
                            reviewFinished = isLast
                        )
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.value = "网络错误，保存进度失败"
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isSubmitting = false) }
                    _toast.value = "网络错误，保存进度失败"
                }
            }
        }
    }

    /** 浏览式下一张（不评分，滑动/按钮触发） */
    fun goToNextCard() = _uiState.update {
        if (it.currentIndex < it.reviewQueue.size - 1) {
            it.copy(currentIndex = it.currentIndex + 1, isFlipped = false)
        } else it
    }

    /** 浏览式上一张（不评分，回到上一张重看；重评时覆盖原结果） */
    fun goToPrevCard() = _uiState.update {
        if (it.currentIndex > 0) {
            it.copy(currentIndex = it.currentIndex - 1, isFlipped = false)
        } else it
    }

    /** 总结页「再来一轮」：把本轮标记为忘记的词重新入队（对齐 Web retryForgotten） */
    fun retryForgotten() {
        val state = _uiState.value
        val forgottenIds = state.reviewResults.filter { it.quality == ReviewQuality.FORGOT }
            .map { it.vocabularyid }.toSet()
        val forgotten = state.vocabulary.filter { it.vocabularyid in forgottenIds }
        if (forgotten.isEmpty()) {
            closeReview()
            _toast.value = "所有生词都已掌握！"
            return
        }
        _uiState.update {
            it.copy(
                reviewQueue = forgotten,
                currentIndex = 0,
                isFlipped = false,
                reviewResults = emptyList(),
                reviewFinished = false
            )
        }
    }

    fun closeReview() = _uiState.update {
        it.copy(
            isReviewOpen = false,
            reviewQueue = emptyList(),
            currentIndex = 0,
            isFlipped = false,
            reviewResults = emptyList(),
            reviewFinished = false
        )
    }
}
