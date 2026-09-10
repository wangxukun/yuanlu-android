package com.wxkzd.yuanlu.feature.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.HeadsetIcon
import com.wxkzd.yuanlu.ui.components.ScheduleIcon
import com.wxkzd.yuanlu.ui.components.ShimmerBox
import com.wxkzd.yuanlu.ui.components.formatDuration
import java.util.Locale
import kotlin.math.roundToInt

// ---------- 曙光橙（Web tailwind accent 阶梯；仅首页局部使用，不入全局主题） ----------
private val Accent400 = Color(0xFFE59D2E) // 今日虚线圈
private val Accent500 = Color(0xFFD98A17) // 里程碑小旗 / 词汇进度条
private val Accent700 = Color(0xFF96580D) // 浅色模式强调文字
private val Accent100 = Color(0xFFFAE5C6) // 打卡胶囊底色（浅色）
private val Accent900Scrim = Color(0x664E2E0B) // 打卡胶囊底色（深色 accent-900/40）
private val Accent300 = Color(0xFFECB35E) // 打卡胶囊文字（深色）

/** 难度 → 文字色（对齐 Web lib/difficulty.ts，徽章恒为白底故不随主题切换） */
private fun difficultyColor(level: String?): Color {
    if (level.isNullOrBlank()) return Color(0xFF4A4436)
    return when {
        level.contains("A") -> Color(0xFF1F7A5C)
        level.contains("B1") -> Color(0xFF3C6989)
        level.contains("B2") -> Color(0xFFB96F0F)
        level.contains("C") -> Color(0xFFD2503F)
        else -> Color(0xFF4A4436)
    }
}

@Composable
private fun isDarkTheme(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenEpisode: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLearningPaths: () -> Unit,
    onGoDiscover: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 重进首页（切 Tab/返回）静默刷新：收听时长由后台心跳累计到服务端，
    // 需重拉才能让「今日打卡还差 X 分钟」随最新时长减少；冷启动骨架屏阶段跳过
    LaunchedEffect(Unit) {
        if (!state.isLoading) viewModel.refresh(silent = true)
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { viewModel.refresh(silent = true) },
        modifier = Modifier.fillMaxSize()
    ) {
        when {
            state.isLoading -> HomeSkeleton()
            state.error != null -> ErrorBox(
                message = state.error!!,
                onRetry = { viewModel.refresh() }
            )
            else -> HomeContent(
                state = state,
                onOpenEpisode = onOpenEpisode,
                onOpenHistory = onOpenHistory,
                onOpenLearningPaths = onOpenLearningPaths,
                onGoDiscover = onGoDiscover
            )
        }
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onOpenEpisode: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLearningPaths: () -> Unit,
    onGoDiscover: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // ---- 一、头部状态区：问候 + 签名 + 连续打卡 ----
        HomeHeader(
            greeting = state.greeting,
            bio = state.bio,
            checkInStatus = state.checkInStatus,
            streakDays = state.streakDays
        )

        HomeSectionSpacer()

        // ---- 二、最近一次收听（续播卡） ----
        ResumeCard(
            latest = state.latestHistory,
            onPlay = onOpenEpisode,
            onGoDiscover = onGoDiscover
        )

        HomeSectionSpacer()

        // ---- 二、本周里程 ----
        WeeklyMileageCard(mileage = state.mileage)

        HomeSectionSpacer()

        // ---- 二、我的路（一周学习小径） ----
        MyRoadCard(days = state.journeyDays, onOpenLearningPaths = onOpenLearningPaths)

        // ---- 三、剧集列表区 ----
        if (state.continueListening.isNotEmpty()) {
            HomeSectionSpacer()
            ContinueListeningSection(
                items = state.continueListening,
                onOpenHistory = onOpenHistory,
                onPlay = onOpenEpisode
            )
        }

        if (state.recommended.isNotEmpty()) {
            HomeSectionSpacer()
            RecommendedSection(
                episodes = state.recommended,
                onPlay = onOpenEpisode
            )
        }

        if (state.latestEpisodes.isNotEmpty()) {
            HomeSectionSpacer()
            LatestSection(
                episodes = state.latestEpisodes,
                onPlay = onOpenEpisode
            )
        }

        // 底部留白：给悬浮迷你播放条让位
        Spacer(modifier = Modifier.height(96.dp))
    }
}

@Composable
private fun HomeSectionSpacer(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.height(24.dp))
}

// ================================================================
// 一、头部状态区
// ================================================================

/**
 * 头部状态区（对齐 Web 首页 Header）：左右 Flex 两端分布 + 垂直居中。
 * 左侧问候语（大字加粗）+ 个性签名（小字浅色）；右侧连胜徽章（双行胶囊）。
 * 左列 weight(1f) + 文本省略/换行兜底，长昵称小屏下不会挤压右侧徽章。
 */
@Composable
private fun HomeHeader(
    greeting: String,
    bio: String,
    checkInStatus: String,
    streakDays: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = greeting,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = bio,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        StreakBadge(status = checkInStatus, days = streakDays)
    }
}

/**
 * 连胜徽章（对齐 Web 首页打卡胶囊）：accent-100 暖橘底胶囊，
 * 内部上下两行居中——上行今日打卡进度，下行 🔥 连续天数，accent-700 强调色。
 */
@Composable
private fun StreakBadge(status: String, days: Int, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) Accent900Scrim else Accent100)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (dark) Accent300 else Accent700,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "🔥 连续 $days 天",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (dark) Accent300 else Accent700,
            maxLines = 1
        )
    }
}

// ================================================================
// 二、核心卡片
// ================================================================

/** 最近一次收听：大封面 + 标题/播客名 + 进度条 + 继续播放按钮（对齐 Web 首卡移动端纵向布局） */
@Composable
private fun ResumeCard(
    latest: HistoryItem?,
    onPlay: (String) -> Unit,
    onGoDiscover: () -> Unit
) {
    HomeCard {
        if (latest == null) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "还没有收听记录，今天从一期新节目开始第一步吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onGoDiscover,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text(text = "去发现页看看", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                CoverImage(
                    url = latest.episode.thumbnailUrl,
                    contentDescription = latest.episode.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    cornerRadius = 8.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "继续收听",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = latest.episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = latest.episode.category.ifBlank { latest.episode.author },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))
                MilestoneProgressBar(
                    progress = latest.progressRatio,
                    height = 4.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resumeHint(latest),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ResumeButton(latest = latest, onPlay = onPlay)
                }
            }
        }
    }
}

/** 「已听完，值得再走一遍」/「还剩约 x 分钟」 */
private fun resumeHint(latest: HistoryItem): String {
    if (latest.isFinished) return "已听完，值得再走一遍"
    if (latest.episode.durationSeconds <= 0) return ""
    val remaining =
        ((latest.episode.durationSeconds - latest.progressSeconds) / 60f).coerceAtLeast(0f)
    return "还剩约 ${remaining.roundToInt()} 分钟"
}

/** 继续播放 / 再听一遍 主按钮（进度 mm:ss 随按钮展示，对齐 Web ResumeButton） */
@Composable
private fun ResumeButton(latest: HistoryItem, onPlay: (String) -> Unit) {
    val finished = latest.isFinished
    Button(
        onClick = { onPlay(latest.episode.id) },
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = if (finished) Icons.Filled.Refresh else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        val label = if (finished) {
            "再听一遍"
        } else {
            val minutes = latest.progressSeconds / 60
            val seconds = latest.progressSeconds % 60
            "继续 (%d:%02d)".format(Locale.US, minutes, seconds)
        }
        Text(text = label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** 本周里程卡：把收听时长翻译成行走距离（5km/h），含词汇路标进度（对齐 Web WeeklyMileageCard） */
@Composable
private fun WeeklyMileageCard(mileage: WeeklyMileage) {
    HomeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本周里程",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val positive = mileage.weeklyProgress >= 0
                Text(
                    text = (if (positive) "+" else "") + "${mileage.weeklyProgress}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (positive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (positive) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(Locale.US, mileage.kmCurrent),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "/ ${trimZero(mileage.kmGoal)} km",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            MilestoneProgressBar(
                progress = if (mileage.kmGoal > 0) {
                    (mileage.kmCurrent / mileage.kmGoal).toFloat().coerceIn(0f, 1f)
                } else 0f,
                height = 6.dp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (mileage.remainingMins <= 0) {
                    "本周目标已达成，走得漂亮！"
                } else {
                    "再走 ${mileage.remainingMins} 分钟达成周目标"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "词汇路标",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${mileage.wordsCurrent} / ${mileage.wordsGoal}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            MilestoneProgressBar(
                progress = if (mileage.wordsGoal > 0) {
                    (mileage.wordsCurrent.toFloat() / mileage.wordsGoal).coerceIn(0f, 1f)
                } else 0f,
                height = 6.dp,
                color = Accent500
            )
        }
    }
}

/** 5.0 -> "5"；2.5 -> "2.5"（目标公里数去掉多余的 .0） */
private fun trimZero(value: Double): String {
    return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}

/** 我的路卡：蜿蜒虚线小径 + 7 天节点（学习日点亮远青并插旗，今日虚线圈），对齐 Web JourneyStrip */
@Composable
private fun MyRoadCard(days: List<JourneyDay>, onOpenLearningPaths: () -> Unit) {
    HomeCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "我的路",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "查看学习路径 →",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenLearningPaths() }
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            JourneyStrip(days = days)
        }
    }
}

/**
 * 一周学习小径的自绘实现：
 * 坐标系与 Web Strip(W=360,H=92,pad=30) 同构，按实际宽度等比展开；
 * 小径为虚线平滑曲线，节点 r=6dp，学习日插曙光橙小旗，今日套虚线圈。
 */
@Composable
private fun JourneyStrip(days: List<JourneyDay>, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val pathColor = MaterialTheme.colorScheme.outline
    val studiedColor = MaterialTheme.colorScheme.primary
    val unstudiedStroke = MaterialTheme.colorScheme.outline
    val todayLabelColor = if (isDarkTheme()) Accent300 else Accent700
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelPx = with(density) { 12.sp.toPx() }

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(112.dp)
    ) {
        val pad = with(density) { 30.dp.toPx() }
        val r = with(density) { 6.dp.toPx() }
        val h = size.height
        val w = size.width
        val xs = days.indices.map { pad + it * (w - pad * 2) / (days.size - 1).coerceAtLeast(1) }
        // 轻微起伏的路径，营造“路”的感觉（对齐 Web ys 系数）
        val base = h * 0.50f
        val amp = h * 0.09f
        val ys = listOf(
            base + amp,
            base - amp,
            base + amp * 0.8f,
            base - amp,
            base + amp * 0.7f,
            base - amp * 0.9f,
            base + amp
        )

        // 小径：虚线平滑曲线（Q 段过中点）
        val path = Path().apply {
            moveTo(xs.first(), ys[0])
            for (i in 1 until days.size) {
                val midX = (xs[i - 1] + xs[i]) / 2
                val midY = (ys[i - 1] + ys[i]) / 2
                quadraticBezierTo(xs[i - 1], ys[i - 1], midX, midY)
            }
            lineTo(xs.last(), ys.last())
        }
        drawPath(
            path = path,
            color = pathColor,
            style = Stroke(
                width = with(density) { 2.5.dp.toPx() },
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(0.1f, with(density) { 9.dp.toPx() })
                )
            )
        )

        val flagPath = Path()
        days.forEachIndexed { index, day ->
            val x = xs[index]
            val y = ys[index]
            val studied = day.minutes > 0

            // 今天：虚线提醒圈
            if (day.isToday) {
                drawCircle(
                    color = Accent400,
                    radius = r + with(density) { 6.dp.toPx() },
                    center = Offset(x, y),
                    style = Stroke(
                        width = with(density) { 1.5.dp.toPx() },
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(
                                with(density) { 3.dp.toPx() },
                                with(density) { 3.dp.toPx() }
                            )
                        )
                    )
                )
            }

            // 节点：学习日实心远青，未学习白底描边
            if (studied) {
                drawCircle(color = studiedColor, radius = r, center = Offset(x, y))
            } else {
                drawCircle(
                    color = unstudiedStroke,
                    radius = r,
                    center = Offset(x, y),
                    style = Stroke(width = with(density) { 1.5.dp.toPx() })
                )
            }

            // 里程碑小旗：M0,-r L0,-r-13 L11,-r-9.5 L0,-r-6 Z
            if (studied) {
                flagPath.reset()
                flagPath.moveTo(x, y - r)
                flagPath.lineTo(x, y - r - with(density) { 13.dp.toPx() })
                flagPath.lineTo(x + with(density) { 11.dp.toPx() }, y - r - with(density) { 9.5.dp.toPx() })
                flagPath.lineTo(x, y - r - with(density) { 6.dp.toPx() })
                flagPath.close()
                drawPath(flagPath, color = Accent500)
            }

            // 日标签：今天高亮加粗
            drawIntoCanvasText(
                text = if (day.isToday) "今天" else day.label,
                x = x,
                y = y + r + labelPx + with(density) { 4.dp.toPx() },
                color = if (day.isToday) todayLabelColor else labelColor,
                bold = day.isToday,
                labelPx = labelPx
            )
        }
    }
}

/** Canvas 内的文本绘制封装（Compose 无 drawText，借 nativeCanvas） */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntoCanvasText(
    text: String,
    x: Float,
    y: Float,
    color: Color,
    bold: Boolean,
    labelPx: Float
) {
    drawContext.canvas.nativeCanvas.drawText(
        text,
        x,
        y,
        android.graphics.Paint().apply {
            isAntiAlias = true
            textSize = labelPx
            textAlign = android.graphics.Paint.Align.CENTER
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
            isFakeBoldText = bold
        }
    )
}

// ================================================================
// 三、剧集列表区
// ================================================================

@Composable
private fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAction?.invoke() }
            )
        }
    }
}

/** 继续收听（水平滚动）：历史第 2 条起，封面 + 标题 + 进度条（对齐 Web ContinueListening） */
@Composable
private fun ContinueListeningSection(
    items: List<HistoryItem>,
    onOpenHistory: () -> Unit,
    onPlay: (String) -> Unit
) {
    Column {
        SectionTitle(title = "继续收听", actionLabel = "查看历史", onAction = onOpenHistory)
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.historyid }) { item ->
                ContinueCard(item = item, onClick = { onPlay(item.episode.id) })
            }
        }
    }
}

@Composable
private fun ContinueCard(item: HistoryItem, onClick: () -> Unit) {
    HomeCard(
        modifier = Modifier
            .width(300.dp)
            .clickable { onClick() },
        cornerRadius = 12.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(
                url = item.episode.thumbnailUrl,
                contentDescription = item.episode.title,
                modifier = Modifier
                    .width(128.dp)
                    .aspectRatio(16f / 9f),
                cornerRadius = 8.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.episode.category.ifBlank { item.episode.author },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MilestoneProgressBar(
                        progress = item.progressRatio,
                        height = 3.dp,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(item.progressRatio * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 为你推荐（纵向 4 行列表，卡片与「最新发布」共用 [HomeHorizontalEpisodeCard]，
 * 整体风格对齐「我的收藏 → 单集」剧集卡片）。
 */
@Composable
private fun RecommendedSection(
    episodes: List<Episode>,
    onPlay: (String) -> Unit
) {
    EpisodeListSection(title = "为你推荐", episodes = episodes, onPlay = onPlay)
}

/** 最新发布（纵向 4 行列表，取消水平滚动与切换箭头） */
@Composable
private fun LatestSection(
    episodes: List<Episode>,
    onPlay: (String) -> Unit
) {
    EpisodeListSection(title = "最新发布", episodes = episodes, onPlay = onPlay)
}

/** 首页两模块共用的纵向剧集列表：模块标题 + 上下排列的剧集卡片 */
@Composable
private fun EpisodeListSection(
    title: String,
    episodes: List<Episode>,
    onPlay: (String) -> Unit
) {
    Column {
        SectionTitle(title = title)
        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            episodes.forEach { episode ->
                HomeHorizontalEpisodeCard(
                    episode = episode,
                    onClick = { onPlay(episode.episodeid) }
                )
            }
        }
    }
}

/**
 * 首页剧集通用卡（为你推荐 / 最新发布共用）：
 * 结构对齐「我的收藏 → 单集」卡 —— Row 左右布局且垂直居中；
 * 左侧 140dp 16:9 剧集自身封面（右上难度角标、右下时长角标），
 * 右侧 标题 → 所属播客 → 播放数（耳机图标）+ 首个标签胶囊。
 */
@Composable
internal fun HomeHorizontalEpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 左侧封面：只加载剧集自身封面（列表端点未签名时由 ViewModel 富化为签名直链，
        // 绝不回退所属播客的专辑封面）；难度/时长以叠加层挂在封面右上/右下角
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
        ) {
            CoverImage(
                url = episode.coverUrl,
                contentDescription = episode.title,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 8.dp
            )
            episode.difficulty?.let {
                DifficultyBadge(
                    level = it,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
            DurationBadge(
                text = formatDuration(episode.duration),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
        }
        // 右侧文本区：标题 / 所属播客 / 播放数 + 首个标签
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = episode.podcastTitle ?: "未知播客",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
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
                episode.tags.firstOrNull()?.let { tag ->
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
                }
            }
        }
    }
}

// ================================================================
// 通用小件
// ================================================================

/** 白卡容器：rounded-2xl + 描边（对齐 Web bg-white rounded-2xl border ink-100） */
@Composable
private fun HomeCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(cornerRadius))) {
            content()
        }
    }
}

/** 里程/词汇/续播进度条：轨道 surfaceVariant，前景 primary 或指定色 */
@Composable
private fun MilestoneProgressBar(
    progress: Float,
    height: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified
) {
    val barColor = if (color == Color.Unspecified) MaterialTheme.colorScheme.primary else color
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(barColor)
        )
    }
}

/** 难度角标：白底彩字（A* 远青 / B1 黛蓝 / B2 橙 / C* 红，对齐 Web DifficultyBadge） */
@Composable
private fun DifficultyBadge(level: String, modifier: Modifier = Modifier) {
    Text(
        text = level,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.ExtraBold,
        color = difficultyColor(level),
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xF2FFFFFF))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** 时长角标：黑色半透明底 + 时钟图标（0x99000000 口径对齐收藏单集卡） */
@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = ScheduleIcon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/** 首页骨架屏：按模块形状扫光占位 */
@Composable
private fun HomeSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.6f).height(28.dp), cornerRadius = 8.dp)
        Spacer(modifier = Modifier.height(8.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp), cornerRadius = 6.dp)
        Spacer(modifier = Modifier.height(20.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(220.dp))
        Spacer(modifier = Modifier.height(16.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp))
        Spacer(modifier = Modifier.height(16.dp))
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp))
    }
}
