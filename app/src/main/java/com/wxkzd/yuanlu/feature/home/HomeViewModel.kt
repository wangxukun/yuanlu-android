package com.wxkzd.yuanlu.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val editorPicks: List<Podcast> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val page: Int = 0
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.value = HomeUiState(isLoading = true)
        viewModelScope.launch {
            val picksDeferred = async { contentRepository.getPodcasts() }
            val firstPage = contentRepository.getLatestEpisodes(page = 1, pageSize = PAGE_SIZE)

            when (firstPage) {
                is Result.Success -> {
                    val picks = (picksDeferred.await() as? Result.Success)
                        ?.data
                        ?.filter { it.isEditorPick }
                        .orEmpty()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            editorPicks = picks,
                            episodes = firstPage.data,
                            page = 1,
                            endReached = firstPage.data.size < PAGE_SIZE
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = firstPage.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network connection failed")
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached || state.error != null) return
        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val nextPage = state.page + 1
            when (val result = contentRepository.getLatestEpisodes(nextPage, PAGE_SIZE)) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        episodes = it.episodes + result.data,
                        page = nextPage,
                        // 后端该端点无 hasMore 标记：不足一页即认为到底
                        endReached = result.data.size < PAGE_SIZE
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
