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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.SpeechNotebook
import com.wxkzd.yuanlu.domain.model.WeakSentenceRecord
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import android.widget.Toast

/** 画像雷达紫（Web recharts #7c3aed） */
private val ProfileRadarColor = Color(0xFF7C3AED)
/** 音素雷达靛蓝（Web recharts #4f46e5） */
private val PhonemeRadarColor = Color(0xFF4F46E5)
/** 复习横幅深青蓝（Web bg-info-600 近似） */
private val ReviewBannerLight = Color(0xFF3D6A8D)
private val ReviewBannerDark = Color(0xFF1B3348)
/** 达成绿（Web success #2e8f6f） */
private val SuccessGreen = Color(0xFF2E8F6F)

/**
 * 发音弱项本主页（复刻 Web /library/pronunciation 移动端布局，参照设计稿图1）：
 * 发音能力画像（五维雷达 + CEFR + 三统计）→ 统计面板（待复习句子/最弱音素/已攻克音素 + 达人榜入口）→
 * 复习计划已就绪横幅（开始闯关复习）→ 薄弱音素诊断雷达 → 待复习弱项句子列表（含非会员锁定卡）。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PronunciationNotebookRoute(
    onBack: () -> Unit,
    onOpenPractice: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onOpenSpeechEval: (episodeid: String, subtitleId: Int?) -> Unit,
    viewModel: PronunciationNotebookViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // ---- 顶部导航栏 ----
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
                text = "发音弱项本",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        when {
            state.isLoading -> LoadingBox()
            state.loadError != null -> ErrorBox(message = state.loadError!!, onRetry = viewModel::retry)
            else -> {
                val notebook = state.notebook
                if (notebook == null) {
                    EmptyBox("暂无数据")
                } else {
                    PullToRefreshBox(
                        isRefreshing = state.isRefreshing,
                        onRefresh = viewModel::refresh
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Spacer(modifier = Modifier.height(4.dp))
                            SpeechProfileCard(notebook)
                            StatsPanelHeader(
                                notebook = notebook,
                                onOpenLeaderboard = onOpenLeaderboard
                            )
                            ReviewPlanBanner(
                                weakCount = notebook.totalErrors,
                                onStartPractice = onOpenPractice
                            )
                            PhonemeRadarCard(notebook)
                            WeakSentenceListCard(
                                notebook = notebook,
                                onOpenSpeechEval = onOpenSpeechEval
                            )
                            Spacer(modifier = Modifier.height(88.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------- 1. 发音能力画像卡（五维雷达 + CEFR + 三统计 + 说明文案） ----------

@Composable
private fun SpeechProfileCard(notebook: SpeechNotebook) {
    val profile = notebook.profile
    val isDark = isDarkAppearance()

    CardShell {
        // 标题行：仪表图标 + 发音能力画像 + CEFR 徽章
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconBadge(
                icon = Icons.Filled.Speed,
                tint = MaterialTheme.colorScheme.secondary,
                bgAlpha = 0.1f
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "发音能力画像",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            profile.cefrLevel?.let { level ->
                Text(
                    text = "$level 级",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            RoundedCornerShape(50)
                        )
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }

        // 五维雷达（准确度/流利度/完整度/语速适配/综合表现）
        if (profile.hasData) {
            val dims = profile.radarDims
            RadarChart(
                labels = dims.map { it.first },
                values = dims.map { it.second.toFloat() },
                strokeColor = ProfileRadarColor,
                gridColor = if (isDark) Color(0xFF3A342C) else Color(0xFFE5E7EB),
                labelColor = if (isDark) Color(0xFFA8A29E) else Color(0xFF6B7280),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(vertical = 8.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "暂无画像数据",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "完成语音评测后自动生成你的能力雷达",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 三统计格：评测次数 / 综合得分 / 平均语速
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCell(
                label = "评测次数",
                value = profile.evalCount.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "综合得分",
                value = profile.avgOverall?.let { Math.round(it).toString() } ?: "—",
                modifier = Modifier.weight(1f)
            )
            StatCell(
                label = "平均语速",
                value = profile.avgSpeed?.let { "${Math.round(it)} 词/分" } ?: "—",
                modifier = Modifier.weight(1f)
            )
        }

        // 说明文案
        val hint = if (profile.hasData && profile.cefrLevel != null) {
            "根据你的评测表现，当前发音水平约为 CEFR ${profile.cefrLevel} 级，首页「为你推荐」已按该等级匹配剧集难度。评测越多，画像越准。"
        } else {
            "在任意剧集的跟读练习中完成语音评测，即可生成专属画像并获得难度匹配推荐。"
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (profile.hasData && profile.cefrLevel != null)
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.06f)
                    else
                        (if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background),
                    RoundedCornerShape(12.dp)
                )
                .padding(12.dp)
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = if (profile.hasData && profile.cefrLevel != null)
                    MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- 2. 统计面板（页头 + 达人榜入口 + 三统计卡） ----------

@Composable
private fun StatsPanelHeader(
    notebook: SpeechNotebook,
    onOpenLeaderboard: () -> Unit
) {
    Column {
        // 标题 + 达人榜入口
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "发音弱项本",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "针对性攻克发音短板，提升口语地道程度。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 发音达人榜入口（社区功能，对所有用户开放）
            Surface(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)),
                modifier = Modifier.clickable { onOpenLeaderboard() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "发音达人榜",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 三统计卡：待复习句子 / 最弱音素 / 已攻克音素
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            MiniStatCard(
                icon = Icons.Filled.Description,
                tint = MaterialTheme.colorScheme.secondary,
                label = "待复习句子",
                value = notebook.totalErrors.toString(),
                modifier = Modifier.weight(1f)
            )
            MiniStatCard(
                icon = Icons.Filled.TrackChanges,
                tint = MaterialTheme.colorScheme.error,
                label = "最弱音素",
                value = notebook.weakestPhoneme ?: "-",
                mono = true,
                modifier = Modifier.weight(1f)
            )
            MiniStatCard(
                icon = Icons.Filled.EmojiEvents,
                tint = SuccessGreen,
                label = "已攻克音素",
                value = notebook.masteredPhonemeCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ---------- 3. 复习计划已就绪横幅 ----------

@Composable
private fun ReviewPlanBanner(
    weakCount: Int,
    onStartPractice: () -> Unit
) {
    val isDark = isDarkAppearance()
    if (weakCount > 0) {
        Surface(
            color = if (isDark) ReviewBannerDark else ReviewBannerLight,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Psychology,
                        contentDescription = null,
                        tint = Color(0xFF9CC3E0),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "复习计划已就绪",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "针对你的发音短板，你有 $weakCount 个弱项句子需要复习。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = onStartPractice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = ReviewBannerLight
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "开始闯关复习", fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        CardShell {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = SuccessGreen,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "全部完成了！",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "你做得很好，目前没有待复习的弱项句子。快去挑战新播客吧。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- 4. 薄弱音素诊断雷达 ----------

@Composable
private fun PhonemeRadarCard(notebook: SpeechNotebook) {
    val isDark = isDarkAppearance()
    CardShell {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.TrackChanges,
                tint = MaterialTheme.colorScheme.primary,
                bgAlpha = 0.1f
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "薄弱音素诊断雷达",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        val points = PronunciationUtils.phonemeRadarPoints(notebook.phonemeStats)
        if (points.size >= 3) {
            RadarChart(
                labels = points.map { it.first },
                values = points.map { it.second },
                strokeColor = PhonemeRadarColor,
                gridColor = if (isDark) Color(0xFF3A342C) else Color(0xFFE5E7EB),
                labelColor = if (isDark) Color(0xFFA8A29E) else Color(0xFF6B7280),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                        RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.TrackChanges,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "数据积累中",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "完成更多评测即可解锁雷达图",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ---------- 5. 待复习弱项句子列表 ----------

@Composable
private fun WeakSentenceListCard(
    notebook: SpeechNotebook,
    onOpenSpeechEval: (episodeid: String, subtitleId: Int?) -> Unit
) {
    val context = LocalContext.current
    CardShell {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            IconBadge(
                icon = Icons.Filled.EmojiEvents,
                tint = MaterialTheme.colorScheme.secondary,
                bgAlpha = 0.1f
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "待复习弱项句子",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (notebook.errors.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isDarkAppearance())
                            MaterialTheme.colorScheme.surfaceVariant
                        else MaterialTheme.colorScheme.background,
                        RoundedCornerShape(16.dp)
                    )
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "太棒了！",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "您目前没有待复习的弱项句子",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notebook.errors.forEach { record ->
                    // 点卡片直达语音评测并定位该句录音卡（对齐 Web practice=true&subtitleId= 跳转）
                    WeakSentenceRow(record = record) {
                        record.episodeid?.let { onOpenSpeechEval(it, record.subtitleId) }
                    }
                }
                // 非会员试用：剩余弱项句子锁定卡
                if (notebook.lockedCount > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Toast.makeText(context, "解锁 PRO 会员查看完整弱项本", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(14.dp)
                        ) {
                            IconBadge(
                                icon = Icons.Filled.Lock,
                                tint = MaterialTheme.colorScheme.primary,
                                bgAlpha = 0.1f
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "还有 ${notebook.lockedCount} 条弱项句子待攻克",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "解锁 PRO 会员查看完整弱项本，开始针对性循环练习",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            ) {
                                Text(
                                    text = "解锁",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 弱项句子行：剧集封面 + 标题/原句/得分徽章 + 测试日期（Web 列表项移动端形态） */
@Composable
private fun WeakSentenceRow(
    record: WeakSentenceRecord,
    onClick: () -> Unit
) {
    val isDark = isDarkAppearance()
    Surface(
        color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isDark) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            CoverImage(
                url = record.episodeCoverUrl,
                contentDescription = record.episodeTitle,
                modifier = Modifier.size(56.dp),
                cornerRadius = 10.dp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.episodeTitle ?: "未知播客",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = record.targetText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val tone = PronunciationUtils.classifyScore(record.lastScore)
                    val (bg, fg) = when (tone) {
                        ScoreTone.GOOD -> SuccessGreen.copy(alpha = 0.12f) to SuccessGreen
                        ScoreTone.MID -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) to MaterialTheme.colorScheme.secondary
                        ScoreTone.BAD -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f) to MaterialTheme.colorScheme.error
                    }
                    Text(
                        text = "上次得分: ${record.lastScore}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier
                            .background(bg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = PronunciationUtils.formatZhDate(record.recognitionDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ---------- 通用小组件 ----------

/** 白卡容器（Web bg-white rounded-2xl border shadow-sm） */
@Composable
private fun CardShell(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

/** 圆角图标底衬（Web p-2 bg-彩色10% rounded-lg） */
@Composable
private fun IconBadge(
    icon: ImageVector,
    tint: Color,
    bgAlpha: Float = 0.1f
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(tint.copy(alpha = bgAlpha), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** 画像三统计格（Web grid-cols-3 圆角浅底小卡） */
@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkAppearance()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(
                if (isDark) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background,
                RoundedCornerShape(12.dp)
            )
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 统计面板三卡（待复习句子/最弱音素/已攻克音素，白卡 + 彩色图标） */
@Composable
private fun MiniStatCard(
    icon: ImageVector,
    tint: Color,
    label: String,
    value: String,
    mono: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(tint.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = if (mono) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontFamily = if (mono) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 深浅色判定（背景亮度反推，与 PlayerPalette.isDarkAppearance 同口径，本包内自持一份） */
@Composable
private fun isDarkAppearance(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f
