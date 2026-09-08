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
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.domain.model.PracticeTextMode
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.model.SubtitleWord
import com.wxkzd.yuanlu.feature.player.Accent100
import com.wxkzd.yuanlu.feature.player.Accent300
import com.wxkzd.yuanlu.feature.player.Accent700
import com.wxkzd.yuanlu.feature.player.Accent900
import com.wxkzd.yuanlu.feature.player.isDarkAppearance
import com.wxkzd.yuanlu.ui.components.MicIcon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 单句评测卡（复刻 Web SpeechEvaluationCard）：
 * 录音准备态（三播放钮 + 字幕区 + 大录音钮）⇄ 结果态（环形得分 + 逐词纠错 + 音素诊断），
 * 两态之间以水平轴翻转动画切换；顶部操作栏右侧提供"最近得分"历史入口。
 *
 * 字幕区英文原句为可交互文本：点词查词（共享 VocabularySheet + WordLookupController，
 * 与精听页一致）；原声/慢速播放时按词级时间戳扫光高亮（AI 朗读不触发——
 * highlightPositionMs 由 ViewModel 仅在 ORIGINAL/SLOW 播放时发值）。
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
    onShowLatestScore: () -> Unit = {},
    /** 原声/慢速播放的实时进度（null/未传入 = 不点亮扫光） */
    highlightPositionMs: StateFlow<Long?>? = null,
    /** 已保存生词集合（句内已保存词 primary 标色；未传入不标色） */
    savedWords: StateFlow<Set<String>?>? = null,
    /** 点词查词回调（word 为带标点原词，由查词入口统一清洗） */
    onWordClick: ((word: String, timestampSec: Double) -> Unit)? = null
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
                highlightPositionMs = highlightPositionMs,
                savedWords = savedWords,
                onWordClick = onWordClick,
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

// ---------- 字幕区（原文 / 音标 / 盲读 三模式 + 点词查词 + 词级扫光 + 盲读切换） ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleSection(
    subtitle: Subtitle,
    settings: com.wxkzd.yuanlu.domain.model.PracticeSettings,
    ipaCache: Map<String, String>,
    revealed: Boolean,
    highlightPositionMs: StateFlow<Long?>?,
    savedWords: StateFlow<Set<String>?>?,
    onWordClick: ((word: String, timestampSec: Double) -> Unit)?,
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
    // 进度与生词集合在句内订阅：50ms 进度 tick 只重组句子文本，不惊动整卡
    val highlightFallback = remember { MutableStateFlow<Long?>(null) }
    val savedFallback = remember { MutableStateFlow<Set<String>?>(null) }
    val posMs by (highlightPositionMs ?: highlightFallback).collectAsStateWithLifecycle()
    val saved by (savedWords ?: savedFallback).collectAsStateWithLifecycle()

    Column {
        when {
            blindMasked -> {
                // 盲读：按词长生成模糊条（不可点查词），句末附翻译图标
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
            else -> InteractiveSentenceText(
                subtitle = subtitle,
                fontSize = englishSize,
                ipaMap = if (settings.textMode == PracticeTextMode.IPA) ipaCache else null,
                sweepSec = posMs?.div(1000.0),
                savedWords = saved,
                onWordClick = onWordClick,
                showCn = showCn,
                onToggleCn = { translationOverride = !showCn }
            )
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

/** 句内可交互 token：展示文本（音标模式为音标）、原词（查词入参）、字符区间、词级时间戳 */
private data class SentenceToken(
    val display: String,
    val word: String,
    val start: Int,
    val end: Int,
    val timeStart: Double
)

/** 单词扫光状态：背景光斑 alpha（0..1）与已读前景插值（0..1，对齐 Web useWordHighlight） */
private data class WordSweepPaint(val bgAlpha: Float, val readAlpha: Float)

/** 查词/音标键：去除词上标点后小写（与音标预取、生词集合口径一致） */
private fun cleanWordKey(word: String): String =
    word.trim { !it.isLetter() && it != '\'' }.lowercase()

/**
 * 分词并记录字符区间（"单词 + 单空格"拼接文本口径，与精听 srtWordTokens 一致）：
 * 有词级时间戳 → 逐词绑定 start/end（可扫光可点）；无 → 按空白分词兜底
 * （仅点词查词，时间戳退化为句起点）。音标模式展示文本替换为词典 US 音标。
 */
private fun buildSentenceTokens(
    subtitle: Subtitle,
    words: List<SubtitleWord>,
    ipaMap: Map<String, String>?
): List<SentenceToken> {
    val tokens = mutableListOf<SentenceToken>()
    var cursor = 0
    fun push(rawWord: String, timeStart: Double) {
        val display = if (ipaMap != null) ipaMap[cleanWordKey(rawWord)] ?: rawWord else rawWord
        tokens.add(SentenceToken(display, rawWord, cursor, cursor + display.length - 1, timeStart))
        cursor += display.length + 1 // 词后拼一个空格
    }
    if (words.isNotEmpty()) {
        words.forEach { push(it.word, it.start) }
    } else {
        subtitle.textEn.split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { push(it, subtitle.start) }
    }
    return tokens
}

/**
 * 词级扫光（对齐 Web useWordHighlight 全量口径）：
 * - 当前朗读词背景光斑全亮；已读词前景转 accent；未读保持基础色；
 * - 词间间隙内线性插值交叉过渡——prev 光斑 1→0、next 光斑 0→1，prev 前景同步
 *   primary→accent；间隙越长过渡越慢（停顿/慢速天然自适应）；
 * - 整句读完全部转已读色；播放起点之前全暗。
 */
private fun sweepPaints(words: List<SubtitleWord>, t: Double): List<WordSweepPaint> {
    val n = words.size
    if (n == 0) return emptyList()
    val start = words.first().start
    val end = words.last().end
    if (t < start) return List(n) { WordSweepPaint(0f, 0f) }
    if (t >= end) return List(n) { WordSweepPaint(0f, 1f) }
    val current = words.indexOfFirst { t >= it.start && t <= it.end }
    if (current >= 0) {
        return List(n) { i ->
            when {
                i < current -> WordSweepPaint(0f, 1f)
                i == current -> WordSweepPaint(1f, 0f)
                else -> WordSweepPaint(0f, 0f)
            }
        }
    }
    // 词间间隙：已读部分（< next）转 accent，prev/next 光斑交叉过渡
    var prev = -1
    var next = n
    words.forEachIndexed { i, w ->
        if (w.end < t) prev = i
        if (w.start > t && next == n) next = i
    }
    val paints = MutableList(n) { i -> if (i < next) WordSweepPaint(0f, 1f) else WordSweepPaint(0f, 0f) }
    if (prev >= 0 && next < n) {
        val gap = words[next].start - words[prev].end
        if (gap > 0) {
            val p = ((t - words[prev].end) / gap).coerceIn(0.0, 1.0).toFloat()
            paints[prev] = WordSweepPaint(1f - p, p)
            paints[next] = WordSweepPaint(p, 0f)
        }
    }
    return paints
}

/** 句末内联翻译图标的占位 id */
private const val TranslateToggleTag = "translateToggle"

/**
 * 可交互英文原句（normal/ipa 模式共用）：
 * - 点词查词：AnnotatedString 词区间 + pointerInput 命中（精听 SubtitleRow 同款机制）；
 * - 词级扫光：sweepSec 非 null（原声/慢速播放中）时逐词应用 [sweepPaints]；
 * - 已保存生词 primary 标色（Web globalVocabWords 同语义）；
 * - 句末内联"文/A"翻译切换图标（随文本换行流动）。
 */
@Composable
private fun InteractiveSentenceText(
    subtitle: Subtitle,
    fontSize: TextUnit,
    ipaMap: Map<String, String>?,
    sweepSec: Double?,
    savedWords: Set<String>?,
    onWordClick: ((word: String, timestampSec: Double) -> Unit)?,
    showCn: Boolean,
    onToggleCn: () -> Unit
) {
    val isDark = isDarkAppearance()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val words = subtitle.words.orEmpty()
    val tokens = remember(subtitle.id, ipaMap) { buildSentenceTokens(subtitle, words, ipaMap) }
    val currentTokens by rememberUpdatedState(tokens)

    val paints = if (sweepSec != null && words.isNotEmpty()) sweepPaints(words, sweepSec) else null

    // 扫光配色（与精听/Web 同源）：浅色光斑 accent-100、深色 accent-900（40% 上限）；
    // 已读前景 accent-700 / accent-300
    val sweepBg = if (isDark) Accent900 else Accent100
    val sweepBgMaxAlpha = if (isDark) 0.4f else 0.9f
    val readColor = if (isDark) Accent300 else Accent700
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary

    val annotated: AnnotatedString = buildAnnotatedString {
        tokens.forEachIndexed { i, token ->
            val paint = paints?.getOrNull(i)
            val isSavedWord = savedWords?.contains(cleanWordKey(token.word)) == true
            val base = if (isSavedWord) primaryColor else onSurfaceColor
            val color = when {
                paint == null || paint.readAlpha <= 0.001f ->
                    if (isSavedWord) primaryColor else Color.Unspecified
                paint.readAlpha >= 0.999f -> readColor
                else -> lerp(base, readColor, paint.readAlpha)
            }
            val background = paint
                ?.takeIf { it.bgAlpha > 0.001f }
                ?.let { sweepBg.copy(alpha = sweepBgMaxAlpha * it.bgAlpha) }
                ?: Color.Transparent
            withStyle(SpanStyle(color = color, background = background)) { append(token.display) }
            if (i != tokens.lastIndex) append(' ')
        }
        appendInlineContent(TranslateToggleTag, "译")
    }

    Text(
        text = annotated,
        onTextLayout = { textLayout = it },
        inlineContent = mapOf(
            TranslateToggleTag to InlineTextContent(
                Placeholder(1.8.em, 1.8.em, PlaceholderVerticalAlign.TextCenter)
            ) {
                TranslateToggleButton(showCn = showCn, onClick = onToggleCn)
            }
        ),
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        lineHeight = (fontSize.value * 1.5f).sp,
        color = onSurfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(subtitle.id, tokens) {
                if (onWordClick == null) return@pointerInput
                detectTapGestures { pos ->
                    val layout = textLayout ?: return@detectTapGestures
                    val offset = layout.getOffsetForPosition(pos)
                    currentTokens.firstOrNull { offset >= it.start && offset <= it.end + 1 }
                        ?.let { onWordClick(it.word, it.timeStart) }
                }
            }
    )
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
