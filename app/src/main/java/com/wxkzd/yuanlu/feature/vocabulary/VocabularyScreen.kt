package com.wxkzd.yuanlu.feature.vocabulary

import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.feature.player.Accent100
import com.wxkzd.yuanlu.feature.player.Accent500
import com.wxkzd.yuanlu.feature.player.Ink100
import com.wxkzd.yuanlu.feature.player.Ink200
import com.wxkzd.yuanlu.feature.player.Ink400
import com.wxkzd.yuanlu.feature.player.Ink50
import com.wxkzd.yuanlu.feature.player.Ink600
import com.wxkzd.yuanlu.feature.player.Ink700
import com.wxkzd.yuanlu.feature.player.Ink800
import com.wxkzd.yuanlu.feature.player.Ink900
import com.wxkzd.yuanlu.feature.player.Primary400
import com.wxkzd.yuanlu.feature.player.Primary50
import com.wxkzd.yuanlu.feature.player.Primary500
import com.wxkzd.yuanlu.feature.player.Primary600
import com.wxkzd.yuanlu.feature.player.Primary900
import com.wxkzd.yuanlu.feature.player.isDarkAppearance
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox

/**
 * 生词本列表页（复刻 Web /library/vocabulary 移动端）：
 * 标题 + 统计卡（总计/待复习/已掌握）→ 复习入口横幅 → 状态 Tab / 搜索 / 排序 →
 * 列表卡片（单词/音标/释义/原句片段，点按展开词典详情：英美发音、核心释义、
 * 原声出处、词源记忆、标记掌握/重新学习、彻底删除）。
 * 发音用独立 MediaPlayer 播词典音频（OSS/dictvoice），无可播地址时静默。
 */
@Composable
fun VocabularyScreen(viewModel: VocabularyViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeToast()
        }
    }

    // 发音播放器：随页面生命周期创建/释放（同 VocabularySheet 模式）
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }
    fun playUrl(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            player.reset()
            player.setDataSource(url)
            player.prepare()
            player.start()
        } catch (_: Exception) {
            // 音频过期/网络失败静默忽略
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading -> LoadingBox()
            state.error != null -> ErrorBox(message = state.error!!, onRetry = viewModel::load)
            else -> VocabularyListContent(
                state = state,
                onSearch = viewModel::setSearchQuery,
                onTab = viewModel::setFilterStatus,
                onSort = viewModel::setSortMethod,
                onToggleExpand = viewModel::toggleExpanded,
                onStartReview = viewModel::startReview,
                onPlayUrl = ::playUrl,
                onRequestDelete = viewModel::requestDelete,
                onToggleStatus = viewModel::toggleStatus
            )
        }

        // 删除确认（对齐 Web modal-bottom 确认弹窗）
        state.deletingItem?.let { item ->
            AlertDialog(
                onDismissRequest = viewModel::dismissDelete,
                title = { Text("确定要彻底删除该生词吗？", fontWeight = FontWeight.Bold) },
                text = { Text("「${item.word}」将被永久移出生词本，此操作不可恢复。") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmDelete) {
                        if (state.isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("确定删除", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissDelete) { Text("取消") }
                },
                containerColor = if (isDarkAppearance()) Ink800 else Color.White
            )
        }
    }
}

/** 单词发音地址：词典 US/UK → 保存时的 speakUrl → 有道 dictvoice 合成兜底 */
private fun wordAudioUrl(item: VocabularyItem): String =
    item.dictEntry?.audioUs
        ?: item.dictEntry?.audioUk
        ?: item.speakUrl
        ?: youdaoDictVoiceUrl(item.word)

@Composable
private fun VocabularyListContent(
    state: VocabularyUiState,
    onSearch: (String) -> Unit,
    onTab: (VocabStatusTab) -> Unit,
    onSort: (VocabSortMethod) -> Unit,
    onToggleExpand: (Int) -> Unit,
    onStartReview: () -> Unit,
    onPlayUrl: (String?) -> Unit,
    onRequestDelete: (VocabularyItem) -> Unit,
    onToggleStatus: (VocabularyItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, top = 12.dp, bottom = 96.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.MenuBook,
                        contentDescription = null,
                        tint = if (isDarkAppearance()) Primary400 else Primary600,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "生词本",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "管理你的生词收藏并进行科学的间隔复习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 统计卡：总计 / 待复习 / 已掌握
        item { StatsRow(state) }

        // 复习入口横幅
        item { ReviewBanner(state = state, onStartReview = onStartReview) }

        // 状态 Tab + 搜索 + 排序
        item {
            VocabularyControls(
                state = state,
                onSearch = onSearch,
                onTab = onTab,
                onSort = onSort
            )
        }

        // 列表
        val list = state.filteredList
        if (list.isEmpty()) {
            item {
                EmptyBox(
                    message = if (state.vocabulary.isEmpty()) "空空如也，快去精听时点词收藏吧"
                    else if (state.searchQuery.isNotBlank()) "未找到匹配的生词"
                    else if (state.filterStatus == VocabStatusTab.MASTERED) "空空如也，暂无已掌握的单词"
                    else "空空如也，暂无学习中的单词"
                )
            }
        } else {
            items(list, key = { it.vocabularyid }) { item ->
                WordItemCard(
                    item = item,
                    isExpanded = state.expandedId == item.vocabularyid,
                    onToggle = { onToggleExpand(item.vocabularyid) },
                    onPlayWord = { onPlayUrl(wordAudioUrl(item)) },
                    onPlayUrl = onPlayUrl,
                    onRequestDelete = { onRequestDelete(item) },
                    onToggleStatus = { onToggleStatus(item) }
                )
            }
        }
    }
}

// ---------------- 统计卡 ----------------

@Composable
private fun StatsRow(state: VocabularyUiState) {
    val isDark = isDarkAppearance()
    val cardBg = if (isDark) Ink900 else Color.White
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatCard(
            label = "总计", count = state.total,
            icon = { Icon(Icons.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
            iconBg = if (isDark) Ink800 else Ink50,
            cardBg = cardBg,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "待复习", count = state.dueCount,
            icon = { Icon(Icons.Filled.Schedule, null, tint = Accent500, modifier = Modifier.size(16.dp)) },
            iconBg = Accent500.copy(alpha = 0.1f),
            cardBg = cardBg,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "已掌握", count = state.masteredCount,
            icon = { Icon(Icons.Filled.MilitaryTech, null, tint = Primary500, modifier = Modifier.size(16.dp)) },
            iconBg = Primary500.copy(alpha = 0.1f),
            cardBg = cardBg,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    count: Int,
    icon: @Composable () -> Unit,
    iconBg: Color,
    cardBg: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg)
                    .padding(6.dp)
            ) { icon() }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// ---------------- 复习入口横幅 ----------------

@Composable
private fun ReviewBanner(state: VocabularyUiState, onStartReview: () -> Unit) {
    val isDark = isDarkAppearance()
    if (state.dueCount > 0) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Primary900.copy(alpha = 0.45f) else Primary600
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = if (isDark) Primary400 else Color(0xFF7DC5A8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "复习计划已就绪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "根据遗忘曲线，你有 ${state.dueCount} 个生词需要复习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White,
                    modifier = Modifier.clickable(onClickLabel = "开始复习") { onStartReview() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayCircle,
                            contentDescription = null,
                            tint = if (isDark) Primary400 else Primary600,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "开始复习",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Primary400 else Primary600
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (isDark) Ink900 else Color.White,
            shadowElevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Primary500,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "全部完成了！",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "你做得很好，今日复习任务已清空。快去听播客添加新词吧。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------- Tab / 搜索 / 排序 ----------------

@Composable
private fun VocabularyControls(
    state: VocabularyUiState,
    onSearch: (String) -> Unit,
    onTab: (VocabStatusTab) -> Unit,
    onSort: (VocabSortMethod) -> Unit
) {
    val isDark = isDarkAppearance()
    val chipContainer = if (isDark) Ink800 else Ink100

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 状态 Tab：学习中 / 已掌握（对齐 Web active: primary-50 / success-10）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(chipContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VocabStatusTab.entries.forEach { tab ->
                val selected = state.filterStatus == tab
                val selBg = when (tab) {
                    VocabStatusTab.LEARNING -> if (isDark) Primary900 else Primary50
                    VocabStatusTab.MASTERED -> Primary500.copy(alpha = 0.12f)
                }
                val selColor = when (tab) {
                    VocabStatusTab.LEARNING -> if (isDark) Primary400 else Primary600
                    VocabStatusTab.MASTERED -> Primary500
                }
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) selColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (selected) selBg else Color.Transparent)
                        .clickable(onClickLabel = tab.label) { onTab(tab) }
                        .padding(vertical = 8.dp)
                        .fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Ink800 else Color.White)
                .border(1.dp, if (isDark) Ink700 else Ink200, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = Ink400,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = state.searchQuery,
                onValueChange = onSearch,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(if (isDark) Primary400 else Primary600),
                decorationBox = { innerField ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (state.searchQuery.isEmpty()) {
                            Text(
                                text = "搜索单词或释义...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ink400
                            )
                        }
                        innerField()
                    }
                }
            )
            if (state.searchQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "清空搜索",
                    tint = Ink400,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "清空搜索") { onSearch("") }
                )
            }
        }

        // 排序 chips：复习时间 / 添加时间 / A-Z
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VocabSortMethod.entries.forEach { method ->
                val selected = state.sortMethod == method
                Text(
                    text = method.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) (if (isDark) Primary500 else Primary600) else Color.Transparent)
                        .border(
                            1.dp,
                            if (selected) Color.Transparent else (if (isDark) Ink700 else Ink200),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable(onClickLabel = method.label) { onSort(method) }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ---------------- 列表卡片 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WordItemCard(
    item: VocabularyItem,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onPlayWord: () -> Unit,
    onPlayUrl: (String?) -> Unit,
    onRequestDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    val isDark = isDarkAppearance()
    val cardBg = if (isDark) Ink900 else Color.White
    val mastered = item.status == "MASTERED"
    val due = isDue(item.nextReviewAt) && !mastered
    // 紧凑释义行：优先词典首条（带词性前缀），退回保存时拼装的 definition（本就含 [pos]）
    val definition = item.dictEntry?.definitions?.firstOrNull()
        ?.let { def -> if (def.pos.isNotBlank()) "[${def.pos}] ${def.meaningCn}" else def.meaningCn }
        ?: item.definition ?: "暂无定义"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        shadowElevation = if (isExpanded) 4.dp else 1.dp,
        border = if (isExpanded) androidx.compose.foundation.BorderStroke(1.dp, Primary600.copy(alpha = 0.3f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // ---- 紧凑视图：单词 / 音标 / 释义 / 原句片段 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = if (isExpanded) "收起" else "展开详情") { onToggle() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.word,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val phonetic = item.dictEntry?.phoneticsUs ?: item.dictEntry?.phoneticsUk
                            phonetic?.let {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDark) Ink800 else Ink50)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            // 到期状态点：warning 呼吸 / 未到期灰点
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            mastered -> Primary500.copy(alpha = 0.35f)
                                            due -> Accent500
                                            else -> if (isDark) Ink700 else Ink200
                                        }
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = definition,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        // 原句片段（任务要求：例句/原句）
                        item.contextSentence?.let { sentence ->
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "“$sentence”",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // 发音按钮（收起时显示，对齐 Web 40dp 触达区）
                    if (!isExpanded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(onClickLabel = "播放发音") { onPlayWord() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "播放发音",
                                tint = if (isDark) Primary400 else Primary600.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = if (isDark) Ink800 else Ink100.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 熟练度 5 格条
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(5) { level ->
                            Box(
                                modifier = Modifier
                                    .width(5.dp)
                                    .height(if (level < item.proficiency) 14.dp else 10.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (level < item.proficiency) {
                                            if (isDark) Primary400 else Primary500
                                        } else {
                                            if (isDark) Ink800 else Ink100
                                        }
                                    )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // 到期徽章 / 已掌握
                    if (mastered) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Primary500,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "已掌握",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary500
                            )
                        }
                    } else if (due) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Accent500,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "需要复习",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Accent500
                            )
                        }
                    } else {
                        Text(
                            text = "下次复习 ${formatReviewDate(item.nextReviewAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (isExpanded) 180f else 0f,
                        animationSpec = tween(250),
                        label = "chevron"
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronRotation)
                    )
                }
            }

            // ---- 展开详情（词典富数据）----
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ExpandedWordDetail(
                    item = item,
                    onPlayUrl = onPlayUrl,
                    onRequestDelete = onRequestDelete,
                    onToggleStatus = onToggleStatus
                )
            }
        }
    }
}

// ---------------- 展开详情 ----------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpandedWordDetail(
    item: VocabularyItem,
    onPlayUrl: (String?) -> Unit,
    onRequestDelete: () -> Unit,
    onToggleStatus: () -> Unit
) {
    val isDark = isDarkAppearance()
    val entry = item.dictEntry
    val contentColor = if (isDark) Color(0xFFE8E3D9) else Ink800
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        (if (isDark) Ink800 else Ink100).copy(alpha = 0.3f)
                    )
                )
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 英美音标 + 发音
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhoneticPill(
                label = "US",
                phonetic = entry?.phoneticsUs,
                labelColor = if (isDark) Primary400 else Primary600,
                onPlay = { onPlayUrl(entry?.audioUs ?: youdaoDictVoiceUrl(item.word)) },
                isDark = isDark
            )
            PhoneticPill(
                label = "UK",
                phonetic = entry?.phoneticsUk,
                labelColor = Accent500,
                onPlay = { onPlayUrl(entry?.audioUk ?: youdaoDictVoiceUrl(item.word, us = false)) },
                isDark = isDark
            )
        }

        // 核心释义
        val definitions = entry?.definitions.orEmpty()
        if (definitions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Ink800.copy(alpha = 0.5f) else Ink50.copy(alpha = 0.7f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SectionLabel("核心释义", subColor)
                definitions.forEach { def ->
                    Row {
                        Text(
                            text = def.pos,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Primary400 else Primary600,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background((if (isDark) Primary500 else Primary600).copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = def.meaningCn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            def.meaningEn?.let { en ->
                                Text(
                                    text = en,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 原声出处（词高亮）
        item.contextSentence?.let { sentence ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                (if (isDark) Primary400 else Primary600).copy(alpha = 0.05f),
                                Accent500.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(1.dp, (if (isDark) Primary400 else Primary600).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                SectionLabel("原声出处", subColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildHighlightedContext(
                        sentence = sentence,
                        word = item.word,
                        baseColor = contentColor,
                        highlightColor = if (isDark) Primary400 else Primary600
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                item.translation?.let { cn ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cn,
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor.copy(alpha = 0.7f)
                    )
                }
                item.episodeTitle?.let { title ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "来自《$title》",
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor.copy(alpha = 0.55f)
                    )
                }
            }
        }

        // 词源记忆（前缀/词根/后缀 + 记忆技巧）
        entry?.etymology?.let { ety ->
            if (ety.prefix != null || ety.root != null || ety.suffix != null || !ety.mnemonic.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent500.copy(alpha = if (isDark) 0.1f else 0.06f))
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Lightbulb,
                            contentDescription = null,
                            tint = Accent500,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        SectionLabel("词源记忆", subColor)
                    }
                    val parts = listOfNotNull(
                        ety.prefix?.let { "前缀 · $it" },
                        ety.root?.let { "词根 · $it" },
                        ety.suffix?.let { "后缀 · $it" }
                    )
                    if (parts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            parts.forEach { part ->
                                Text(
                                    text = part,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Ink400 else Ink600,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isDark) Ink800 else Color.White)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                    ety.mnemonic?.takeIf { it.isNotBlank() }?.let { mnemonic ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 $mnemonic",
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor
                        )
                    }
                }
            }
        }

        // 操作：标记为已掌握 / 重新学习 + 彻底删除
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (item.status == "MASTERED") {
                    (if (isDark) Ink800 else Ink100)
                } else {
                    Primary500
                },
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = if (item.status == "MASTERED") "重新学习" else "标记为已掌握") {
                        onToggleStatus()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (item.status == "MASTERED") Icons.Filled.Refresh else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (item.status == "MASTERED") MaterialTheme.colorScheme.onSurface else Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (item.status == "MASTERED") "重新学习" else "标记为已掌握",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (item.status == "MASTERED") MaterialTheme.colorScheme.onSurface else Color.White
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                modifier = Modifier.clickable(onClickLabel = "删除生词") { onRequestDelete() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "彻底删除",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "彻底删除",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneticPill(
    label: String,
    phonetic: String?,
    labelColor: Color,
    onPlay: () -> Unit,
    isDark: Boolean
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isDark) Ink800 else Color.White.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black,
                color = labelColor
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = phonetic ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "播放${label}发音",
                tint = labelColor,
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "播放${label}发音") { onPlay() }
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = color.copy(alpha = 0.55f)
    )
}
