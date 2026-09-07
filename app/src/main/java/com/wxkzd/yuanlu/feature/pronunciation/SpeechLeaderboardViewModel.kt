package com.wxkzd.yuanlu.feature.pronunciation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.LeaderboardMetric
import com.wxkzd.yuanlu.domain.model.LeaderboardPeriod
import com.wxkzd.yuanlu.domain.model.SpeechLeaderboard
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpeechLeaderboardUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val period: LeaderboardPeriod = LeaderboardPeriod.WEEKLY,
    val metric: LeaderboardMetric = LeaderboardMetric.SCORE,
    /** 各 (period × metric) 象限的缓存，Tab 来回切换即时呈现 */
    val boards: Map<Pair<LeaderboardPeriod, LeaderboardMetric>, SpeechLeaderboard> = emptyMap()
) {
    val current: SpeechLeaderboard?
        get() = boards[period to metric]
}

/**
 * 发音达人榜（复刻 Web /library/pronunciation/leaderboard）：
 * 周期（近7天/今日）× 指标（平均分榜/勤奋榜）四象限切换，切 Tab 即拉取并缓存。
 */
@HiltViewModel
class SpeechLeaderboardViewModel @Inject constructor(
    private val speechRepository: SpeechRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeechLeaderboardUiState())
    val uiState: StateFlow<SpeechLeaderboardUiState> = _uiState.asStateFlow()

    /** 进行中的请求所属象限（避免快速切换 Tab 时旧响应覆盖新象限） */
    private var loadingKey: Pair<LeaderboardPeriod, LeaderboardMetric>? = null

    init { fetch(LeaderboardPeriod.WEEKLY, LeaderboardMetric.SCORE) }

    fun selectPeriod(period: LeaderboardPeriod) {
        if (_uiState.value.period == period) return
        _uiState.update { it.copy(period = period, loadError = null) }
        ensureLoaded(period, _uiState.value.metric)
    }

    fun selectMetric(metric: LeaderboardMetric) {
        if (_uiState.value.metric == metric) return
        _uiState.update { it.copy(metric = metric, loadError = null) }
        ensureLoaded(_uiState.value.period, metric)
    }

    fun retry() = ensureLoaded(_uiState.value.period, _uiState.value.metric)

    private fun ensureLoaded(period: LeaderboardPeriod, metric: LeaderboardMetric) {
        val key = period to metric
        if (key in _uiState.value.boards) return
        fetch(period, metric)
    }

    private fun fetch(period: LeaderboardPeriod, metric: LeaderboardMetric) {
        val key = period to metric
        loadingKey = key
        _uiState.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            when (val r = speechRepository.getLeaderboard(period, metric)) {
                is Result.Success -> {
                    // 仅当仍是当前选中象限（或仍在加载同一象限）时落地，避免竞态错位
                    if (loadingKey == key) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                boards = it.boards + (key to r.data)
                            )
                        }
                    }
                }
                is Result.Error -> {
                    if (loadingKey == key) {
                        _uiState.update { it.copy(isLoading = false, loadError = r.message) }
                    }
                }
                Result.NetworkError -> {
                    if (loadingKey == key) {
                        _uiState.update { it.copy(isLoading = false, loadError = "网络连接失败") }
                    }
                }
            }
        }
    }
}
