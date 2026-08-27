package com.wxkzd.yuanlu.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            placeholder = { Text("Search podcasts") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            state.isLoading -> LoadingBox()
            state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::load)
            state.searchResults != null -> SearchResults(
                results = state.searchResults!!,
                isSearching = state.isSearching,
                query = state.query,
                onOpenPodcast = onOpenPodcast
            )
            else -> BrowseContent(
                state = state,
                onSelectTag = viewModel::selectTag,
                onOpenPodcast = onOpenPodcast,
                onOpenChannel = onOpenChannel
            )
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Podcast>,
    isSearching: Boolean,
    query: String,
    onOpenPodcast: (String) -> Unit
) {
    when {
        isSearching -> LoadingBox()
        results.isEmpty() -> EmptyBox("No podcasts found for \"$query\"")
        else -> PodcastGrid(podcasts = results, onOpenPodcast = onOpenPodcast)
    }
}

@Composable
private fun BrowseContent(
    state: DiscoverUiState,
    onSelectTag: (Tag?) -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val filtered = state.selectedTag
        ?.let { tag -> state.podcasts.filter { p -> p.tags.any { it.id == tag.id } } }
        ?: state.podcasts

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        if (state.channels.isNotEmpty()) {
            item(key = "channels_header") {
                SectionHeader("Channels", modifier = Modifier.padding(horizontal = 16.dp))
            }
            item(key = "channels_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.channels, key = { it }) { channel ->
                        FilterChip(
                            selected = false,
                            onClick = { onOpenChannel(channel) },
                            label = { Text(channel) }
                        )
                    }
                }
            }
        }
        item(key = "tags_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.selectedTag == null,
                    onClick = { onSelectTag(null) },
                    label = { Text("All") }
                )
                state.tags.forEach { tag ->
                    FilterChip(
                        selected = state.selectedTag?.id == tag.id,
                        onClick = { onSelectTag(if (state.selectedTag?.id == tag.id) null else tag) },
                        label = { Text(tag.name) }
                    )
                }
            }
        }
        item(key = "grid_header") {
            SectionHeader(
                if (state.selectedTag != null) state.selectedTag!!.name else "All Podcasts",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyBox(if (state.podcasts.isEmpty()) "No podcasts yet" else "No podcasts in this category")
            }
        } else {
            // 网格按两列拆行（容器是 LazyColumn，避免嵌套滚动）
            filtered.chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "row_$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { podcast ->
                            Box(modifier = Modifier.weight(1f)) {
                                PodcastCard(
                                    podcast = podcast,
                                    onClick = { onOpenPodcast(podcast.podcastid) }
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PodcastGrid(podcasts: List<Podcast>, onOpenPodcast: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        podcasts.chunked(2).forEachIndexed { rowIndex, rowItems ->
            item(key = "row_$rowIndex") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowItems.forEach { podcast ->
                        Box(modifier = Modifier.weight(1f)) {
                            PodcastCard(
                                podcast = podcast,
                                onClick = { onOpenPodcast(podcast.podcastid) }
                            )
                        }
                    }
                    if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
