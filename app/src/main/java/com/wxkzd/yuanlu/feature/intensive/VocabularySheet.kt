package com.wxkzd.yuanlu.feature.intensive

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxkzd.yuanlu.feature.player.Ink300
import com.wxkzd.yuanlu.feature.player.Ink400
import com.wxkzd.yuanlu.feature.player.Ink600
import com.wxkzd.yuanlu.feature.player.Ink700
import com.wxkzd.yuanlu.feature.player.Ink800
import com.wxkzd.yuanlu.feature.player.Ink900
import com.wxkzd.yuanlu.feature.player.Ink50
import com.wxkzd.yuanlu.feature.player.Primary400
import com.wxkzd.yuanlu.feature.player.Primary600
import com.wxkzd.yuanlu.feature.player.isDarkAppearance

/**
 * 查词弹层（复刻 Web VocabularyModal 移动端底部弹层形态）：
 * 单词 + 收藏/关闭 → 音标与英美发音 → 词性释义卡 → 词源记忆卡（默认收缩）→
 * 底栏「来源：剧集名 + 完成学习 →」。发音用独立 MediaPlayer 播放 OSS 音频，
 * 弹层关闭自动释放。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VocabularySheet(
    sheet: WordSheetState,
    episodeTitle: String?,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkAppearance()
    val context = LocalContext.current
    val sheetBg = if (isDark) Ink900 else Color.White
    val titleColor = if (isDark) Color(0xFFD5EFE3) else Primary600
    val subColor = if (isDark) Ink400 else Ink600
    val cardBg = if (isDark) Ink800.copy(alpha = 0.4f) else Ink50
    val cardBorder = if (isDark) Ink700.copy(alpha = 0.4f) else Color(0xFFE5E0D5)

    // 发音播放器：随弹层生命周期创建/释放
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            if (player.isPlaying) player.stop()
            player.release()
        }
    }
    fun playAudio(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            player.reset()
            player.setDataSource(url)
            player.prepare()
            player.start()
        } catch (e: Exception) {
            // OSS 音频过期等异常静默忽略
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        containerColor = sheetBg,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            // ---- 单词行：收藏 + 关闭 ----
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = sheet.word,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = titleColor,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = if (sheet.isSaved) "已在生词本中" else "保存生词"
                        ) { onSave() },
                    contentAlignment = Alignment.Center
                ) {
                    if (sheet.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary600)
                    } else {
                        Icon(
                            imageVector = if (sheet.isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (sheet.isSaved) "已在生词本中" else "保存生词",
                            tint = if (sheet.isSaved) Primary600 else subColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClickLabel = "关闭") { onClose() },
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

            // ---- 加载态 / 音标 + 发音 / 释义 ----
            if (sheet.isLoading) {
                Row(
                    modifier = Modifier.padding(vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Primary600
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "请耐心等待，正在查询词典…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = subColor
                    )
                }
            } else {
                val entry = sheet.entry
                if (entry == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无词典数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = subColor
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(10.dp))
                    // 音标 + 英美发音
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        entry.phoneticsUk?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = subColor)
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "播放发音 (UK)",
                                tint = Primary600,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { playAudio(entry.audioUk) }
                            )
                        }
                        if (entry.phoneticsUs != null && entry.phoneticsUs != entry.phoneticsUk) {
                            Text(text = "|", style = MaterialTheme.typography.bodySmall, color = subColor.copy(alpha = 0.4f))
                            Text(text = entry.phoneticsUs, style = MaterialTheme.typography.bodySmall, color = subColor)
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = "播放发音 (US)",
                                tint = Primary600,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .clickable { playAudio(entry.audioUs) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 词性与释义卡
                    if (entry.definitions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(cardBg)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = "词性与释义",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                color = subColor.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            entry.definitions.forEach { def ->
                                Row {
                                    Text(
                                        text = "[${def.pos}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = titleColor
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = def.meaningCn,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isDark) Color(0xFFE8E3D9) else Ink800,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                def.meaningEn?.let { en ->
                                    Text(
                                        text = en,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = subColor.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(start = 44.dp, top = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // 词源记忆卡（默认收缩，点击展开）
                    entry.etymology?.let { ety ->
                        if (ety.prefix != null || ety.root != null || ety.suffix != null ||
                            !ety.breakdown.isNullOrBlank() || !ety.mnemonic.isNullOrBlank()
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))
                            EtymologyCard(ety = ety, isDark = isDark)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ---- 底栏：来源 + 完成学习 ----
            HorizontalDivider(color = cardBorder)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = episodeTitle?.let { "来源：$it" } ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = subColor.copy(alpha = 0.5f),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "完成学习 →",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Primary400 else Primary600,
                    modifier = Modifier.clickable(onClickLabel = "完成学习") { onClose() }
                )
            }
            Spacer(modifier = Modifier.navigationBarsSpacer())
        }
    }
}

/** 词源记忆卡：前缀/词根/后缀 chips + 拆解 + 记忆技巧（默认收缩，Web 同款交互） */
@Composable
private fun EtymologyCard(
    ety: com.wxkzd.yuanlu.domain.model.DictEtymology,
    isDark: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val container = if (isDark) Color(0x33EDF7F2) else Color(0xCCEDF7F2)
    val labelColor = if (isDark) Primary400.copy(alpha = 0.7f) else Primary600.copy(alpha = 0.6f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "词源记忆") { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧬 词源记忆",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = labelColor,
                modifier = Modifier.weight(1f)
            )
            if (!expanded) {
                (ety.root ?: ety.prefix ?: ety.suffix)?.let { preview ->
                    Text(
                        text = preview,
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        maxLines = 1
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = labelColor,
                modifier = Modifier.size(16.dp)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ety.prefix?.let {
                        EtymologyChip("前缀 · $it", isAmber = true, isDark = isDark)
                    }
                    ety.root?.let {
                        EtymologyChip("词根 · $it", isAmber = false, isDark = isDark)
                    }
                    ety.suffix?.let {
                        EtymologyChip("后缀 · $it", isAmber = false, isNeutral = true, isDark = isDark)
                    }
                }
                ety.breakdown?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Ink300 else Ink700
                    )
                }
                ety.mnemonic?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 记忆技巧：$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDark) Ink400 else Ink600
                    )
                }
            }
        }
    }
}

@Composable
private fun EtymologyChip(text: String, isDark: Boolean, isAmber: Boolean = false, isNeutral: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = when {
            isAmber -> Color(0xFF92400E)
            isNeutral -> if (isDark) Ink300 else Ink600
            else -> if (isDark) Primary400 else Primary600
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isAmber -> Color(0xFFFEF3C7)
                    isNeutral -> if (isDark) Ink800 else Ink50
                    else -> if (isDark) Primary600.copy(alpha = 0.15f) else Color(0xFFD5EDE1)
                }
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/** 底部手势区留白（弹层内容避让） */
private fun Modifier.navigationBarsSpacer(): Modifier =
    this.then(Modifier.padding(bottom = 12.dp))
