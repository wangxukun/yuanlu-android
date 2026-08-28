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
import androidx.compose.foundation.layout.statusBarsPadding
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

import androidx.compose.material.icons.filled.Computer
import androidx.compose.ui.text.style.TextAlign

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
    onOpenChannel: (String) -> Unit,
    onViewAllChannels: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        when {
            state.isLoading -> DiscoverSkeleton()
            state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::load)
            else -> BrowseContent(
                state = state,
                onSelectTag = viewModel::selectTag,
                onOpenPodcast = onOpenPodcast,
                onOpenChannel = onOpenChannel,
                onViewAllChannels = onViewAllChannels
            )
        }
    }
}
// ---------- 浏览态 ----------

@Composable
private fun BrowseContent(
    state: DiscoverUiState,
    onSelectTag: (Tag?) -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onViewAllChannels: () -> Unit
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
                SectionHeader("热门榜", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            state.trending.take(6).chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "trending_row_$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { podcast ->
                            Box(modifier = Modifier.weight(1f)) {
                                RankedPodcastCard(
                                    podcast = podcast,
                                    rank = state.trending.indexOf(podcast),
                                    onClick = { onOpenPodcast(podcast.podcastid) }
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- 为您推荐（编辑精选） ----
        if (state.editorPicks.isNotEmpty()) {
            item(key = "picks_header") {
                SectionHeader("为您推荐", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            state.editorPicks.take(6).chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "picks_row_$rowIndex") {
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
                                    pill = podcast.tags.firstOrNull()?.name
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- 新节目 ----
        if (state.newPodcasts.isNotEmpty()) {
            item(key = "new_header") {
                SectionHeader("新节目", modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp))
            }
            state.newPodcasts.take(6).chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "new_row_$rowIndex") {
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
                                    badge = "新"
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- 推荐频道 ----
        if (state.channels.isNotEmpty()) {
            item(key = "channels_header") {
                SectionHeader(
                    title = "推荐频道",
                    actionLabel = "查看更多",
                    onAction = onViewAllChannels,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
                )
            }
            state.channels.take(6).chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "channels_row_$rowIndex") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowItems.forEach { channel ->
                            Box(modifier = Modifier.weight(1f)) {
                                ChannelCard(
                                    channel = channel,
                                    onClick = { onOpenChannel(channel.name) }
                                )
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
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
                        label = { Text("全部") }
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
                if (state.selectedTag != null) state.selectedTag!!.name else "全部播客",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp)
            )
        }
        if (filtered.isEmpty()) {
            item(key = "empty") {
                EmptyBox(
                    if (state.podcasts.isEmpty()) "暂无播客"
                    else "该分类暂无播客"
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
fun ChannelCard(
    channel: ChannelEntry,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${channel.podcastCount} 档节目",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(50),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Computer,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "频道主页",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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
