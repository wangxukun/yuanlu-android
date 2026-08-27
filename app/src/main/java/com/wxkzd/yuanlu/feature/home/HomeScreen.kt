package com.wxkzd.yuanlu.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.ui.components.EpisodeRow
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::refresh)
        state.episodes.isEmpty() && state.editorPicks.isEmpty() -> EmptyBox("No episodes yet")
        else -> HomeContent(
            state = state,
            onOpenPodcast = onOpenPodcast,
            onOpenEpisode = onOpenEpisode,
            onLoadMore = viewModel::loadMore
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onLoadMore: () -> Unit
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        if (state.editorPicks.isNotEmpty()) {
            item(key = "picks_header") {
                SectionHeader("Editor's Picks", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "picks_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.editorPicks, key = { it.podcastid }) { podcast ->
                        Box(modifier = Modifier.width(144.dp)) {
                            PodcastCard(podcast = podcast, onClick = { onOpenPodcast(podcast.podcastid) })
                        }
                    }
                }
            }
            item(key = "latest_header") {
                SectionHeader("Latest Episodes", modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        itemsIndexed(state.episodes, key = { _, episode -> episode.episodeid }) { index, episode ->
            EpisodeRow(episode = episode, onClick = { onOpenEpisode(episode.episodeid) })
            if (index < state.episodes.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 92.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
        item(key = "footer") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoadingMore -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    state.endReached && state.episodes.isNotEmpty() -> Text(
                        text = "You've reached the end",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
