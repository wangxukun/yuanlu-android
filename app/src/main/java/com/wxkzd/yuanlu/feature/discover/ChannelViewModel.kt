package com.wxkzd.yuanlu.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.ChannelData
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChannelUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val channel: ChannelData? = null
)

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private var loadedName: String? = null

    fun load(name: String) {
        if (loadedName == name) return
        loadedName = name
        _uiState.value = ChannelUiState(isLoading = true)
        viewModelScope.launch {
            when (val result = contentRepository.getChannel(name)) {
                is Result.Success -> _uiState.update { it.copy(isLoading = false, channel = result.data) }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "Network connection failed")
                }
            }
        }
    }

    fun retry() {
        loadedName?.let { load(it) }
    }
}
