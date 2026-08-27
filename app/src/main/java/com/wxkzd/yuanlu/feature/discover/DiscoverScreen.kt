package com.wxkzd.yuanlu.feature.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.domain.model.Tag
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.EyebrowText
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader
import com.wxkzd.yuanlu.ui.components.ShimmerBox
import com.wxkzd.yuanlu.ui.components.formatPlays

/** 热门榜排名徽章配色，对齐 Web 端 01 金 / 02 银 / 03 铜 / 其余墨；金色压深保证白底封面上的辨识度 */
private val RankBadgeColors = listOf(
    0xFFDAA520L, // gold(goldenrod)
    0xFFB8BCC2L, // silver
    0xFFCD7F32L  // bronze
)
private const val RankBadgeIron = 0xFF44403CL
private const val RankBadgeFallback = 0xFF666666

/** 徽章描边：任何封面颜色下都能保持边缘清晰 */
private val RankBadgeBorder = Color(0x33000000)

@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 搜索态下系统返回键先清空搜索返回浏览态，而不是直接退出应用
    BackHandler(enabled = state.query.isNotEmpty()) {
        viewModel.onQueryChange("")
    }

    Column(modifier = Modifier.fillMaxSize()) {
        DiscoverHeader(
            query = state.query,
            onQueryChange = viewModel::onQueryChange
        )

        when {
            state.isLoading -> DiscoverSkeleton()
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
private fun DiscoverHeader(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Discover",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
        )
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.padding(vertical = 12.dp)) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Search podcasts",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// ---------- 搜索态 ----------

@Composable
private fun SearchResults(
    results: List<Podcast>,
    isSearching: Boolean,
    query: String,
    onOpenPodcast: (String) -> Unit
) {
    when {
        isSearching -> DiscoverSkeleton()
        results.isEmpty() -> EmptyBox("No podcasts found for \"$query\"")
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            results.chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "row_$rowIndex") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { podcast ->
                            Box(modifier = Modifier.weight(1f)) {
                                PodcastCard(
                                    podcast = podcast,
                                    onClick = { onOpenPodcast(podcast.podcastid) },
                                    showPlays = true,
                                    showDescription = true
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

// ---------- 浏览态 ----------

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
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- 热门节目 ----
        if (state.trending.isNotEmpty()) {
            item(key = "trending_header") {
                SectionHeader("Trending", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            item(key = "trending_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.trending, key = { it.podcastid }) { podcast ->
                        Box(modifier = Modifier.width(150.dp)) {
                            RankedPodcastCard(
                                podcast = podcast,
                                rank = state.trending.indexOf(podcast),
                                onClick = { onOpenPodcast(podcast.podcastid) }
                            )
                        }
                    }
                }
            }
        }

        // ---- 为您推荐（编辑精选） ----
        if (state.editorPicks.isNotEmpty()) {
            item(key = "picks_header") {
                SectionHeader("Editor's Picks", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            item(key = "picks_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.editorPicks, key = { it.podcastid }) { podcast ->
                        Box(modifier = Modifier.width(150.dp)) {
                            PodcastCard(
                                podcast = podcast,
                                onClick = { onOpenPodcast(podcast.podcastid) },
                                pill = podcast.tags.firstOrNull()?.name
                            )
                        }
                    }
                }
            }
        }

        // ---- 新节目 ----
        if (state.newPodcasts.isNotEmpty()) {
            item(key = "new_header") {
                SectionHeader("New Shows", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            item(key = "new_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.newPodcasts, key = { it.podcastid }) { podcast ->
                        Box(modifier = Modifier.width(150.dp)) {
                            PodcastCard(
                                podcast = podcast,
                                onClick = { onOpenPodcast(podcast.podcastid) },
                                badge = "NEW"
                            )
                        }
                    }
                }
            }
        }

        // ---- 推荐频道 ----
        if (state.channels.isNotEmpty()) {
            item(key = "channels_header") {
                SectionHeader("Channels", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            item(key = "channels_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.channels, key = { it.name }) { channel ->
                        ChannelCard(
                            channel = channel,
                            onClick = { onOpenChannel(channel.name) }
                        )
                    }
                }
            }
        }

        // ---- 分类标签 ----
        if (state.tags.isNotEmpty()) {
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
        }

        // ---- 全部播客网格 ----
        item(key = "grid_header") {
            SectionHeader(
                if (state.selectedTag != null) state.selectedTag!!.name else "All Podcasts",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyBox(
                    if (state.podcasts.isEmpty()) "No podcasts yet"
                    else "No podcasts in this category"
                )
            }
        } else {
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
                                    onClick = { onOpenPodcast(podcast.podcastid) },
                                    showPlays = true
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

// ---------- 私有组件 ----------

/** 热门榜卡片：封面左上角圆形排名徽章 + 平台眉标 + 标题 + 收听量 */
@Composable
private fun RankedPodcastCard(
    podcast: Podcast,
    rank: Int,
    onClick: () -> Unit
) {
    val badgeColor = when {
        rank < RankBadgeColors.size -> RankBadgeColors[rank]
        rank < 4 -> RankBadgeIron
        else -> RankBadgeFallback
    }
    val badgeTextColor = if (rank == 0 || rank == 1) {
        androidx.compose.ui.graphics.Color(0xFF151310) // 金/银底用深色文字
    } else {
        androidx.compose.ui.graphics.Color.White
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box {
            CoverImage(
                url = podcast.coverUrl,
                contentDescription = podcast.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
            Text(
                text = "%02d".format(rank + 1),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = badgeTextColor,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(color = Color(badgeColor), shape = CircleShape)
                    .border(width = 1.dp, color = RankBadgeBorder, shape = CircleShape)
                    .size(26.dp)
                    .wrapContentSize(Alignment.Center)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        podcast.platform?.let { EyebrowText(it) }
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        formatPlays(podcast.totalPlays)?.let { plays ->
            Text(
                text = plays,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 频道入口卡：品牌色底、无封面（对齐 Web 端推荐频道卡片） */
@Composable
private fun ChannelCard(
    channel: ChannelEntry,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .width(160.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(5.dp)
                )
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2
            )
            Text(
                text = "${channel.podcastCount} shows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

/** 发现页骨架屏：两行卡片占位 + 扫光 */
@Composable
private fun DiscoverSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            cornerRadius = 28.dp
        )
        repeat(2) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(2) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
                            cornerRadius = 16.dp
                        )
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                    }
                }
            }
        }
    }
}
