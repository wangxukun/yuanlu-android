package com.wxkzd.yuanlu.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** 状态过滤器（对齐 Web ListeningHistoryPage：all / in-progress / finished） */
enum class HistoryFilter(val apiValue: String, val label: String) {
    ALL("all", "全部"),
    IN_PROGRESS("in-progress", "进行中"),
    COMPLETED("finished", "已完成")
}

/** 时间分组单元：今天 / 昨天 / 具体日期（如 2026年9月4日） */
data class HistoryGroup(val label: String, val items: List<HistoryItem>)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val filter: HistoryFilter = HistoryFilter.ALL,
    val items: List<HistoryItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 0,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false
) {
    /** 按收听日期分组：今天 / 昨天 / 更早显示具体日期（如 2026年9月4日）。
     *  数据已按 listenAt 倒序返回，groupBy 的 LinkedHashMap 保持出现顺序，
     *  组间天然从近到远排列。 */
    val groups: List<HistoryGroup>
        get() = groupByTimeline(items)

    companion object {
        fun groupByTimeline(items: List<HistoryItem>): List<HistoryGroup> {
            val todayKey = calendarKey(Calendar.getInstance())
            @Suppress("UNCHECKED_CAST")
            val yesterday = (Calendar.getInstance().clone() as Calendar)
                .apply { add(Calendar.DAY_OF_YEAR, -1) }
            val yesterdayKey = calendarKey(yesterday)

            // groupBy 返回 LinkedHashMap，组顺序 = items（倒序）中首次出现的顺序
            val byLabel = items.groupBy { item -> timelineLabel(item, todayKey, yesterdayKey) }
            return byLabel.map { (label, grouped) -> HistoryGroup(label, grouped) }
        }

        private fun timelineLabel(item: HistoryItem, todayKey: Long, yesterdayKey: Long): String {
            val cal = parseUtcToLocal(item.listenAt) ?: return "更早"
            return when (calendarKey(cal)) {
                todayKey -> "今天"
                yesterdayKey -> "昨天"
                else -> chineseDateLabel(cal)
            }
        }

        /** 具体日期标签："2026年9月4日"（月/日无前导零） */
        private fun chineseDateLabel(cal: Calendar): String =
            "${cal.get(Calendar.YEAR)}年${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日"

        /** "2026-09-06T07:00:00.123Z" -> 本地时区 Calendar；解析失败返回 null */
        private fun parseUtcToLocal(iso: String?): Calendar? {
            if (iso.isNullOrBlank()) return null
            return try {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                parser.timeZone = TimeZone.getTimeZone("UTC")
                val date = parser.parse(iso) ?: return null
                Calendar.getInstance().apply { time = date }
            } catch (e: Exception) {
                null
            }
        }

        private fun calendarKey(cal: Calendar): Long =
            cal.get(Calendar.YEAR) * 1000L + cal.get(Calendar.DAY_OF_YEAR)
    }
}

/**
 * 收听历史（复刻 Web /library/history 移动端）：
 * 服务端分页 + 过滤（all/in-progress/finished），本地按 今天/昨天/具体日期 分组；
 * 切换过滤重置分页重拉；下拉刷新清空重载；滚动到底自动加载下一页。
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load(page = 1)
    }

    fun selectFilter(filter: HistoryFilter) {
        if (_uiState.value.filter == filter) return
        _uiState.update { it.copy(filter = filter, items = emptyList(), total = 0, page = 0, endReached = false, isLoading = true, error = null) }
        load(page = 1)
    }

    fun refresh() {
        _uiState.update { it.copy(isRefreshing = true) }
        load(page = 1)
    }

    fun retry() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        load(page = 1)
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.endReached || state.error != null) return
        load(page = state.page + 1)
    }

    private fun load(page: Int) {
        val filter = _uiState.value.filter
        if (page > 1) _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = contentRepository.getListeningHistory(
                page = page,
                pageSize = PAGE_SIZE,
                status = filter.apiValue
            )) {
                is Result.Success -> _uiState.update {
                    val fresh = page == 1
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        error = null,
                        items = if (fresh) result.data.items else it.items + result.data.items,
                        total = result.data.total,
                        page = page,
                        endReached = !result.data.hasMore
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, isLoadingMore = false, error = "网络连接失败")
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 20
    }
}
