package com.wxkzd.yuanlu.feature.learningpath

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.LearningPathEpisode
import com.wxkzd.yuanlu.domain.model.PathEpisodeSearchItem
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LearningPathDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val detail: com.wxkzd.yuanlu.domain.model.LearningPathDetail? = null,
    /** 从 JWT 解出的用户角色（USER | PREMIUM | ADMIN）：播放全部时过滤专属剧集用 */
    val userRole: String? = null,
    // 添加剧集弹窗（仅拥有者）
    val isAddDialogOpen: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PathEpisodeSearchItem> = emptyList(),
    val isSearching: Boolean = false,
    // 编辑弹窗保存 / 删除路径 / 移除剧集在途（防重复提交）
    val isSaving: Boolean = false,
    val isMutating: Boolean = false
)

/**
 * 「某一路径」详情页（复刻 Web /library/learning-paths/[id]）：
 * - isOwner 由仓库层（创建者 userid vs 当前登录用户）得出，驱动条件渲染：
 *   拥有者 = 添加剧集按钮 + 编辑/删除/分享菜单 + 剧集行移除图标；
 *   非拥有者（从「发现」进入）= 仅播放全部/随机 + 分享。
 * - 播放全部/随机播放：过滤无权限的专属剧集后整体入队（PlayerController.playAll）。
 * - 添加剧集弹窗：500ms 防抖搜索 published 剧集，已在路径中的置灰。
 */
@HiltViewModel
class LearningPathDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val playerController: PlayerController,
    private val tokenStore: TokenStore,
    private val learningPathCenter: LearningPathCenter
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearningPathDetailUiState())
    val uiState: StateFlow<LearningPathDetailUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 删除成功信号：Route 观察后退回列表页 */
    private val _navigateBack = MutableStateFlow(false)
    val navigateBack: StateFlow<Boolean> = _navigateBack.asStateFlow()

    private var loadedPathId: Int? = null
    private var searchJob: Job? = null

    fun load(pathId: Int) {
        if (loadedPathId == pathId) return
        loadedPathId = pathId
        _uiState.value = LearningPathDetailUiState()
        viewModelScope.launch {
            val role = tokenStore.roleFlow.first()
            when (val result = contentRepository.getLearningPath(pathId)) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, error = null, detail = result.data, userRole = role)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                Result.NetworkError -> _uiState.update { it.copy(isLoading = false, error = "网络连接失败") }
            }
        }
    }

    fun retry() {
        loadedPathId?.let { id ->
            loadedPathId = null
            load(id)
        }
    }

    // ---------- 播放 ----------

    /** 播放全部 / 随机播放：过滤专属剧集后入队起播（对齐 Web handlePlayAll） */
    fun playAll(shuffle: Boolean) {
        val state = _uiState.value
        val items = state.detail?.items ?: return
        if (items.isEmpty()) return
        val (queue, skipped) = buildPlayQueue(items, state.userRole)
        when {
            queue.isEmpty() -> _toast.value = "路径中的剧集均为专属内容，升级会员后可播放"
            else -> {
                if (skipped > 0) _toast.value = "已跳过 $skipped 个专属剧集"
                val first = queue.first()
                playerController.playAll(
                    episodes = queue,
                    startPositionMs = resumePositionMs(first),
                    shuffle = shuffle
                )
            }
        }
    }

    // ---------- 拥有者：编辑 / 删除 / 添加剧集 / 移除剧集 ----------

    /** 编辑路径（编辑弹窗提交） */
    fun updatePath(pathName: String, description: String, isPublic: Boolean, onSaved: () -> Unit) {
        val pathid = loadedPathId ?: return
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            when (val result = contentRepository.updateLearningPath(pathid, pathName.trim(), description.trim().ifBlank { null }, isPublic)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _toast.value = "已保存"
                    onSaved()
                    reload()
                    learningPathCenter.notifyChanged()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _toast.value = "网络连接失败，请稍后重试"
                }
            }
        }
    }

    /** 删除路径（确认弹窗后调用）：成功后退回列表页 */
    fun deletePath() {
        val pathid = loadedPathId ?: return
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            when (val result = contentRepository.deleteLearningPath(pathid)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false) }
                    learningPathCenter.notifyChanged()
                    _navigateBack.value = true
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _toast.value = "网络连接失败，请稍后重试"
                }
            }
        }
    }

    fun setAddDialogOpen(open: Boolean) {
        _uiState.update {
            it.copy(
                isAddDialogOpen = open,
                // 关弹窗时一并清掉搜索态，下次打开从干净状态开始
                searchQuery = if (open) it.searchQuery else "",
                searchResults = if (open) it.searchResults else emptyList()
            )
        }
    }

    /** 添加剧集弹窗搜索（500ms 防抖，对齐 Web useDebounce） */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _uiState.update { it.copy(isSearching = true) }
            when (val result = contentRepository.searchEpisodesForPath(query.trim())) {
                is Result.Success -> _uiState.update {
                    it.copy(isSearching = false, searchResults = result.data)
                }
                is Result.Error -> _uiState.update { it.copy(isSearching = false) }
                Result.NetworkError -> _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    /** 添加剧集到路径末尾（客户端先去重，对齐 Web handleAddEpisode） */
    fun addEpisode(episodeid: String) {
        val pathid = loadedPathId ?: return
        val detail = _uiState.value.detail ?: return
        if (detail.items.any { it.episode.episodeid == episodeid }) {
            _toast.value = "该剧集已在列表中"
            return
        }
        viewModelScope.launch {
            when (val result = contentRepository.addEpisodeToLearningPath(pathid, episodeid)) {
                is Result.Success -> {
                    _toast.value = "已添加到路径"
                    reload()
                    learningPathCenter.notifyChanged()
                }
                is Result.Error -> _toast.value = result.message
                Result.NetworkError -> _toast.value = "网络连接失败，请稍后重试"
            }
        }
    }

    /** 从路径移除剧集（确认弹窗后调用，itemId = learning_path_items.id） */
    fun removeEpisode(itemId: Int) {
        val pathid = loadedPathId ?: return
        if (_uiState.value.isMutating) return
        _uiState.update { it.copy(isMutating = true) }
        viewModelScope.launch {
            when (val result = contentRepository.removeEpisodeFromLearningPath(pathid, itemId)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _toast.value = "已从路径移除"
                    reload()
                    learningPathCenter.notifyChanged()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _toast.value = "网络连接失败，请稍后重试"
                }
            }
        }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun consumeNavigateBack() {
        _navigateBack.value = false
    }

    /** 变更后重拉详情（保持弹窗等本地态不重置） */
    private fun reload() {
        val pathid = loadedPathId ?: return
        viewModelScope.launch {
            when (val result = contentRepository.getLearningPath(pathid)) {
                is Result.Success -> _uiState.update {
                    it.copy(detail = result.data, error = null, userRole = it.userRole)
                }
                // 重拉失败不打断页面，提示即可（数据仍为变更前的快照）
                is Result.Error -> _toast.value = result.message
                Result.NetworkError -> _toast.value = "网络连接失败"
            }
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 500L

        /**
         * 构建可播队列：过滤当前用户无权限的专属剧集（PREMIUM/ADMIN 放行，
         * 对齐 Web hasExclusivePermission），返回 (队列, 被跳过数)。
         */
        fun buildPlayQueue(
            items: List<LearningPathEpisode>,
            role: String?
        ): Pair<List<Episode>, Int> {
            val hasExclusivePermission = role == "PREMIUM" || role == "ADMIN"
            val playable = items
                .filter { !(it.episode.isExclusive && !hasExclusivePermission) }
                .map { it.episode }
            return playable to (items.size - playable.size)
        }

        /** 断点续播起点（对齐 PlayerViewModel.resumePositionMs 口径） */
        fun resumePositionMs(episode: Episode): Long {
            val progress = episode.progressSeconds
            if (progress <= 30) return 0L
            val duration = episode.duration
            if (duration > 0 && progress >= duration - 15) return 0L
            return progress * 1000L
        }
    }
}
