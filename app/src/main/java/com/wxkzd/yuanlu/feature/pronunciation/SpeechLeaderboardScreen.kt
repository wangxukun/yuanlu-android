package com.wxkzd.yuanlu.feature.pronunciation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wxkzd.yuanlu.domain.model.LeaderboardEntry
import com.wxkzd.yuanlu.domain.model.LeaderboardMetric
import com.wxkzd.yuanlu.domain.model.LeaderboardPeriod
import com.wxkzd.yuanlu.domain.model.MyLeaderboardRank
import com.wxkzd.yuanlu.ui.components.ErrorBox

/** 前三名徽章配色（Web Crown/Medal：金 #EAB308 / 银 #9CA3AF / 铜 #D97706） */
private val RankGold = Color(0xFFEAB308)
private val RankSilver = Color(0xFF9CA3AF)
private val RankBronze = Color(0xFFD97706)

/**
 * 发音达人榜（复刻 Web /library/pronunciation/leaderboard，参照设计稿图3）：
 * 顶栏（返回 + 奖杯标题 + 榜单规则说明）→ 双重 Tab（周期：近7天/今日；维度：平均分榜/勤奋榜）→
 * 榜单列表（前三金银铜徽章 + 头像 + 昵称 + 得分/次数）→ 吸底"我的排名"卡片。
 */
@Composable
fun SpeechLeaderboardRoute(
    onBack: () -> Unit,
    viewModel: SpeechLeaderboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val board = state.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---- 顶部导航栏：返回 + 奖杯标题 + 规则说明 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "发音达人榜",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "榜单按${state.metric.ruleText}排名",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 38.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        // ---- 双重 Tab 过滤：周期（主色）+ 维度（曙光橙） ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegmentedPill(
                options = LeaderboardPeriod.entries.map { it.label },
                selected = state.period.label,
                selectedContainer = MaterialTheme.colorScheme.primary,
                selectedContent = MaterialTheme.colorScheme.onPrimary,
                onSelect = { label ->
                    LeaderboardPeriod.entries.firstOrNull { it.label == label }?.let(viewModel::selectPeriod)
                },
                modifier = Modifier.weight(1f)
            )
            SegmentedPill(
                options = LeaderboardMetric.entries.map { it.label },
                selected = state.metric.label,
                selectedContainer = MaterialTheme.colorScheme.secondary,
                selectedContent = MaterialTheme.colorScheme.onSecondary,
                onSelect = { label ->
                    LeaderboardMetric.entries.firstOrNull { it.label == label }?.let(viewModel::selectMetric)
                },
                modifier = Modifier.weight(1f)
            )
        }

        // ---- 榜单主体 ----
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading && board == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError != null && board == null -> ErrorBox(
                    message = state.loadError!!,
                    onRetry = viewModel::retry
                )
                board == null || board.entries.isEmpty() -> EmptyBoard()
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // "我的排名"吸底卡存在时预留滚动底部空间，末行不被遮挡
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = if (board.me != null) 108.dp else 16.dp
                    )
                ) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                board.entries.forEachIndexed { index, entry ->
                                    LeaderboardRow(
                                        entry = entry,
                                        rank = index + 1,
                                        metric = state.metric
                                    )
                                    if (index != board.entries.lastIndex) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ---- 吸底"我的排名"卡片 ----
            board?.me?.let { me ->
                MyRankCard(
                    me = me,
                    metric = state.metric,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}

/** 联排切换 pill（Web join 组件：选中实底 + 未选描边浅底） */
@Composable
private fun SegmentedPill(
    options: List<String>,
    selected: String,
    selectedContainer: Color,
    selectedContent: Color,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Text(
                text = option,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) selectedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (isSelected) selectedContainer else Color.Transparent,
                        RoundedCornerShape(9.dp)
                    )
                    .clickableNoRipple { onSelect(option) }
                    .padding(vertical = 6.dp)
            )
        }
    }
}

/** 单行榜单：排名徽章（前三特殊图标）+ 头像 + 昵称 + 得分/次数 */
@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    rank: Int,
    metric: LeaderboardMetric
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // 排名位：#1 皇冠金 / #2 银牌 / #3 铜牌 / 其余数字
        Box(modifier = Modifier.width(30.dp), contentAlignment = Alignment.Center) {
            when (rank) {
                1 -> Icon(
                    Icons.Filled.EmojiEvents,
                    contentDescription = "第1名",
                    tint = RankGold,
                    modifier = Modifier.size(22.dp)
                )
                2 -> Icon(
                    Icons.Filled.MilitaryTech,
                    contentDescription = "第2名",
                    tint = RankSilver,
                    modifier = Modifier.size(22.dp)
                )
                3 -> Icon(
                    Icons.Filled.MilitaryTech,
                    contentDescription = "第3名",
                    tint = RankBronze,
                    modifier = Modifier.size(22.dp)
                )
                else -> Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        // 头像（签名 URL / 默认头像，加载失败回退首字母）
        Avatar(entry)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = entry.nickname,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // 右侧：主指标大字 + 副指标小字
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (metric == LeaderboardMetric.SCORE) "${entry.avgScore} 分" else "${entry.evalCount} 次",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (metric == LeaderboardMetric.SCORE) "${entry.evalCount} 次评测" else "平均 ${entry.avgScore} 分",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** 头像：40dp 圆形，失败回退首字母占位（Web default-avatar 兜底） */
@Composable
private fun Avatar(entry: LeaderboardEntry) {
    var failed by remember(entry.userid) { mutableStateOf(false) }
    if (!failed && entry.avatar.isNotBlank()) {
        AsyncImage(
            model = entry.avatar,
            contentDescription = entry.nickname,
            onError = { failed = true },
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = entry.nickname.take(1).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** 吸底"我的排名"卡（Web primary/5 底 + primary/20 描边） */
@Composable
private fun MyRankCard(
    me: MyLeaderboardRank,
    metric: LeaderboardMetric,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "#${me.rank}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "我的排名",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${me.evalCount} 次评测 · 平均 ${me.avgScore} 分",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyBoard() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "本周期暂无上榜记录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "完成语音评测即可上榜",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

/** 无涟漪点击（pill 分段切换避免涟漪扩散溢出圆角） */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
