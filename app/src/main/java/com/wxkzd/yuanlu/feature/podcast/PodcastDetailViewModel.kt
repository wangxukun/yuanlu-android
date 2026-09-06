package com.wxkzd.yuanlu.feature.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.feature.favorites.FavoriteCenter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PodcastDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val podcast: Podcast? = null,
    val isFavorited: Boolean = false,
    /** 收藏请求在途（对齐 Web isLoadingFavorite：禁用按钮防连点） */
    val isFavoriteBusy: Boolean = false,
    val channelPodcasts: List<Podcast> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val sortAscending: Boolean = false
)

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val contentRepository: ContentRepository,
    private val favoriteCenter: FavoriteCenter
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    /** 一次性提示（收藏成功/失败等），消费后置空 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var podcastid: String? = null
    private var favoriteKey: FavoriteCenter.Key? = null

    init {
        // 全局收藏中心驱动：收藏列表等任意页面取消收藏后，本页图标同步翻转
        viewModelScope.launch {
            favoriteCenter.states.collect { table ->
                val key = favoriteKey ?: return@collect
                table[key]?.let { favorited ->
                    _uiState.update { it.copy(isFavorited = favorited) }
                }
            }
        }
        viewModelScope.launch {
            favoriteCenter.pendingKeys.collect { pending ->
                val key = favoriteKey ?: return@collect
                _uiState.update { it.copy(isFavoriteBusy = key in pending) }
            }
        }
        viewModelScope.launch {
            favoriteCenter.messages.collect { _toast.value = it }
        }
    }

    fun load(podcastid: String) {
        if (this.podcastid == podcastid) return
        this.podcastid = podcastid
        val key = FavoriteCenter.Key(FavoriteCenter.Target.PODCAST, podcastid)
        favoriteKey = key
        _uiState.value = PodcastDetailUiState(isLoading = true)

        viewModelScope.launch {
            when (val detail = contentRepository.getPodcastDetail(podcastid)) {
                is Result.Success -> {
                    // 收藏态初值：优先全局中心已知值；否则查 find-unique
                    //（detail 接口走 Cookie 态，移动端 Bearer 下 isFavorited 恒为 false，仅作兜底）
                    val initialFavorite = favoriteCenter.knownState(key)
                        ?: when (val check = contentRepository.checkPodcastFavorite(podcastid)) {
                            is Result.Success -> check.data
                            else -> detail.data.isFavorited
                        }
                    favoriteCenter.seedIfAbsent(key, initialFavorite)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            podcast = detail.data.podcast,
                            isFavorited = initialFavorite,
                            channelPodcasts = detail.data.channelPodcasts
                        )
                    }
                    loadEpisodesPage(1)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = detail.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "网络连接失败")
                }
            }
        }
    }

    /** 收藏/取消收藏：乐观翻转由 FavoriteCenter 统一处理并广播（对齐 Web handleToggleFavorite） */
    fun toggleFavorite() {
        podcastid?.let { favoriteCenter.togglePodcast(it) }
    }

    fun consumeToast() {
        _toast.value = null
    }

    fun toggleSort() {
        val ascending = !_uiState.value.sortAscending
        _uiState.update {
            it.copy(
                sortAscending = ascending,
                episodes = emptyList(),
                total = 0,
                page = 0,
                endReached = false
            )
        }
        loadEpisodesPage(1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached || state.error != null) return
        loadEpisodesPage(state.page + 1)
    }

    fun retry() {
        podcastid?.let { load(it) }
    }

    private fun loadEpisodesPage(page: Int) {
        val id = podcastid ?: return
        val ascending = _uiState.value.sortAscending
        if (page > 1) _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = contentRepository.getPodcastEpisodes(
                podcastid = id,
                page = page,
                limit = PAGE_SIZE,
                ascending = ascending
            )) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        episodes = if (page == 1) result.data.episodes else it.episodes + result.data.episodes,
                        total = result.data.total,
                        page = page,
                        endReached = !result.data.hasMore
                    )
                }
                is Result.Error -> _uiState.update { it.copy(isLoadingMore = false) }
                Result.NetworkError -> _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
