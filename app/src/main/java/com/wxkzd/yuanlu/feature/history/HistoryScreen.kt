package com.wxkzd.yuanlu.feature.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.formatMillis
import java.util.Locale

/**
 * 收听历史（复刻 Web /library/history 移动端，参照设计稿图1）：
 * 顶部导航 + 全部/进行中/已完成分段过滤 + 最近播放高亮卡（继续播放按钮）+
 * 今天/昨天/更早时间分组（stickyHeader 吸顶）+ 左右布局历史卡片（进度条/状态徽章），
 * 支持下拉刷新与滚动到底自动加载更多。
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HistoryRoute(
    onBack: () -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---- 顶部导航栏 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "收听历史",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        when {
            state.isLoading -> LoadingBox()
            state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::retry)
            else -> HistoryContent(
                state = state,
                onSelectFilter = viewModel::selectFilter,
                onRefresh = viewModel::refresh,
                onLoadMore = viewModel::loadMore,
                onOpenEpisode = onOpenEpisode
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun HistoryContent(
    state: HistoryUiState,
    onSelectFilter: (HistoryFilter) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenEpisode: (String) -> Unit
) {
    val listState = rememberLazyListState()

    // 滚动接近末尾时预加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 状态过滤器：全部 / 进行中 / 已完成 ----
            item(key = "filter") {
                HistoryFilterBar(
                    selected = state.filter,
                    total = state.total,
                    onSelect = onSelectFilter
                )
            }

            // ---- 时间分组列表（今天/昨天/具体日期，分组标题带日历图标吸顶） ----
            state.groups.forEach { group ->
                stickyHeader(key = "header_${group.label}") {
                    HistoryGroupHeader(label = group.label)
                }
                items(group.items, key = { "history_${it.historyid}" }) { item ->
                    HistoryRowCard(item = item, onClick = { onOpenEpisode(item.episode.id) })
                }
            }

            // ---- 列表为空 ----
            if (state.groups.isEmpty() && state.items.isEmpty() && !state.isLoadingMore) {
                item(key = "empty") { EmptyBox("暂无收听记录") }
            }

            // ---- 底部：加载更多 / 到底 ----
            item(key = "footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        state.endReached && state.items.isNotEmpty() -> Text(
                            text = "已经到底了",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 分段过滤器（样式与收藏页 Tab 切换卡同款） */
@Composable
private fun HistoryFilterBar(
    selected: HistoryFilter,
    total: Int,
    onSelect: (HistoryFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isSelected && filter == HistoryFilter.ALL) "全部 ($total)" else filter.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 时间分组标题（吸顶）：日历图标 + 文字同行居中，右侧细分隔线 */
@Composable
private fun HistoryGroupHeader(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.DateRange,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * 普通历史卡片（左右布局）：左 16:9 封面，右侧 状态徽章（已完成/百分比）+ 播客名 +
 * 标题 + 进度条与进度文本。卡片整体与收藏页剧集卡片同款视觉。
 */
@Composable
private fun HistoryRowCard(item: HistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
        ) {
            CoverImage(
                url = item.episode.thumbnailUrl,
                contentDescription = item.episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            // 状态徽章 + 所属播客名
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HistoryStatusBadge(item = item)
                if (item.episode.category.isNotBlank()) {
                    Text(
                        text = item.episode.category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 进度条 + 进度文本（呼应"进行中/已完成"状态）
            LinearProgressIndicator(
                progress = { item.progressRatio },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = progressText(item),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 状态徽章：已完成（CheckCircle）/ 进行中（百分比） */
@Composable
private fun HistoryStatusBadge(item: HistoryItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Icon(
            imageVector = if (item.isFinished) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = if (item.isFinished) "已完成"
            else "${Math.round(item.progressRatio * 100)}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** 进度文本：已听 5:00 / 12:34（已完成显示完整时长） */
private fun progressText(item: HistoryItem): String {
    val duration = item.episode.duration.ifBlank { formatMillis(item.episode.durationSeconds * 1000L) }
    val listened = formatMillis(item.progressSeconds * 1000L)
    return "已听 $listened / $duration"
}
