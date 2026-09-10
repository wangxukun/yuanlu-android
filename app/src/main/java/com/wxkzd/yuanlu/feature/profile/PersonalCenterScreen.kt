package com.wxkzd.yuanlu.feature.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.ui.components.ShimmerBox
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.min

/** 个人中心选项卡（对齐 Web personal-center 的 activeTab） */
private enum class CenterTab(val label: String) {
    JOURNEY("旅程数据"),
    ACHIEVEMENTS("里程碑"),
    SECURITY("账号与安全")
}

/**
 * 个人中心页（复刻 Web /auth/personal-center）：头部用户卡 + 三选项卡
 * （旅程数据/里程碑/账号与安全）+ 编辑资料全屏弹窗。
 */
@Composable
fun PersonalCenterRoute(
    viewModel: UserProfileViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 每次进入页面刷新全部数据（对齐 Web 各组件挂载即拉取）
    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(Unit) {
        viewModel.toast.collect { message ->
            if (message != null) {
                snackbarHostState.showSnackbar(message)
                viewModel.consumeToast()
            }
        }
    }

    fun comingSoon(label: String) {
        scope.launch { snackbarHostState.showSnackbar("$label 即将上线") }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // ---- 顶部栏：返回 + 标题 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回"
                    )
                }
                Text(
                    text = "个人中心",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                PersonalCenterContent(
                    state = state,
                    onRetry = viewModel::retry,
                    onChangeWeek = viewModel::changeWeek,
                    onOpenEdit = viewModel::openEdit,
                    onComingSoon = { comingSoon(it) }
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (state.isEditOpen) {
        EditProfileDialog(
            state = state,
            onClose = viewModel::closeEdit,
            onSwitchTab = viewModel::switchEditTab,
            onNicknameChange = viewModel::updateNickname,
            onBioChange = viewModel::updateBio,
            onLearnLevelChange = viewModel::updateLearnLevel,
            onDailyGoalChange = viewModel::updateDailyGoalMins,
            onWeeklyHoursChange = viewModel::updateWeeklyHours,
            onWeeklyWordsChange = viewModel::updateWeeklyWords,
            onAvatarPicked = viewModel::onAvatarPicked,
            onSave = viewModel::saveProfile
        )
    }
}

@Composable
private fun PersonalCenterContent(
    state: UserProfileUiState,
    onRetry: () -> Unit,
    onChangeWeek: (Int) -> Unit,
    onOpenEdit: () -> Unit,
    onComingSoon: (String) -> Unit
) {
    var activeTab by remember { mutableStateOf(CenterTab.JOURNEY) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        HeaderCard(state = state, onRetry = onRetry, onOpenEdit = onOpenEdit)

        Spacer(modifier = Modifier.height(16.dp))

        CenterTabRow(selected = activeTab, onSelect = { activeTab = it })

        Spacer(modifier = Modifier.height(12.dp))

        // 选项卡内容（对齐 Web animate-in fade + slide-from-bottom）
        AnimatedContent(
            targetState = activeTab,
            transitionSpec = {
                (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 24 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "centerTab"
        ) { tab ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (tab) {
                    CenterTab.JOURNEY -> {
                        StatsOverviewSection(state)
                        ActivityChartCard(state, onChangeWeek)
                    }
                    CenterTab.ACHIEVEMENTS -> {
                        MilestoneRoadmapCard(state)
                        AchievementsCard(state, onComingSoon)
                    }
                    CenterTab.SECURITY -> SecuritySection(state.profile, onComingSoon)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ---------- 头部用户卡 ----------

@Composable
private fun HeaderCard(
    state: UserProfileUiState,
    onRetry: () -> Unit,
    onOpenEdit: () -> Unit
) {
    ProfileCard {
        Column(modifier = Modifier.padding(20.dp)) {
            when {
                state.isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(modifier = Modifier.size(88.dp), cornerRadius = 44.dp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            ShimmerBox(modifier = Modifier.fillMaxWidth(0.5f).height(22.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            ShimmerBox(modifier = Modifier.fillMaxWidth(0.7f).height(14.dp))
                            Spacer(modifier = Modifier.height(6.dp))
                            ShimmerBox(modifier = Modifier.fillMaxWidth(0.4f).height(12.dp))
                        }
                    }
                }
                state.profile == null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = state.loadError ?: "资料加载失败",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "点击重试",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clickable { onRetry() }
                        )
                    }
                }
                else -> HeaderProfileContent(state.profile!!, onOpenEdit)
            }
        }
    }
}

@Composable
private fun HeaderProfileContent(profile: UserProfile, onOpenEdit: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        // 头像：白描边圆角（Web 4xl 白色 border）
        Box(
            modifier = Modifier
                .size(88.dp)
                .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape)
                .padding(3.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val avatarUrl = profile.avatarUrl
            if (!avatarUrl.isNullOrBlank() && avatarUrl.startsWith("http")) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "avatar",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    // 无头像占位：人形剪影（Web UserCircleIcon 于 ink-100 之上）
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(52.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ProfileUtils.displayName(profile.nickname, profile.email),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            // 远行客 badge（曙光橙，Web hiking 图标）
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                        RoundedCornerShape(50)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Hiking,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = "远行客 · ${ProfileUtils.levelLabel(profile.learnLevel)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ProfileUtils.displayBio(profile.bio),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MetaItem(Icons.Filled.CalendarMonth, "${ProfileUtils.formatDate(profile.createAt)} 加入")
                MetaItem(Icons.Filled.Place, "中国")
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    OutlinedButton(
        onClick = onOpenEdit,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = "编辑资料", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}

// ---------- 选项卡行 ----------

@Composable
private fun CenterTabRow(selected: CenterTab, onSelect: (CenterTab) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            CenterTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) }
                        .padding(bottom = 10.dp)
                ) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // 选中下划线（Web border-b-[3px]）
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    }
}

// ---------- 旅程数据：概览统计 ----------

@Composable
private fun StatsOverviewSection(state: UserProfileUiState) {
    if (state.statsLoading) {
        // 骨架屏（Web animate-pulse 三卡）
        repeat(3) {
            ShimmerBox(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                cornerRadius = 20.dp
            )
        }
        return
    }
    val stats = state.stats ?: return
    val totalKm = ProfileUtils.totalKm(stats.totalHours)
    StatCard(
        label = "累计里程",
        value = ProfileUtils.formatKm(totalKm),
        unit = "km",
        subtext = "${ProfileUtils.formatHours(stats.totalHours)}h 精听",
        icon = Icons.Filled.Hiking,
        tint = MaterialTheme.colorScheme.primary
    )
    StatCard(
        label = "连续天数",
        value = stats.streakDays.toString(),
        unit = "天",
        subtext = null,
        icon = Icons.Filled.LocalFireDepartment,
        tint = MaterialTheme.colorScheme.secondary
    )
    StatCard(
        label = "词汇路标",
        value = stats.wordsLearned.toString(),
        unit = "词",
        subtext = null,
        icon = Icons.Filled.Bookmark,
        tint = MaterialTheme.colorScheme.tertiary
    )
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    unit: String,
    subtext: String?,
    icon: ImageVector,
    tint: Color
) {
    ProfileCard {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp)
                    )
                }
                if (subtext != null) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ---------- 旅程数据：本周行程记录（周活动面积图） ----------

@Composable
private fun ActivityChartCard(state: UserProfileUiState, onChangeWeek: (Int) -> Unit) {
    ProfileCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本周行程记录",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                // 本周/上周切换（Web select 的等价分段控件）
                Row(
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(3.dp)
                ) {
                    listOf(0 to "本周", 1 to "上周").forEach { (offset, label) ->
                        val isSelected = state.weekOffset == offset
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                                )
                                .clickable { onChangeWeek(offset) }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (state.activityLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                WeeklyActivityChart(items = state.weeklyActivity)
            }
        }
    }
}

/** 周活动面积图：单调平滑曲线 + 渐变填充 + 数据点（recharts AreaChart 的 Canvas 等价） */
@Composable
private fun WeeklyActivityChart(items: List<WeeklyActivityItem>) {
    val textMeasurer = rememberTextMeasurer()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val dayLabels = remember(items, labelStyle) {
        items.map { textMeasurer.measure(it.day, labelStyle) }
    }
    val primary = MaterialTheme.colorScheme.primary

    CanvasChart(items = items, dayLabels = dayLabels, primary = primary)
}

@Composable
private fun CanvasChart(
    items: List<WeeklyActivityItem>,
    dayLabels: List<TextLayoutResult>,
    primary: Color
) {
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
    val labelStyle = TextStyle(fontSize = 10.sp, color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    val yMax = ProfileUtils.chartYMax(items.map { it.minutes })
    // 5 个整数刻度（recharts 默认 tickCount=5）：0m 底部 → yMax 顶部
    val yLabels = remember(yMax, labelStyle) {
        (0..4).map { step -> textMeasurer.measure("${step * (yMax / 4)}m", labelStyle) }
    }

    androidx.compose.foundation.Canvas(
        modifier = Modifier.fillMaxWidth().height(220.dp)
    ) {
        val leftPad = 34.dp.toPx()
        val topPad = 6.dp.toPx()
        val rightPad = 6.dp.toPx()
        val bottomPad = 22.dp.toPx()
        val chartW = size.width - leftPad - rightPad
        val chartH = size.height - topPad - bottomPad

        if (items.isEmpty() || chartW <= 0f) return@Canvas

        // Y 轴刻度文字：值越大位置越高（原点 0m 在最下方，对齐 Web）。
        // Web 端已移除 CartesianGrid，这里同样只保留刻度、不画网格线。
        yLabels.forEachIndexed { index, label ->
            val fraction = index / 4f
            val y = topPad + chartH * (1f - fraction)
            drawText(
                textLayoutResult = label,
                topLeft = Offset(leftPad - label.size.width - 6.dp.toPx(), y - label.size.height / 2f)
            )
        }

        val stepX = if (items.size > 1) chartW / (items.size - 1) else 0f
        val points = items.mapIndexed { index, item ->
            val yFraction = (item.minutes.toFloat() / yMax).coerceIn(0f, 1f)
            Offset(leftPad + index * stepX, topPad + chartH * (1f - yFraction))
        }

        // 单调平滑曲线（Fritsch–Carlson，曲线严格经过每个数据点，对齐 recharts type="monotone"）
        val linePath = monotonePath(points)

        // 渐变面积（primary 15% → 0%）
        val areaPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, topPad + chartH)
            lineTo(points.first().x, topPad + chartH)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(primary.copy(alpha = 0.15f), primary.copy(alpha = 0f)),
                startY = topPad,
                endY = topPad + chartH
            )
        )
        drawPath(
            path = linePath,
            color = primary,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 数据点：primary 圆 + 白描边（Web dot r3 stroke white 2）
        points.forEach { point ->
            drawCircle(Color.White, radius = 4.dp.toPx(), center = point)
            drawCircle(primary, radius = 3.dp.toPx(), center = point)
        }

        // X 轴星期标签
        dayLabels.forEachIndexed { index, label ->
            val x = (points[index].x - label.size.width / 2f)
                .coerceIn(0f, size.width - label.size.width.toFloat())
            drawText(textLayoutResult = label, topLeft = Offset(x, topPad + chartH + 8.dp.toPx()))
        }
    }
}

/**
 * Fritsch–Carlson 单调三次插值 → 三次贝塞尔路径。
 * 与 recharts type="monotone"（d3 curveMonotoneX 同族）一致：曲线严格经过每个数据点，
 * 且在局部极值处不产生过冲（峰值点即曲线顶点）。
 */
private fun monotonePath(points: List<Offset>): Path {
    val path = Path()
    val n = points.size
    if (n == 0) return path
    path.moveTo(points[0].x, points[0].y)
    if (n == 1) return path

    // 相邻线段的斜率（Canvas 坐标系 y 向下，符号随数据自然翻转，不影响单调性判定）
    val dx = FloatArray(n - 1) { points[it + 1].x - points[it].x }
    val delta = FloatArray(n - 1) { i ->
        if (dx[i] == 0f) 0f else (points[i + 1].y - points[i].y) / dx[i]
    }

    // 内部点切线：两侧斜率异号（局部极值）取 0，否则取加权调和平均（保证单调不过冲）
    val tangent = FloatArray(n)
    for (i in 1 until n - 1) {
        tangent[i] = if (delta[i - 1] * delta[i] <= 0f) {
            0f
        } else {
            val w1 = 2f * dx[i] + dx[i - 1]
            val w2 = dx[i] + 2f * dx[i - 1]
            (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])
        }
    }
    // 端点切线（d3 slope2 口径）：由 3×端段斜率与相邻切线推得，避免端部翘起
    tangent[0] = if (n == 2) delta[0] else (3f * delta[0] - tangent[1]) / 2f
    tangent[n - 1] = if (n == 2) delta[n - 2] else (3f * delta[n - 2] - tangent[n - 2]) / 2f

    // Hermite → 三次贝塞尔：控制点取两端各 1/3 段长处的切线端点
    for (i in 0 until n - 1) {
        val h = dx[i]
        path.cubicTo(
            points[i].x + h / 3f, points[i].y + tangent[i] * h / 3f,
            points[i + 1].x - h / 3f, points[i + 1].y - tangent[i + 1] * h / 3f,
            points[i + 1].x, points[i + 1].y
        )
    }
    return path
}

// ---------- 里程碑：路图 ----------

/** 远路里程碑路图：把累计收听换算成里程，落在蜿蜒路径上（Web MilestoneRoadmap） */
@Composable
private fun MilestoneRoadmapCard(state: UserProfileUiState) {
    if (state.statsLoading) {
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(190.dp), cornerRadius = 20.dp)
        return
    }
    val totalKm = ProfileUtils.totalKm(state.stats?.totalHours ?: 0.0)
    ProfileCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "远路里程碑",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${ProfileUtils.formatKm(totalKm)} km",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            MilestoneStrip(totalKm = totalKm)
        }
    }
}

@Composable
private fun MilestoneStrip(totalKm: Double) {
    val textMeasurer = rememberTextMeasurer()
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val surface = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.outline
    val labelDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
    val labelFaint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)

    val labelStyleDim = TextStyle(fontSize = 10.sp, color = labelDim)
    val labelStyleFaint = TextStyle(fontSize = 9.sp, color = labelFaint)
    val labelStyleReached = TextStyle(fontSize = 10.sp, color = primary, fontWeight = FontWeight.SemiBold)
    val kmStyleReached = TextStyle(fontSize = 9.sp, color = secondary, fontWeight = FontWeight.Medium)

    val milestones = ProfileUtils.MILESTONES
    val labels = remember(milestones, labelStyleDim, labelStyleReached) {
        milestones.map { textMeasurer.measure(it.second, labelStyleReached) to textMeasurer.measure(it.second, labelStyleDim) }
    }
    val kmLabels = remember(milestones, kmStyleReached, labelStyleFaint) {
        milestones.map { textMeasurer.measure("${it.first}km", kmStyleReached) to textMeasurer.measure("${it.first}km", labelStyleFaint) }
    }

    // 当前进度脉冲点（Web animate r/opacity 2s 循环）
    val pulse by rememberInfiniteTransition(label = "milestonePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxWidth().height(128.dp)) {
        val W = size.width
        val H = size.height
        val pad = 42.dp.toPx()
        val count = milestones.size
        val xs = (0 until count).map { pad + it * (W - pad * 2) / (count - 1) }
        val base = H * 0.52f
        val amp = H * 0.10f
        val ys = listOf(
            base + amp,
            base - amp,
            base + amp * 0.7f,
            base - amp * 0.9f,
            base + amp * 0.5f
        )
        val radius = 6.dp.toPx()

        // 蜿蜒平滑路径
        val path = Path().apply {
            moveTo(xs[0], ys[0])
            for (i in 1 until count) {
                val midX = (xs[i - 1] + xs[i]) / 2f
                val midY = (ys[i - 1] + ys[i]) / 2f
                quadraticBezierTo(xs[i - 1], ys[i - 1], midX, midY)
            }
            lineTo(xs[count - 1], ys[count - 1])
        }

        // 全程虚线（ink-200 圆点虚线）
        drawPath(
            path = path,
            color = outline,
            style = Stroke(
                width = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.1f, 9.dp.toPx()))
            )
        )
        // 已完成段实线（按进度裁剪，Web strokeDasharray progress*1000 等价）
        val progressRatio = (totalKm / milestones.last().first).coerceIn(0.0, 1.0)
        if (progressRatio > 0.0) {
            val pathLength = android.graphics.PathMeasure(path.asAndroidPath(), false).length
            drawPath(
                path = path,
                color = primary,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(pathLength * progressRatio.toFloat(), 100000f)
                    )
                )
            )
        }

        // 里程碑节点
        milestones.forEachIndexed { i, (km, _) ->
            val reached = totalKm >= km
            val x = xs[i]
            val y = ys[i]

            if (reached) {
                drawCircle(primary, radius = radius, center = Offset(x, y))
                // 冲线小旗（accent 三角）
                val flag = Path().apply {
                    moveTo(x, y - radius)
                    lineTo(x, y - radius - 11.dp.toPx())
                    lineTo(x + 9.dp.toPx(), y - radius - 7.5.dp.toPx())
                    lineTo(x, y - radius - 4.5.dp.toPx())
                    close()
                }
                drawPath(flag, color = secondary)
            } else {
                drawCircle(surface, radius = radius, center = Offset(x, y))
                drawCircle(
                    outline,
                    radius = radius,
                    center = Offset(x, y),
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // 名称标签（节点下方）
            val label = if (reached) labels[i].first else labels[i].second
            drawText(
                textLayoutResult = label,
                topLeft = Offset(x - label.size.width / 2f, y + radius + 6.dp.toPx())
            )
            // km 标签（旗帜/节点上方）
            val kmLabel = if (reached) kmLabels[i].first else kmLabels[i].second
            val kmY = if (reached) y - radius - 15.dp.toPx() - kmLabel.size.height else y - radius - 7.dp.toPx() - kmLabel.size.height
            drawText(
                textLayoutResult = kmLabel,
                topLeft = Offset(x - kmLabel.size.width / 2f, kmY)
            )
        }

        // 当前进度脉冲点（介于 0 与满里程之间才显示）
        if (totalKm > 0.0 && totalKm < milestones.last().first) {
            val segmentIndex = min(count - 2, floor(progressRatio * (count - 1)).toInt())
            val t = (progressRatio * (count - 1) - segmentIndex).toFloat()
            val px = xs[segmentIndex] + (xs[segmentIndex + 1] - xs[segmentIndex]) * t
            val py = ys[segmentIndex] + (ys[segmentIndex + 1] - ys[segmentIndex]) * t
            val pulseRadius = radius + 2.dp.toPx() + (6.dp.toPx() * pulse)
            val pulseAlpha = 0.5f - (0.35f * pulse)
            drawCircle(primary.copy(alpha = pulseAlpha), radius = pulseRadius, center = Offset(px, py))
            drawCircle(primary, radius = 4.dp.toPx(), center = Offset(px, py))
        }
    }
}

// ---------- 里程碑：成就墙 ----------

@Composable
private fun AchievementsCard(state: UserProfileUiState, onComingSoon: (String) -> Unit) {
    ProfileCard {
        Column(modifier = Modifier.padding(20.dp)) {
            if (state.achievementsLoading) {
                // 骨架屏：标题条 + 2 行 4 列方格
                ShimmerBox(modifier = Modifier.width(96.dp).height(20.dp))
                Spacer(modifier = Modifier.height(16.dp))
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        repeat(4) {
                            ShimmerBox(
                                modifier = Modifier.weight(1f).aspectRatio(1f),
                                cornerRadius = 14.dp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                val unlockedCount = state.achievements.count { it.unlocked }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "远路里程碑 ($unlockedCount)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "全部查看",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onComingSoon("完整成就墙") }
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                if (state.achievements.isEmpty()) {
                    Text(
                        text = "暂无里程碑数据",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    // 4 列网格，仅展示前 8 个（已解锁排前，Web 同口径）
                    state.achievements.take(8).chunked(4).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowItems.forEach { achievement ->
                                AchievementTile(
                                    achievement = achievement,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            repeat(4 - rowItems.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

/** 成就方格：点亮 = 远青底色，未解锁 = 灰暗（Web unlocked/unlocked 样式） */
@Composable
private fun AchievementTile(achievement: AchievementItem, modifier: Modifier = Modifier) {
    val unlocked = achievement.unlocked
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .background(
                if (unlocked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                RoundedCornerShape(14.dp)
            )
            .border(
                width = 1.dp,
                color = if (unlocked) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                },
                shape = RoundedCornerShape(14.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = achievement.icon,
            fontSize = 26.sp,
            modifier = Modifier
                .padding(4.dp)
                .let { base ->
                    if (unlocked) base else base.alpha(0.4f)
                }
        )
    }
}

// ---------- 账号与安全 ----------

@Composable
private fun SecuritySection(profile: UserProfile?, onComingSoon: (String) -> Unit) {
    val hasPhone = !profile?.phone.isNullOrBlank()
    val hasRealEmail = !profile?.email.isNullOrBlank() && !ProfileUtils.isPlaceholderEmail(profile?.email)
    val passwordSet = !(ProfileUtils.isPlaceholderEmail(profile?.email) && !hasRealEmail)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SecurityCard(
            icon = Icons.Filled.Smartphone,
            tint = MaterialTheme.colorScheme.primary,
            title = "手机号",
            trailing = if (!hasPhone) {
                { BindButton("绑定手机号") { onComingSoon("绑定手机号") } }
            } else null
        ) {
            if (hasPhone) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ProfileUtils.maskPhone(profile?.phone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "已验证",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                UnboundBadge()
            }
        }
        SecurityCard(
            icon = Icons.Filled.Email,
            tint = MaterialTheme.colorScheme.secondary,
            title = "邮箱",
            trailing = if (!hasRealEmail) {
                { BindButton("绑定邮箱") { onComingSoon("绑定邮箱") } }
            } else null
        ) {
            if (hasRealEmail) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ProfileUtils.maskEmail(profile?.email),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = "已验证",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                UnboundBadge()
            }
        }
        SecurityCard(
            icon = Icons.Filled.Lock,
            tint = MaterialTheme.colorScheme.tertiary,
            title = "登录密码"
        ) {
            Text(
                text = if (passwordSet) "已设置" else "未设置",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnboundBadge() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "未绑定",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(13.dp)
        )
    }
}

/** 账号安全列表卡（Web AccountSecurityTab 的圆角卡 + 图标底 + 标题/副文案 + 可选尾随操作） */
@Composable
private fun SecurityCard(
    icon: ImageVector,
    tint: Color,
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    subtitle: @Composable () -> Unit
) {
    ProfileCard(onClick = null) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                subtitle()
            }
            if (trailing != null) {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** 绑定操作胶囊按钮（Web bg-primary-50 圆角胶囊；端内先以即将上线占位） */
@Composable
private fun BindButton(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

// ---------- 通用卡片 ----------

/** 白底圆角描边卡（Web bg-white rounded-2xl border ink-100 shadow-sm 的 Material 等价） */
@Composable
private fun ProfileCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
    ) {
        Box { content() }
    }
}
