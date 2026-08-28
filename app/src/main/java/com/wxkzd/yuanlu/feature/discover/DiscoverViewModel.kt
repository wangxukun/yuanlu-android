package com.wxkzd.yuanlu.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 频道入口条目（由播客列表按 platform 聚合） */
data class ChannelEntry(
    val name: String,
    val podcastCount: Int
)

data class DiscoverUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    /** null = 未处于搜索态；空列表 = 搜索无结果 */
    val searchResults: List<Podcast>? = null,
    val isSearching: Boolean = false,
    val tags: List<Tag> = emptyList(),
    val selectedTag: Tag? = null,
    val podcasts: List<Podcast> = emptyList(),
    // ---- 区块数据（均由全量播客列表客户端派生） ----
    val trending: List<Podcast> = emptyList(),      // totalPlays 降序 TOP10
    val editorPicks: List<Podcast> = emptyList(),   // isEditorPick
    val newPodcasts: List<Podcast> = emptyList(),   // createAt 降序前 8
    val channels: List<ChannelEntry> = emptyList()  // platform 聚合 + 播客数
)

@OptIn(FlowPreview::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val queryInput = MutableStateFlow("")
    private var searchJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            queryInput.debounce(400L).collect { query ->
                if (query.isBlank()) {
                    _uiState.update { it.copy(searchResults = null, isSearching = false) }
                } else {
                    searchPodcasts(query)
                }
            }
        }
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val tagsResult = contentRepository.getTags()
            val podcastsResult = contentRepository.getPodcasts()

            when (podcastsResult) {
                is Result.Success -> {
                    val podcasts = podcastsResult.data
                    val tags = (tagsResult as? Result.Success)?.data ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            podcasts = podcasts,
                            tags = tags,
                            trending = podcasts.sortedByDescending { p -> p.totalPlays }.take(TRENDING_LIMIT),
                            editorPicks = podcasts.filter { p -> p.isEditorPick },
                            newPodcasts = podcasts
                                .filter { p -> p.createAt != null }
                                .sortedByDescending { p -> p.createAt }
                                .take(NEW_LIMIT),
                            channels = podcasts
                                .groupBy { p -> p.platform }
                                .filterKeys { name -> !name.isNullOrBlank() }
                                .map { (name, group) -> ChannelEntry(name!!, group.size) }
                                .sortedByDescending { entry -> entry.podcastCount }
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = podcastsResult.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "网络连接失败")
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        // 清空时立即回浏览态，不等防抖（避免残留旧结果 400ms）
        if (query.isBlank()) {
            searchJob?.cancel()
            _uiState.update { it.copy(searchResults = null, isSearching = false) }
        }
        queryInput.value = query
    }

    private fun searchPodcasts(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            when (val result = contentRepository.searchPodcasts(query.trim())) {
                is Result.Success -> _uiState.update {
                    it.copy(isSearching = false, searchResults = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSearching = false, searchResults = emptyList())
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isSearching = false, searchResults = emptyList())
                }
            }
        }
    }

    fun selectTag(tag: Tag?) {
        _uiState.update { it.copy(selectedTag = tag) }
    }

    companion object {
        const val TRENDING_LIMIT = 10
        const val NEW_LIMIT = 8
    }
}
