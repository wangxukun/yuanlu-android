package com.wxkzd.yuanlu.feature.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.SpeechNotebook
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PronunciationNotebookUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val isRefreshing: Boolean = false,
    val notebook: SpeechNotebook? = null
)

/**
 * 发音弱项本主页（复刻 Web /library/pronunciation SSR 页）：
 * 一次拉取 notebook 聚合（画像/音素统计/试用切片弱项列表）；
 * 闯关复习返回时 refresh 刷新弱项列表（达标句子已从后端弱项集移除）。
 */
@HiltViewModel
class PronunciationNotebookViewModel @Inject constructor(
    private val speechRepository: SpeechRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PronunciationNotebookUiState())
    val uiState: StateFlow<PronunciationNotebookUiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        _uiState.update {
            it.copy(isLoading = it.notebook == null, loadError = null, isRefreshing = it.notebook != null)
        }
        viewModelScope.launch {
            when (val r = speechRepository.getNotebook()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, notebook = r.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = r.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, loadError = "网络连接失败")
                }
            }
        }
    }

    /** 下拉刷新 / 闯关复习返回后的手动刷新 */
    fun refresh() = load()

    fun retry() = load()

    /**
     * 页面重新进入组合（从闯关复习/达人榜返回）时刷新弱项列表——
     * 达标句子已被后端移出弱项集；首次进入时 init 的加载已在途，跳过。
     */
    fun onReenter() {
        val state = _uiState.value
        if (state.notebook != null || state.loadError != null) load()
    }
}
