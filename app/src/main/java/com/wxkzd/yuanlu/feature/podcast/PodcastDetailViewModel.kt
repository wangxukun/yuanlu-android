package com.wxkzd.yuanlu.feature.podcast

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.repository.ContentRepository
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
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PodcastDetailUiState())
    val uiState: StateFlow<PodcastDetailUiState> = _uiState.asStateFlow()

    private var podcastid: String? = null

    fun load(podcastid: String) {
        if (this.podcastid == podcastid) return
        this.podcastid = podcastid
        _uiState.value = PodcastDetailUiState(isLoading = true)

        viewModelScope.launch {
            when (val detail = contentRepository.getPodcastDetail(podcastid)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            podcast = detail.data.podcast,
                            isFavorited = detail.data.isFavorited,
                            channelPodcasts = detail.data.channelPodcasts
                        )
                    }
                    loadEpisodesPage(1)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = detail.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network connection failed")
                }
            }
        }
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
