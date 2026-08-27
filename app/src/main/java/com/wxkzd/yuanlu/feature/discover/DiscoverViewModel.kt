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
    /** 由播客列表聚合出的频道（platform）名 */
    val channels: List<String> = emptyList()
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
                            channels = podcasts.mapNotNull { p -> p.platform?.takeIf(String::isNotBlank) }
                                .distinct()
                                .sorted()
                        )
                    }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = podcastsResult.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network connection failed")
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
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
}
