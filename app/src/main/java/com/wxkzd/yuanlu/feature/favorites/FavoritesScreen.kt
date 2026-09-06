package com.wxkzd.yuanlu.feature.favorites

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.FavoriteEpisode
import com.wxkzd.yuanlu.domain.model.FavoriteSeries
import com.wxkzd.yuanlu.ui.components.BookmarkBorderIcon
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.HeadsetIcon
import com.wxkzd.yuanlu.ui.components.LayersIcon
import com.wxkzd.yuanlu.ui.components.LoadingBox
import com.wxkzd.yuanlu.ui.components.ScheduleIcon
import java.util.Locale

/**
 * 「我的收藏」（复刻 Web /library/favorites 移动端）：
 * 标题区 + 「播客系列/单集」双 Tab 切换卡 + 搜索框；
 * 播客卡片 = 封面/标签/名称/平台 + 单集数·收听数·收藏数统计行 + 右侧取消收藏；
 * 单集卡片 = 16:9 封面（播放遮罩）/单集名/所属播客/平台 + 时长·收听数·收藏数 + 取消收藏。
 */
@Composable
fun FavoritesRoute(
    onBack: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onGoDiscover: () -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---- 顶部导航 ----
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
                text = "我的收藏",
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
            else -> FavoritesContent(
                state = state,
                onSelectTab = viewModel::selectTab,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onOpenPodcast = onOpenPodcast,
                onOpenEpisode = onOpenEpisode,
                onRemovePodcast = viewModel::removePodcast,
                onRemoveEpisode = viewModel::removeEpisode,
                onGoDiscover = onGoDiscover
            )
        }
    }
}

@Composable
private fun FavoritesContent(
    state: FavoritesUiState,
    onSelectTab: (FavoritesTab) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onRemovePodcast: (String) -> Unit,
    onRemoveEpisode: (String) -> Unit,
    onGoDiscover: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- 双 Tab 切换卡（带数量角标，对齐 Web segmented 卡） ----
        item(key = "tabs") {
            FavoritesTabSwitcher(
                podcastCount = state.podcasts.size,
                episodeCount = state.episodes.size,
                activeTab = state.activeTab,
                onSelect = onSelectTab
            )
        }

        // ---- 搜索框 ----
        item(key = "search") {
            SearchField(
                query = state.searchQuery,
                placeholder = if (state.activeTab == FavoritesTab.PODCASTS) "搜索收藏的播客..." else "搜索收藏的单集...",
                onQueryChange = onSearchQueryChange
            )
        }

        if (state.activeTab == FavoritesTab.PODCASTS) {
            val podcasts = state.filteredPodcasts
            if (podcasts.isEmpty()) {
                item(key = "empty_podcasts") { FavoritesEmptyState(isPodcasts = true, onGoDiscover = onGoDiscover) }
            } else {
                items(podcasts, key = { "podcast_${it.id}" }) { series ->
                    FavoritePodcastCard(
                        series = series,
                        onClick = { onOpenPodcast(series.id) },
                        onRemove = { onRemovePodcast(series.id) }
                    )
                }
            }
        } else {
            val episodes = state.filteredEpisodes
            if (episodes.isEmpty()) {
                item(key = "empty_episodes") { FavoritesEmptyState(isPodcasts = false, onGoDiscover = onGoDiscover) }
            } else {
                items(episodes, key = { "episode_${it.id}" }) { episode ->
                    FavoriteEpisodeCard(
                        episode = episode,
                        onClick = { onOpenEpisode(episode.id) },
                        onRemove = { onRemoveEpisode(episode.id) }
                    )
                }
            }
        }

        item(key = "footer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

/** 双 Tab 切换卡：播客系列 (n) / 单集 (n) */
@Composable
private fun FavoritesTabSwitcher(
    podcastCount: Int,
    episodeCount: Int,
    activeTab: FavoritesTab,
    onSelect: (FavoritesTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        TabSwitchButton(
            label = "播客系列 ($podcastCount)",
            selected = activeTab == FavoritesTab.PODCASTS,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(FavoritesTab.PODCASTS) }
        )
        TabSwitchButton(
            label = "单集 ($episodeCount)",
            selected = activeTab == FavoritesTab.EPISODES,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(FavoritesTab.EPISODES) }
        )
    }
}

@Composable
private fun TabSwitchButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    placeholder: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                inner()
            }
        )
    }
}

/**
 * 播客系列卡片（对齐 Web 移动端横向布局）：
 * 封面(96dp) + 首个标签 + 名称 + 平台；底部统计行（单集数/收听数/收藏数）靠左，
 * 取消收藏圆形按钮并入该行绝对靠右（文本区吃满整卡宽度）。
 */
@Composable
private fun FavoritePodcastCard(
    series: FavoriteSeries,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
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
        CoverImage(
            url = series.thumbnailUrl,
            contentDescription = series.title,
            modifier = Modifier.size(96.dp),
            cornerRadius = 12.dp
        )
        Column(modifier = Modifier.weight(1f)) {
            // 移动端仅展示首个分类标签（对齐 Web xl:hidden 分支）
            series.category.firstOrNull()?.let { tag ->
                Text(
                    text = tag.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = series.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = series.author.ifBlank { "Unknown Author" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 底部统计与操作栏：统计（单集数 Layers / 收听数 Headphones / 收藏数 Bookmark）靠左，
            // 取消收藏按钮并入本行并经 Spacer(weight(1f)) 绝对靠右——文本区因此吃满整卡宽度
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatItem(LayersIcon, series.episodeCount.toString())
                    StatItem(HeadsetIcon, formatPlaysShort(series.plays))
                    StatItem(BookmarkBorderIcon, series.followers.toString())
                }
                Spacer(modifier = Modifier.weight(1f))
                // 取消收藏（与剧集卡片一致的垃圾桶按钮：error/10 底 + error 图标）
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "取消收藏",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

/**
 * 单集卡片（左右布局，外层与播客卡片同构；内部文本基准对齐播客详情页剧集列表行）：
 * 左侧 16:9 封面，时长胶囊挂封面右下角（半透明黑底叠加层）；
 * 右侧副标题（所属播客·平台，labelSmall）→ 标题（bodyMedium SemiBold）→
 * 底部操作栏左中右分布：收听数靠左 / 收藏数居中 / 取消收藏按钮靠右。
 */
@Composable
private fun FavoriteEpisodeCard(
    episode: FavoriteEpisode,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
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
        // 左侧 16:9 封面：宽度定 140dp，aspectRatio 由宽推高（79dp），严格锁定 16:9；
        // 时长/收听数以半透明黑底叠加层置于封面左下角（对齐详情页时长角标的 0x99000000 口径）
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
        ) {
            CoverImage(
                url = episode.thumbnailUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp
            )
            // 时长：封面右下角（半透明黑底胶囊，对齐详情页时长角标的 0x99000000 口径）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(
                    imageVector = ScheduleIcon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = episode.duration.ifBlank { "--" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }
        // 右侧文本信息：weight(1f) 吃满封面与边缘之间的剩余宽度
        Column(modifier = Modifier.weight(1f)) {
            // 副标题：所属播客 · 平台（对齐详情页系列名行：labelSmall + onSurfaceVariant）
            Text(
                text = listOf(episode.author, episode.platform.takeIf { it.isNotBlank() })
                    .filterNotNull()
                    .joinToString(" · ")
                    .ifBlank { "Unknown Series" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 标题（对齐详情页剧集行：bodyMedium + SemiBold）
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            // 底部操作栏（左中右分布，双 Spacer(weight(1f)) 保证三元素均衡对齐）：
            // 左：收听数（对齐详情页底部行口径） / 中：收藏数 / 右：取消收藏按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = HeadsetIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = String.format(Locale.US, "%,d", episode.playCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = BookmarkBorderIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = episode.favoriteCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                        .clickable { onRemove() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "取消收藏",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** 空态（对齐 Web EmptyState：虚线框 + 圆形图标 + 引导文案 + 去发现按钮） */
@Composable
private fun FavoritesEmptyState(isPodcasts: Boolean, onGoDiscover: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .border(
                2.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(24.dp)
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = BookmarkBorderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有收藏任何${if (isPodcasts) "播客" else "单集"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "开始探索并点击收藏图标，将你喜欢的${if (isPodcasts) "播客系列" else "精彩单集"}保存到这里以便稍后学习。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onGoDiscover,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "去发现",
                style = MaterialTheme.typography.labelLarge,
                fontSize = 14.sp
            )
        }
    }
}

/** 收听数缩写（对齐 Web 移动端：>999 转 "x.xk"） */
private fun formatPlaysShort(count: Int): String =
    if (count > 999) String.format(Locale.US, "%.1fk", count / 1000.0) else count.toString()
