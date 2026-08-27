package com.wxkzd.yuanlu.feature.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.EpisodeRow
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader

@Composable
fun PodcastDetailRoute(
    podcastid: String,
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: PodcastDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(podcastid) {
        viewModel.load(podcastid)
    }
    PodcastDetailScreen(
        onBack = onBack,
        onOpenPodcast = onOpenPodcast,
        onOpenChannel = onOpenChannel,
        onOpenEpisode = onOpenEpisode,
        viewModel = viewModel
    )
}

@Composable
fun PodcastDetailScreen(
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    viewModel: PodcastDetailViewModel
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::retry)
        state.podcast == null -> EmptyBox("Podcast not found")
        else -> PodcastDetailContent(
            state = state,
            onBack = onBack,
            onOpenPodcast = onOpenPodcast,
            onOpenChannel = onOpenChannel,
            onOpenEpisode = onOpenEpisode,
            onLoadMore = viewModel::loadMore,
            onToggleSort = viewModel::toggleSort,
            modifier = Modifier.statusBarsPadding()
        )
    }
}

@Composable
private fun PodcastDetailContent(
    state: PodcastDetailUiState,
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleSort: () -> Unit,
    modifier: Modifier = Modifier
) {
    val podcast = state.podcast!!
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
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // ---- 头部 ----
        item(key = "header") {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    CoverImage(
                        url = podcast.coverUrl,
                        contentDescription = podcast.title,
                        modifier = Modifier.size(96.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = podcast.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val meta = listOfNotNull(
                            "${state.total} episodes",
                            podcast.platform
                        ).joinToString(" · ")
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        podcast.platform?.let { platform ->
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "More in $platform",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onOpenChannel(platform) }
                            )
                        }
                    }
                }
                if (podcast.tags.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        podcast.tags.take(4).forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag.name) })
                        }
                    }
                }
                podcast.description?.takeIf { it.isNotBlank() }?.let { desc ->
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // ---- 排序切换 ----
        item(key = "sort") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = false,
                    onClick = onToggleSort,
                    label = {
                        Text(if (state.sortAscending) "Oldest first" else "Newest first")
                    }
                )
            }
        }

        // ---- 剧集列表 ----
        if (state.episodes.isEmpty() && !state.isLoadingMore) {
            item(key = "empty_episodes") { EmptyBox("No episodes yet") }
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

        // ---- 同频道播客 ----
        if (state.channelPodcasts.isNotEmpty()) {
            item(key = "channel_header") {
                SectionHeader(
                    "More in ${podcast.platform ?: "this channel"}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "channel_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.channelPodcasts, key = { it.podcastid }) { other ->
                        Box(modifier = Modifier.width(144.dp)) {
                            PodcastCard(podcast = other, onClick = { onOpenPodcast(other.podcastid) })
                        }
                    }
                }
            }
        }
    }
}
