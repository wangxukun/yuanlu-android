package com.wxkzd.yuanlu.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.FavoriteEpisode
import com.wxkzd.yuanlu.domain.model.FavoriteSeries
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 顶部 Tab：播客系列 / 单集（对齐 Web FavoritesPage activeTab） */
enum class FavoritesTab { PODCASTS, EPISODES }

data class FavoritesUiState(
    val isLoading: Boolean = true,
    /** 下拉刷新中（静默重载，保留现有内容，仅顶部转圈） */
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val activeTab: FavoritesTab = FavoritesTab.PODCASTS,
    val searchQuery: String = "",
    val podcasts: List<FavoriteSeries> = emptyList(),
    val episodes: List<FavoriteEpisode> = emptyList()
) {
    /** 关键字过滤（对齐 Web：标题/作者不区分大小写包含匹配） */
    val filteredPodcasts: List<FavoriteSeries>
        get() = searchQuery.takeIf { it.isNotBlank() }?.let { q ->
            podcasts.filter {
                it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true)
            }
        } ?: podcasts

    val filteredEpisodes: List<FavoriteEpisode>
        get() = searchQuery.takeIf { it.isNotBlank() }?.let { q ->
            episodes.filter {
                it.title.contains(q, ignoreCase = true) || it.author.contains(q, ignoreCase = true)
            }
        } ?: episodes
}

/**
 * 「我的收藏」列表（对齐 Web /library/favorites）：
 * 全量拉取两个 Tab 数据；取消收藏乐观移除 + 失败由 FavoriteCenter 回滚广播
 * （revision 变化 → 重取服务端，回滚项自然恢复，对齐 Web 的回滚 + revalidatePath）。
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val favoriteCenter: FavoriteCenter
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        load()
        // 任意页面发生收藏变更（含本页取消失败回滚）后刷新列表；
        // 跳过初值（0），避免与 load() 重复请求
        viewModelScope.launch {
            favoriteCenter.revision.collect { revision ->
                if (revision > 0) load(showLoading = false)
            }
        }
        viewModelScope.launch {
            favoriteCenter.messages.collect { _toast.value = it }
        }
    }

    /** 一次性提示（Toast），消费后置空 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    fun load(showLoading: Boolean = true) {
        if (showLoading) _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch { fetchFavorites() }
    }

    /** 下拉刷新：静默重载（保留现有列表，仅顶部转圈），完成收起指示器 */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            fetchFavorites()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchFavorites() {
        when (val result = contentRepository.getFavorites()) {
            is Result.Success -> {
                // 把服务端事实回填进全局中心：列表页的取消收藏依赖中心已知"已收藏"
                result.data.podcasts.forEach {
                    favoriteCenter.seedIfAbsent(FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, it.id), true)
                }
                result.data.episodes.forEach {
                    favoriteCenter.seedIfAbsent(FavoriteCenter.Key(FavoriteCenter.Target.EPISODE, it.id), true)
                }
                _uiState.update {
                    it.copy(isLoading = false, error = null, podcasts = result.data.podcasts, episodes = result.data.episodes)
                }
            }
            is Result.Error -> _uiState.update {
                it.copy(isLoading = false, error = result.message)
            }
            Result.NetworkError -> _uiState.update {
                it.copy(isLoading = false, error = "网络连接失败")
            }
        }
    }

    fun retry() = load()

    fun selectTab(tab: FavoritesTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * 取消收藏播客：乐观从列表移除，请求与回滚由 FavoriteCenter 统一处理
     * （显式 remove 语义：不依赖中心已知状态，避免把删除误判成插入）
     */
    fun removePodcast(id: String) {
        val snapshot = _uiState.value.podcasts
        _uiState.update { it.copy(podcasts = snapshot.filterNot { p -> p.id == id }) }
        favoriteCenter.removePodcast(id)
    }

    /** 取消收藏单集：乐观从列表移除 */
    fun removeEpisode(id: String) {
        val snapshot = _uiState.value.episodes
        _uiState.update { it.copy(episodes = snapshot.filterNot { e -> e.id == id }) }
        favoriteCenter.removeEpisode(id)
    }

    fun consumeToast() {
        _toast.value = null
    }
}
