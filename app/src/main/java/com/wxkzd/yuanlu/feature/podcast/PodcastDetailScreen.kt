package com.wxkzd.yuanlu.feature.podcast

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Podcast
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.HeadsetIcon
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.PodcastCard
import com.wxkzd.yuanlu.ui.components.SectionHeader
import com.wxkzd.yuanlu.ui.components.formatPublishDate
import java.util.Locale

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
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    when {
        state.isLoading -> LoadingBox()
        state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::retry)
        state.podcast == null -> EmptyBox("未找到该播客")
        else -> PodcastDetailContent(
            state = state,
            onBack = onBack,
            onOpenPodcast = onOpenPodcast,
            onOpenChannel = onOpenChannel,
            onOpenEpisode = onOpenEpisode,
            onLoadMore = viewModel::loadMore,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = Modifier.statusBarsPadding()
        )
    }
}

/**
 * 播客详情布局（对齐参考截图）：导航栏与播客卡片固定在顶部、不随列表滚动；
 * 剧集列表独占滚动区，简介在顶部卡片内联展开/收起（非模态）。
 */
@Composable
private fun PodcastDetailContent(
    state: PodcastDetailUiState,
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onLoadMore: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val podcast = state.podcast!!
    val listState = rememberLazyListState()
    val context = LocalContext.current

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

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 顶部导航栏（固定）----
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
                text = "播客详情",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {}) {
                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
            }
        }

        // ---- 播客卡片（固定，不随列表滚动）----
        PodcastHeaderCard(
            podcast = podcast,
            episodeCount = podcast.episodeCount.takeIf { it > 0 } ?: state.total,
            isFavorited = state.isFavorited,
            isFavoriteBusy = state.isFavoriteBusy,
            onFavorite = onToggleFavorite,
            onShare = {
                val text = buildString {
                    append("播客「${podcast.title}」")
                    podcast.description?.take(60)?.takeIf { it.isNotBlank() }?.let { append("：$it…") }
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(sendIntent, "分享播客"))
            }
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // ---- 剧集列表（滚动区）----
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)
        ) {
            if (state.episodes.isEmpty() && !state.isLoadingMore) {
                item(key = "empty_episodes") { EmptyBox("暂无单集") }
            }
            itemsIndexed(state.episodes, key = { _, episode -> episode.episodeid }) { _, episode ->
                CollectionEpisodeRow(
                    episode = episode,
                    seriesName = episode.podcastTitle ?: podcast.title,
                    // 单集封面解码失败（如 AVIF）时降级到专辑封面（对齐 Web episode || podcast || default）
                    fallbackCoverUrl = podcast.coverUrl,
                    onClick = { onOpenEpisode(episode.episodeid) }
                )
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
                            text = "已经到底了",
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
                        "${podcast.platform ?: "该频道"}的更多节目",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        actionLabel = podcast.platform?.let { "查看频道" },
                        onAction = podcast.platform?.let { platform -> { onOpenChannel(platform) } }
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
}

/**
 * 顶部固定播客卡片：大封面（集数角标）+ 标题/作者/两行简介（点击展开为完整简介，
 * 在卡片内联展开于顶部、非模态，右下角切换"展开/收起"）+ 收藏/分享 + 编辑日期。
 * 注意：文字列只随内容自适应高度，不能使用 fillMaxHeight/weight 占位，
 * 否则会把固定区撑满整屏、把下方剧集列表挤成 0 高度。
 */
@Composable
private fun PodcastHeaderCard(
    podcast: Podcast,
    episodeCount: Int,
    isFavorited: Boolean,
    isFavoriteBusy: Boolean,
    onFavorite: () -> Unit,
    onShare: () -> Unit
) {
    var introExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(2f)
                .aspectRatio(1f)
        ) {
            CoverImage(
                url = podcast.coverUrl,
                contentDescription = podcast.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 10.dp
            )
            if (episodeCount > 0) {
                Text(
                    text = "$episodeCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(3f)) {
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            podcast.platform?.let { platform ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = platform,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (!podcast.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (introExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                )
                // 展开/收起：内联于顶部卡片，右下角
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = if (introExpanded) "收起" else "展开",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { introExpanded = !introExpanded }
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HeaderAction(
                    icon = if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    label = if (isFavorited) "已收藏" else "收藏",
                    tint = if (isFavorited) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !isFavoriteBusy,
                    onClick = onFavorite
                )
                HeaderAction(
                    icon = Icons.Filled.Share,
                    label = "分享",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    onClick = onShare
                )
            }
            podcast.createAt?.takeIf { it.isNotBlank() }?.let { createAt ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatPublishDate(createAt)}编辑",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 卡片操作项：图标 + 文字的小按钮（收藏/分享）；enabled=false 时置灰防连点 */
@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 2.dp)
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

/**
 * 剧集行（对齐截图合集列表与 Web 端 EpisodeCard）：
 * 16:9 缩略图（时长角标/进度条）+ 系列名/标题，
 * 底部播放数（耳机图标+数字）居左、发布日期（日历图标）同行居右。
 */
@Composable
private fun CollectionEpisodeRow(
    episode: Episode,
    seriesName: String,
    fallbackCoverUrl: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.width(140.dp).aspectRatio(16f / 9f)) {
            CoverImage(
                url = episode.coverUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp,
                fallbackUrl = fallbackCoverUrl
            )
            // 难度角标：左上角，与右下角时长角标形成对角（白底彩字，对齐 Web 端 DifficultyBadge）
            episode.difficulty?.takeIf { it.isNotBlank() }?.let { level ->
                Text(
                    text = level,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = difficultyColor(level),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color(0xF2FFFFFF), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                )
            }
            if (episode.duration > 0) {
                Text(
                    text = formatBadgeDuration(episode.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
            // 收听中的剧集在缩略图底部显示细进度条
            if (episode.progressSeconds > 0 && episode.duration > 0 && !episode.isFinished) {
                LinearProgressIndicator(
                    progress = {
                        (episode.progressSeconds.toFloat() / episode.duration).coerceIn(0f, 1f)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp),
                    trackColor = Color.White.copy(alpha = 0.4f)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = seriesName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 播放数（耳机图标+数字）居左，发布日期（日历图标+日期）同行居右
            val showPlayCount = episode.playCount > 0
            val publishDate = episode.publishAt?.takeIf { it.isNotBlank() }?.let { formatPublishDate(it) }
            if (showPlayCount || publishDate != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (showPlayCount) {
                        Icon(
                            imageVector = HeadsetIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = formatPlayCount(episode.playCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (publishDate != null) {
                        Icon(
                            imageVector = Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = publishDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 时长角标：612s -> "10:12" */
private fun formatBadgeDuration(totalSeconds: Int): String =
    "%02d:%02d".format(Locale.US, totalSeconds / 60, totalSeconds % 60)

/** 难度角标配色，对齐 Web 端 lib/difficulty.ts：A 远青 / B1 黛蓝 / B2 曙光橙 / C 陶土红，其余墨色 */
private fun difficultyColor(level: String): Color = when {
    level.contains("A") -> Color(0xFF1F7A5C)
    level.contains("B1") -> Color(0xFF4A7FA5)
    level.contains("B2") -> Color(0xFFB96F0F)
    level.contains("C") -> Color(0xFFD2503F)
    else -> Color(0xFF44403C)
}

/** 播放量：10500 -> "1.1万"，不足一万显示原值 */
private fun formatPlayCount(count: Int): String = when {
    count >= 10000 -> {
        val wan = count / 10000.0
        if (wan >= 100 || wan == wan.toInt().toDouble()) "${wan.toInt()}万"
        else String.format(Locale.US, "%.1f万", wan)
    }
    else -> count.toString()
}
