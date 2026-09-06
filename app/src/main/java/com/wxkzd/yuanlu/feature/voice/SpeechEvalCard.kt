package com.wxkzd.yuanlu.feature.voice

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.domain.model.PracticeTextMode
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.ui.components.MicIcon

/**
 * 单句评测卡（复刻 Web SpeechEvaluationCard）：
 * 录音准备态（三播放钮 + 字幕区 + 大录音钮）⇄ 结果态（环形得分 + 逐词纠错 + 音素诊断），
 * 两态之间以水平轴翻转动画切换；顶部操作栏右侧提供"最近得分"历史入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeechEvalCard(
    state: SpeechEvalUiState,
    onToggleRecording: () -> Unit,
    onAiReading: () -> Unit,
    onPlayOriginal: (Float) -> Unit,
    onPlayUserAudio: () -> Unit,
    onRetryRecording: () -> Unit,
    onSelectWord: (Int?) -> Unit,
    onPlayDictVoice: (String, Boolean) -> Unit,
    onPlayWordOriginal: (String) -> Unit,
    onPlayWordMe: (Int) -> Unit,
    onPrefetchIpa: (Subtitle) -> Unit = {},
    onToggleBlindReveal: () -> Unit = {},
    onShowLatestScore: () -> Unit = {}
) {
    val subtitle = state.current ?: return

    // 音标模式进入时预取当前句音标
    LaunchedEffect(subtitle.id, state.settings.textMode) {
        onPrefetchIpa(subtitle)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            // ---- 顶部操作栏：AI 朗读 / 原声播放 / 慢速播放 + 最近得分（居右，仅有历史得分时显示） ----
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CircleActionIconButton(
                    icon = Icons.Filled.GraphicEq,
                    label = "AI朗读",
                    active = state.playing == PlaybackKind.AI,
                    enabled = state.phase != EvalPhase.EVALUATING,
                    onClick = onAiReading
                )
                CircleActionIconButton(
                    icon = Icons.Filled.VolumeUp,
                    label = "原声播放",
                    active = state.playing == PlaybackKind.ORIGINAL,
                    enabled = state.phase != EvalPhase.EVALUATING,
                    onClick = { onPlayOriginal(1f) }
                )
                CircleActionIconButton(
                    icon = Icons.Filled.SlowMotionVideo,
                    label = "慢速播放",
                    active = state.playing == PlaybackKind.SLOW,
                    enabled = state.phase != EvalPhase.EVALUATING,
                    onClick = { onPlayOriginal(0.75f) }
                )
                if (state.latestRecord != null) {
                    Spacer(modifier = Modifier.weight(1f))
                    CircleActionIconButton(
                        icon = Icons.Filled.Leaderboard,
                        label = "最近得分",
                        active = false,
                        enabled = state.phase != EvalPhase.EVALUATING && state.phase != EvalPhase.RECORDING,
                        onClick = onShowLatestScore
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- 字幕区 ----
            SubtitleSection(
                subtitle = subtitle,
                settings = state.settings,
                ipaCache = state.ipaCache,
                revealed = state.blindRevealed,
                onToggleReveal = onToggleBlindReveal
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ---- 录音区 ⇄ 结果区：水平轴卡片翻转（评测完成 / 点"最近得分"翻至结果面） ----
            val isResultPhase = state.phase == EvalPhase.RESULT
            val flip = remember { Animatable(if (isResultPhase) 180f else 0f) }
            LaunchedEffect(isResultPhase) {
                flip.animateTo(if (isResultPhase) 180f else 0f, animationSpec = tween(360))
            }
            // 回翻时 result 已清空，缓存最近一次非空结果避免翻完前闪现 0 分占位
            var lastResult by remember { mutableStateOf<SpeechEvalResult?>(null) }
            state.result?.let { lastResult = it }
            // 正面固定展示录音态：翻至结果面后 phase 已变 RESULT，保留翻起前的录音态画面
            var frontPhase by remember { mutableStateOf(EvalPhase.IDLE) }
            if (state.phase != EvalPhase.RESULT) frontPhase = state.phase
            val frontVisible = flip.value < 90f
            Box(
                modifier = Modifier.graphicsLayer {
                    rotationX = if (frontVisible) flip.value else flip.value - 180f
                    cameraDistance = 16.dp.toPx()
                }
            ) {
                if (frontVisible) {
                    RecordingSection(
                        phase = frontPhase,
                        amplitudes = state.amplitudes,
                        onToggleRecording = onToggleRecording
                    )
                } else {
                    ResultSection(
                        state = state,
                        result = lastResult ?: SpeechEvalResult(0, 0, 0, 0, 0, emptyList()),
                        onPlayUserAudio = onPlayUserAudio,
                        onRetryRecording = onRetryRecording,
                        onSelectWord = onSelectWord,
                        onPlayDictVoice = onPlayDictVoice,
                        onPlayWordOriginal = onPlayWordOriginal,
                        onPlayWordMe = onPlayWordMe
                    )
                }
            }
        }
    }
}

// ---------- 字幕区（原文 / 音标 / 盲读 三模式 + 句末翻译图标 + 中文翻译 + 盲读切换） ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleSection(
    subtitle: Subtitle,
    settings: com.wxkzd.yuanlu.domain.model.PracticeSettings,
    ipaCache: Map<String, String>,
    revealed: Boolean,
    onToggleReveal: () -> Unit
) {
    val englishSize = when (settings.fontSizeLevel) {
        0 -> 17.sp
        2 -> 24.sp
        else -> 20.sp
    }
    // 句末"文/A"图标的本地覆盖：null = 跟随设置；换句或设置开关变化时回归默认（Web mobileCnOverride 同口径）
    var translationOverride by remember(subtitle.id, settings.showTranslation) { mutableStateOf<Boolean?>(null) }
    val showCn = translationOverride ?: settings.showTranslation
    val blindMasked = settings.textMode == PracticeTextMode.BLIND && !revealed

    Column {
        when {
            blindMasked -> {
                // 盲读：按词长生成模糊条，句末附翻译图标
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    subtitle.textEn.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                        Box(
                            modifier = Modifier
                                .width((englishSize.value * 0.62f * word.length.coerceAtLeast(2)).dp)
                                .height(englishSize.value.dp + 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
                        )
                    }
                    TranslateToggleButton(showCn = showCn, onClick = { translationOverride = !showCn })
                }
            }
            settings.textMode == PracticeTextMode.IPA -> {
                // 音标：逐词替换为词典 US 音标（未命中回退原词），句末附翻译图标
                val parts = subtitle.textEn.split(Regex("\\s+")).filter { it.isNotBlank() }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parts.forEach { word ->
                        val rendered = ipaCache[word.trim { !it.isLetter() && it != '\'' }.lowercase()] ?: word
                        Text(
                            text = rendered,
                            fontSize = englishSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TranslateToggleButton(showCn = showCn, onClick = { translationOverride = !showCn })
                }
            }
            else -> {
                // 原文：逐词流式排版，句末附翻译图标
                val parts = subtitle.textEn.split(Regex("\\s+")).filter { it.isNotBlank() }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parts.forEach { word ->
                        Text(
                            text = word,
                            fontSize = englishSize,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    TranslateToggleButton(showCn = showCn, onClick = { translationOverride = !showCn })
                }
            }
        }

        // 中文翻译：只看 showCn（盲读模式下同样展示，对齐 Web；不再被盲读模式屏蔽）
        if (showCn && !subtitle.textCn.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle.textCn,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (showCn && subtitle.textCn.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "（本句暂无中文翻译）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }

        // 盲读模式：显示原文 / 重新遮挡 切换（Web visibility pill 同款）
        if (settings.textMode == PracticeTextMode.BLIND) {
            BlindRevealPill(masked = blindMasked, onClick = onToggleReveal)
        }
    }
}

/** 句末"文/A"翻译切换图标（Web Languages 按钮）：点亮=翻译可见，点击临时开/关中文翻译 */
@Composable
private fun TranslateToggleButton(showCn: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(start = 2.dp, top = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Translate,
            contentDescription = if (showCn) "隐藏翻译" else "显示翻译",
            tint = if (showCn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 盲读模式的"显示原文 / 重新遮挡"胶囊按钮 */
@Composable
private fun BlindRevealPill(masked: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = if (masked) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = if (masked) "显示原文" else "重新遮挡",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ---------- 录音区（大麦克风按钮 + 音量动效 + 评测中） ----------

@Composable
private fun RecordingSection(
    phase: EvalPhase,
    amplitudes: List<Int>,
    onToggleRecording: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        when (phase) {
            EvalPhase.EVALUATING -> {
                Spacer(modifier = Modifier.height(24.dp))
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "评测中…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
            EvalPhase.RECORDING -> {
                Spacer(modifier = Modifier.height(20.dp))
                AmplitudeBars(amplitudes = amplitudes)
                Spacer(modifier = Modifier.height(20.dp))
                PulsingRecordButton(recording = true, onClick = onToggleRecording)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击停止并评测",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                Spacer(modifier = Modifier.height(12.dp))
                PulsingRecordButton(recording = false, onClick = onToggleRecording)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击录音",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "朗读上方句子，跟读练习发音",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

/** 录音中波纹呼吸动画的停止钮 / 待录状态的静态麦克风钮 */
@Composable
private fun PulsingRecordButton(recording: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "recordPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(contentAlignment = Alignment.Center) {
        if (recording) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(pulse)
                    .background(
                        MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                        CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (recording) {
                Icon(
                    imageVector = Icons.Filled.Stop,
                    contentDescription = "停止录音",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(
                    imageVector = MicIcon,
                    contentDescription = "录音",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp)
                )
            }
        }
    }
}

/** 录音音量条（滚动窗口，高度按 0..100 归一化） */
@Composable
private fun AmplitudeBars(amplitudes: List<Int>) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.height(44.dp)
    ) {
        val bars = if (amplitudes.isEmpty()) List(24) { 6 } else amplitudes
        bars.takeLast(24).forEach { amp ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((10 + amp * 0.34f).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.75f))
            )
        }
    }
}

// ---------- 结果区 ----------

@Composable
private fun ResultSection(
    state: SpeechEvalUiState,
    result: SpeechEvalResult,
    onPlayUserAudio: () -> Unit,
    onRetryRecording: () -> Unit,
    onSelectWord: (Int?) -> Unit,
    onPlayDictVoice: (String, Boolean) -> Unit,
    onPlayWordOriginal: (String) -> Unit,
    onPlayWordMe: (Int) -> Unit
) {
    val score = result.overallScore
    val threshold = state.effectiveThreshold

    Column {
        // ---- 综合得分：环形 + 评价 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score = score, size = 104.dp, stroke = 10.dp)
            Spacer(modifier = Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        score >= threshold -> "Excellent!"
                        score >= 60 -> "Good Job!"
                        else -> "Keep Trying!"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor(score)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (score >= threshold) {
                        "已过关（≥${threshold}），发音很棒！"
                    } else {
                        "继续练习，达到 ${threshold} 分即可过关"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                ScoreMetricBar("准确度", result.pronunciation, MaterialTheme.colorScheme.primary)
                ScoreMetricBar("流利度", result.fluency, MaterialTheme.colorScheme.tertiary)
                ScoreMetricBar("完整度", result.integrity, MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // ---- 逐词纠错：回放我的发音 + 单词流式 ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "逐词纠错",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onPlayUserAudio,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (state.playing == PlaybackKind.USER) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("回放我的发音", style = MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (result.words.isEmpty()) {
            Text(
                text = "本句未返回逐词明细",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 盲读未揭示：逐词 chip 同样遮挡（Web isBlindMasked 同口径）
            val blindMasked = state.settings.textMode == PracticeTextMode.BLIND && !state.blindRevealed
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                result.words.forEachIndexed { index, word ->
                    if (blindMasked) {
                        MaskedWordChip(wordLength = word.word.length)
                    } else {
                        val clickable = word.score < 85 && word.phonemes.isNotEmpty()
                        WordChip(
                            word = word.word,
                            score = word.score,
                            selected = state.selectedWordIndex == index,
                            enabled = clickable,
                            onClick = {
                                if (clickable) {
                                    onSelectWord(if (state.selectedWordIndex == index) null else index)
                                }
                            }
                        )
                    }
                }
            }
            Text(
                text = "点击橙色/红色单词查看音素诊断",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // ---- 音素诊断面板 ----
        val selected = state.selectedWordIndex?.let { result.words.getOrNull(it) }
        if (selected != null && selected.phonemes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            PhonemePanel(
                word = selected.word,
                phonemes = selected.phonemes,
                showIpa = state.settings.showIpa,
                playing = state.playing,
                onPlayDictVoice = onPlayDictVoice,
                onPlayWordOriginal = { onPlayWordOriginal(selected.word) },
                onPlayWordMe = { state.selectedWordIndex?.let { onPlayWordMe(it) } }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        OutlinedButton(
            onClick = onRetryRecording,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("再试一次")
        }
    }
}

// ---------- 环形得分（radial-progress 的 Canvas 等价） ----------

@Composable
private fun ScoreRing(score: Int, size: Dp, stroke: Dp) {
    val color = scoreColor(score)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(size)) {
            val strokePx = stroke.toPx()
            val inset = strokePx / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - strokePx, this.size.height - strokePx)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * (score.coerceIn(0, 100) / 100f),
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = "综合得分",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------- 三维指标条（准确度/流利度/完整度） ----------

@Composable
private fun ScoreMetricBar(label: String, value: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(38.dp)
        )
        LinearProgressIndicator(
            progress = { value.coerceIn(0, 100) / 100f },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            modifier = Modifier
                .weight(1f)
                .height(5.dp)
        )
        Text(
            text = "$value",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(26.dp),
            textAlign = TextAlign.End
        )
    }
}

// ---------- 盲读遮罩 chip（按词宽占位，样式对齐字幕区模糊条） ----------

@Composable
private fun MaskedWordChip(wordLength: Int) {
    Box(
        modifier = Modifier
            .width(8.dp * wordLength.coerceAtLeast(2) + 20.dp)
            .height(32.dp)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f))
        )
    }
}

// ---------- 逐词 chip（85/60 双分界，对齐 Web getWordColorClass） ----------

@Composable
private fun WordChip(
    word: String,
    score: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val (bg, fg, border) = when {
        score >= 85 -> Triple(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.onSurface,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
        score >= 60 -> Triple(
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f),
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
        )
        else -> Triple(
            MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
            MaterialTheme.colorScheme.error,
            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        )
    }
    Text(
        text = word,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .border(
                if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                else BorderStroke(1.dp, border),
                RoundedCornerShape(8.dp)
            )
            .let { base -> if (enabled) base.clickable { onClick() } else base }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ---------- 音素诊断面板（美音/英音/原声/我 + 音素得分流） ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhonemePanel(
    word: String,
    phonemes: List<com.wxkzd.yuanlu.domain.model.SpeechEvalPhoneme>,
    showIpa: Boolean,
    playing: PlaybackKind,
    onPlayDictVoice: (String, Boolean) -> Unit,
    onPlayWordOriginal: () -> Unit,
    onPlayWordMe: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "音素诊断",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = word,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // 发音对比四路
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PronunciationPill("美音", playing == PlaybackKind.WORD_US) { onPlayDictVoice(word, true) }
                PronunciationPill("英音", playing == PlaybackKind.WORD_UK) { onPlayDictVoice(word, false) }
                PronunciationPill("原声", playing == PlaybackKind.WORD_ORIGINAL) { onPlayWordOriginal() }
                PronunciationPill("我", playing == PlaybackKind.WORD_ME) { onPlayWordMe() }
            }

            if (showIpa) {
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    phonemes.forEach { phoneme ->
                        val good = phoneme.score >= 80
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(
                                    if (good) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (good) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "/${phoneme.phoneme}/",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "${phoneme.score}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (good) FontWeight.Normal else FontWeight.Bold,
                                color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PronunciationPill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

// ---------- 顶部三圆钮 ----------

@Composable
private fun CircleActionIconButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                )
                .border(
                    1.dp,
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    CircleShape
                )
                .let { base -> if (enabled) base.clickable { onClick() } else base }
                .padding(0.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 总分/评价颜色（85/60 双分界，对齐 Web getScoreColor） */
@Composable
internal fun scoreColor(score: Int): Color = when {
    score >= 85 -> MaterialTheme.colorScheme.primary
    score >= 60 -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.error
}
