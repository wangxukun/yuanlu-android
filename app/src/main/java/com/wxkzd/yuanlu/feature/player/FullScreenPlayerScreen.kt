package com.wxkzd.yuanlu.feature.player

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.wxkzd.yuanlu.core.media.LoopMode
import com.wxkzd.yuanlu.core.media.PlayerState
import com.wxkzd.yuanlu.core.media.describe
import com.wxkzd.yuanlu.core.media.SleepConfig
import com.wxkzd.yuanlu.ui.components.CoverImage
import com.wxkzd.yuanlu.ui.components.formatMillis
import kotlin.math.roundToInt

/** 倍速展示：1 → "1x"、1.25 → "1.25x"（对齐 Web 端按钮文案） */
internal fun playbackRateLabel(rate: Float): String =
    if (rate % 1f == 0f) "${rate.toInt()}x" else "${rate}x"

/**
 * 全屏播放器——布局参照截图（模块下沉、页面上部预留评论区滚动空间），
 * 风格沿用 yuanlu Web 移动端（浅色 base-100 / 深色 ink-950 + 远青主色）：
 * - 顶部窄栏（expand_more 收起 / close 关闭停止）之后接弹性留白区，
 *   将来互动讨论评论在此滚动展示；
 * - 下沉模块：拖把 → 16:9 封面（点击跳剧集详情）→ 标题/播客名 →
 *   「播放列表 | 定时关闭闹钟」行（进度条正上方）→ 进度条 + 时间 → 控制排；
 * - 「精听模式」橙色胶囊固定在整个页面最底部；
 * - 点击闹钟弹出「定时」底部弹层（按时间 / 按集数 / 播完本集，复刻截图2）；
 * - 深浅色随外观设置切换。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FullScreenPlayerScreen(
    state: PlayerState,
    onCollapse: () -> Unit,
    onClosePlayer: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onOpenIntensive: (episodeId: String, playbackPositionMs: Long) -> Unit,
    onCyclePlaybackRate: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onApplySleepConfig: (SleepConfig) -> Unit,
    onCancelSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val episode = state.currentEpisode ?: return
    val context = LocalContext.current
    BackHandler(onBack = onCollapse)
    var sleepSheetOpen by remember { mutableStateOf(false) }

    val isDark = isDarkAppearance()
    val sheetBg = if (isDark) Ink950 else Color.White
    val headerDivider = if (isDark) Ink800 else Color(0xFFF2EFE8)
    val handleColor = if (isDark) Ink700 else Ink200
    val titleColor = if (isDark) Ink50 else Ink900
    val podcastColor = if (isDark) Primary400 else Primary600
    val trackColor = if (isDark) Ink800 else Ink100
    val timesColor = Ink400
    val chipBg = if (isDark) Ink800 else Ink100
    val chipText = if (isDark) Ink300 else Ink600
    val iconTint = if (isDark) Ink300 else Ink600

    val isBuffering = state.playbackState == Player.STATE_BUFFERING
    val duration = state.duration.coerceAtLeast(0L)
    val naturalFraction = if (duration > 0) {
        (state.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    } else 0f
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = (dragFraction ?: naturalFraction).coerceIn(0f, 1f)
    val displayMs = (fraction * duration).toLong()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(sheetBg)
            .statusBarsPadding()
    ) {
        // ---- Slim Header（h-14）：expand_more 收起 / close 关闭 ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "收起播放器") { onCollapse() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "收起播放器",
                    tint = if (isDark) Ink400 else Ink500,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "关闭播放器") {
                        onCollapse()
                        onClosePlayer()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭播放器",
                    tint = if (isDark) Ink500 else Ink400,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = headerDivider)

        // ---- 上部弹性留白：互动讨论评论区（预留），有评论时替换为可滚动列表 ----
        Spacer(modifier = Modifier.weight(1f).heightIn(min = 24.dp))

        // ---- 下沉模块：封面 → 标题/作者 → 播放列表/闹钟行 → 进度条 → 控制排 ----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 拖把（drag handle）
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .width(48.dp)
                    .height(6.dp)
                    .background(handleColor, RoundedCornerShape(50))
            )

            // 封面：16:9，最大宽 320dp，点击进入剧集详情
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 320.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, if (isDark) Ink800 else Ink100, RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "查看剧集详情") { onOpenEpisode(episode.episodeid) }
            ) {
                CoverImage(
                    url = episode.coverUrl,
                    contentDescription = episode.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 16.dp,
                    fallbackUrl = episode.coverFallbackUrl
                )
                if (state.isIntensiveMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Primary600.copy(alpha = 0.92f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "精听中",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 标题 / 播客名（居中）
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = titleColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = episode.podcastTitle ?: "远路播客",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = podcastColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 进度条正上方一行：左「播放列表」/ 右「定时关闭」闹钟 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "播放列表") {
                            Toast.makeText(context, "播放队列功能即将上线", Toast.LENGTH_SHORT).show()
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "播放列表",
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "播放列表",
                        style = MaterialTheme.typography.labelMedium,
                        color = iconTint
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "定时关闭") { sleepSheetOpen = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = "定时关闭",
                        tint = if (state.sleepTimer != null) {
                            if (isDark) Primary400 else Primary600
                        } else iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = state.sleepTimer?.label ?: "定时关闭",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (state.sleepTimer != null) {
                            if (isDark) Primary400 else Primary600
                        } else iconTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ---- 自绘进度条 + 双端时间（当前 / -剩余） ----
            WebStyleProgressBar(
                fraction = fraction,
                enabled = duration > 0,
                trackColor = trackColor,
                onDrag = { dragFraction = it },
                onCommit = {
                    onSeekTo((it * duration).toLong())
                    dragFraction = null
                },
                onCancel = { dragFraction = null }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMillis(displayMs),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = timesColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "-" + formatMillis((duration - displayMs).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = timesColor
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ---- 控制排：倍速 / 上一集 / 播放 / 下一集 / 循环 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(chipBg)
                        .clickable(onClickLabel = "切换播放速度") { onCyclePlaybackRate() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = playbackRateLabel(state.playbackRate),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = chipText
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "重新播放本集",
                        tint = if (isDark) Ink300 else Ink700,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onSeekTo(0L) }
                    )
                    BigPlayButton(
                        isPlaying = state.isPlaying,
                        isBuffering = isBuffering,
                        onTogglePlay = onTogglePlay
                    )
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "下一集",
                        tint = if (isDark) Ink300 else Ink700,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable {
                                Toast.makeText(context, "播放队列功能即将上线", Toast.LENGTH_SHORT).show()
                            }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val loopActive = state.loopMode != LoopMode.NONE
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                loopActive && isDark -> Primary900.copy(alpha = 0.4f)
                                loopActive -> Primary50
                                else -> chipBg
                            }
                        )
                        .clickable(onClickLabel = "切换循环模式") { onToggleLoopMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (state.loopMode == LoopMode.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                        contentDescription = "循环模式",
                        tint = when {
                            loopActive && isDark -> Primary400
                            loopActive -> Primary600
                            else -> chipText
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // ---- 精听模式入口：整个页面最底部 ----
            Row(
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Accent500)
                    .clickable(onClickLabel = "进入精听模式") {
                        onOpenIntensive(episode.episodeid, state.currentPosition)
                    }
                    .padding(horizontal = 22.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.GraphicEq,
                    contentDescription = "精听模式",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "精听模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }
        }
    }

    // ---- 「定时」底部弹层（复刻截图2） ----
    if (sleepSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sleepSheetOpen = false },
            containerColor = if (isDark) Ink900 else Color.White
        ) {
            SleepTimerSheet(
                state = state,
                onSelect = onApplySleepConfig,
                onCancel = onCancelSleepTimer,
                onClose = { sleepSheetOpen = false }
            )
        }
    }
}


/**
 * 「定时」弹层内容（复刻截图2 布局，yuanlu 风格）：
 * 上次定时（Switch 快捷重开/取消）→ 按时间（播完整集停止 + 15/30/60/90分/自定义）
 * → 按集数（本集/2集/3集/5集）→ 设置定时启播（占位）。
 */
@Composable
private fun SleepTimerSheet(
    state: PlayerState,
    onSelect: (SleepConfig) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isDarkAppearance()
    val cardBg = if (isDark) Ink800.copy(alpha = 0.5f) else Ink50
    val titleColor = if (isDark) Ink50 else Ink900
    val subColor = if (isDark) Ink400 else Ink500
    val dividerColor = if (isDark) Ink700 else Ink200
    val selected = state.sleepTimer?.config

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        // 标题行：「定时」+ 关闭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "定时",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "关闭定时弹层") { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = subColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .padding(16.dp)
        ) {
            // ---- 上次定时 + Switch（开=按上次配置重开，关=取消） ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "上次定时",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = titleColor
                    )
                    Text(
                        text = state.lastSleepConfig?.describe() ?: "未设置过定时",
                        style = MaterialTheme.typography.labelMedium,
                        color = subColor
                    )
                }
                Switch(
                    checked = state.sleepTimer != null,
                    onCheckedChange = { enable ->
                        if (enable) {
                            onSelect(state.lastSleepConfig ?: SleepConfig.Minutes(15))
                        } else {
                            onCancel()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = Primary600,
                        checkedThumbColor = Color.White
                    )
                )
            }

            HorizontalDivider(
                color = dividerColor,
                modifier = Modifier.padding(vertical = 14.dp)
            )

            // ---- 按时间 ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "按时间",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
                // 播完整集声音再停止（单选行）
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClickLabel = "播完整集声音再停止") {
                            onSelect(SleepConfig.EpisodeEnd)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selected == SleepConfig.EpisodeEnd) "●" else "○",
                        color = if (selected == SleepConfig.EpisodeEnd) {
                            if (isDark) Primary400 else Primary600
                        } else subColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "播完整集声音再停止",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected == SleepConfig.EpisodeEnd) {
                            if (isDark) Primary400 else Primary600
                        } else subColor
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("15分", "30分", "60分", "90分", "自定义").forEach { label ->
                    val minutes = label.removeSuffix("分").toIntOrNull()
                    val isSelected = minutes != null &&
                            selected is SleepConfig.Minutes && selected.minutes == minutes
                    SleepOptionChip(
                        text = label,
                        isSelected = isSelected,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (minutes != null) {
                                onSelect(SleepConfig.Minutes(minutes))
                            } else {
                                Toast.makeText(context, "自定义定时不支持，请选择常用时长", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- 按集数 ----
            Text(
                text = "按集数",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = titleColor
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(1, 2, 3, 5).forEach { count ->
                    val label = if (count == 1) "播完本集" else "播完${count}集"
                    val isSelected = selected is SleepConfig.Episodes && selected.count == count
                    SleepOptionChip(
                        text = label,
                        isSelected = isSelected,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(SleepConfig.Episodes(count)) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ---- 设置定时启播（占位入口） ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(cardBg)
                .clickable {
                    Toast.makeText(context, "定时启播功能即将上线", Toast.LENGTH_SHORT).show()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "设置定时启播",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
                Text(
                    text = "到点自动开始播放，不错过更新内容",
                    style = MaterialTheme.typography.labelMedium,
                    color = subColor
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = subColor
            )
        }
    }
}

/** 定时选项胶囊：选中态远青描边 + 浅底 */
@Composable
private fun SleepOptionChip(
    text: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedColor = if (isDark) Primary400 else Primary600
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    isSelected && isDark -> Primary900.copy(alpha = 0.4f)
                    isSelected -> Primary50
                    isDark -> Ink800
                    else -> Color.White
                }
            )
            .border(
                width = if (isSelected) 1.dp else 1.dp,
                color = if (isSelected) selectedColor else if (isDark) Ink700 else Ink200,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClickLabel = text) { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) selectedColor else if (isDark) Ink300 else Ink600,
            maxLines = 1
        )
    }
}

/**
 * 复刻 Web 展开播放器的进度条：6dp 圆角轨道 + primary-600 填充 + 16dp 白芯描边拖把；
 * 支持点按跳转与水平拖动（拖动中由父层展示临时时间）。
 */
@Composable
private fun WebStyleProgressBar(
    fraction: Float,
    enabled: Boolean,
    trackColor: Color,
    onDrag: (Float) -> Unit,
    onCommit: (Float) -> Unit,
    onCancel: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var latest = fraction
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        latest = (offset.x / size.width).coerceIn(0f, 1f)
                        onDrag(latest)
                    },
                    onHorizontalDrag = { change, _ ->
                        latest = (change.position.x / size.width).coerceIn(0f, 1f)
                        onDrag(latest)
                    },
                    onDragEnd = { onCommit(latest) },
                    onDragCancel = { onCancel() }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onCommit((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        // 轨道（垂直居中）
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(6.dp)
                .background(trackColor, RoundedCornerShape(50))
        )
        // 填充
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction = fraction.coerceIn(0f, 1f))
                .height(6.dp)
                .background(Primary600, RoundedCornerShape(50))
        )
        // 拖把：16dp 白芯 + 远青描边
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset {
                    val trackWidth = maxWidth.toPx() - 16.dp.toPx()
                    IntOffset(x = (fraction.coerceIn(0f, 1f) * trackWidth).roundToInt(), y = 0)
                }
                .size(16.dp)
                .background(Color.White, CircleShape)
                .border(2.dp, Primary600, CircleShape)
        )
    }
}

/** 64dp 主播放圆钮（Web：primary-600 + shadow + active:scale-90；缓冲中转圈） */
@Composable
private fun BigPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onTogglePlay: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(64.dp)
            .scale(if (pressed) 0.9f else 1f)
            .clip(CircleShape)
            .background(Primary600)
            .clickable(
                interactionSource = interactionSource,
                onClickLabel = "播放或暂停"
            ) { onTogglePlay() },
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = Color.White
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
