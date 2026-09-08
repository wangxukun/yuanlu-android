package com.wxkzd.yuanlu.feature.intensive

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.core.media.LoopMode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.feature.player.Accent100
import com.wxkzd.yuanlu.feature.player.Accent300
import com.wxkzd.yuanlu.feature.player.Accent700
import com.wxkzd.yuanlu.feature.player.Accent900
import com.wxkzd.yuanlu.feature.player.Ink400
import com.wxkzd.yuanlu.feature.player.Ink200
import com.wxkzd.yuanlu.feature.player.Ink300
import com.wxkzd.yuanlu.feature.player.Ink500
import com.wxkzd.yuanlu.feature.player.Ink50
import com.wxkzd.yuanlu.feature.player.Ink600
import com.wxkzd.yuanlu.feature.player.Ink800
import com.wxkzd.yuanlu.feature.player.Ink900
import com.wxkzd.yuanlu.feature.player.Ink950
import com.wxkzd.yuanlu.feature.player.Primary400
import com.wxkzd.yuanlu.feature.player.Primary50
import com.wxkzd.yuanlu.feature.player.Primary600
import com.wxkzd.yuanlu.feature.player.Primary900
import com.wxkzd.yuanlu.feature.player.isDarkAppearance
import com.wxkzd.yuanlu.feature.vocabulary.VocabularySheet
import com.wxkzd.yuanlu.ui.components.EmptyBox
import com.wxkzd.yuanlu.ui.components.ErrorBox
import com.wxkzd.yuanlu.ui.components.LoadingBox
import kotlinx.coroutines.delay

/** 迷你播放条内容高（2dp 进度线 + 60dp 行），随导航栏 inset 动态避让 */
private val MiniBarHeight = 63.dp

/**
 * 精听页路由入口：Nav3 entry 在此接参并触发加载（同剧集幂等）。
 * @param playbackPositionMs 由全屏播放器精听按钮携带的当前播放进度
 */
@Composable
fun IntensiveListeningRoute(
    episodeid: String,
    playbackPositionMs: Long,
    onBack: () -> Unit,
    viewModel: IntensiveListeningViewModel = hiltViewModel()
) {
    LaunchedEffect(episodeid) {
        viewModel.load(episodeid, playbackPositionMs)
    }
    IntensiveListeningScreen(onBack = onBack, viewModel = viewModel)
}

/**
 * 精听页——复刻 Web 端「精读/听写」InteractiveTranscript 全量交互：
 * - 顶栏「📖 精读 / ✍️ 听写」模式 Tab（听写：0.8 倍速 + 当前句自动循环 + 逐词拼写校验）；
 * - 句级：当前句卡片高亮、已读句前景色加深、未读句淡化；
 * - 词级（对齐 Web useWordHighlight）：当前句内已读词 accent 前景色、正在读词背景光斑；
 * - 点词查词：底部弹层（音标/发音/释义/词源）+ 收藏保存生词；
 * - 每句右下角单句循环；右下角浮动按钮：译文开关 + 单集循环/多集顺序；
 * - 底部播放控制由全局迷你播放条接管；深浅色随外观设置切换。
 */
@Composable
fun IntensiveListeningScreen(
    onBack: () -> Unit,
    viewModel: IntensiveListeningViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeSubtitleIndex.collectAsStateWithLifecycle()
    val wordSheet by viewModel.wordSheet.collectAsStateWithLifecycle()
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
            .background(if (isDarkAppearance()) Ink950 else Ink50)
            .statusBarsPadding()
    ) {
        when {
            uiState.isLoading -> LoadingBox()
            uiState.error != null -> ErrorBox(message = uiState.error!!, onRetry = viewModel::retry)
            uiState.episode == null -> EmptyBox("未找到该剧集")
            else -> {
                // ---- 顶栏：返回 / 精读·听写 Tab / 关闭（复刻 Web 精听头部） ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "返回",
                            tint = if (isDarkAppearance()) Ink400 else Ink500,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    ModeTabs(
                        mode = uiState.transcriptMode,
                        onSelect = viewModel::setTranscriptMode,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = if (isDarkAppearance()) Ink500 else Ink400,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // ---- 音频不可用（游客 3 分钟预览口径）提示 ----
                if (uiState.audioUrl.isNullOrBlank()) {
                    Text(
                        text = "登录后可收听完整内容，当前仅展示前 3 分钟字幕。",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkAppearance()) Ink400 else Ink600,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .background(
                                (if (isDarkAppearance()) Primary900 else Primary600).copy(alpha = 0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(12.dp)
                    )
                }

                // ---- 字幕列表 + 右下角浮动按钮 ----
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.subtitles.isEmpty()) {
                        EmptyBox("暂无字幕")
                    } else {
                        SubtitleList(
                            subtitles = uiState.subtitles,
                            activeIndex = activeIndex,
                            isPlaying = playerState.isPlaying,
                            positionSec = playerState.currentPosition / 1000.0,
                            showTranslation = uiState.showTranslation,
                            loopingIndex = uiState.loopingIndex,
                            dictationIndex = uiState.dictationIndex,
                            transcriptMode = uiState.transcriptMode,
                            seekEnabled = !uiState.audioUrl.isNullOrBlank(),
                            onSubtitleClick = viewModel::seekToSubtitle,
                            onToggleSentenceLoop = viewModel::toggleSentenceLoop,
                            onWordClick = viewModel::onWordClick,
                            onDictationSuccess = viewModel::onDictationSuccess
                        )
                    }

                    // 右下角浮动按钮列（对齐 Web quick actions）：随导航栏 inset 抬高，避开全局迷你条
                    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = navBarBottom + MiniBarHeight + 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FloatingAction(
                            icon = { tint ->
                                Icon(
                                    imageVector = Icons.Filled.Translate,
                                    contentDescription = if (uiState.showTranslation) "隐藏译文" else "显示译文",
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            active = uiState.showTranslation,
                            onClick = viewModel::toggleTranslation
                        )
                        val loopingEpisode = playerState.loopMode == LoopMode.ONE
                        FloatingAction(
                            icon = { tint ->
                                Icon(
                                    imageVector = if (loopingEpisode) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                                    contentDescription = if (loopingEpisode) "单集循环中，点击切换为多集顺序播放" else "多集顺序播放，点击切换为单集循环",
                                    tint = tint,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            active = loopingEpisode,
                            onClick = {
                                val willLoop = playerState.loopMode == LoopMode.NONE
                                viewModel.toggleEpisodeLoopMode()
                                Toast.makeText(
                                    context,
                                    if (willLoop) "单集循环" else "多集顺序播放",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // ---- 查词弹层（点词触发） ----
    wordSheet?.let { sheet ->
        VocabularySheet(
            sheet = sheet,
            episodeTitle = uiState.episode?.title,
            onSave = viewModel::saveCurrentWord,
            onClose = viewModel::closeWordSheet
        )
    }

    // ---- 听写完成结算弹层（末句拼写正确触发；循环已随 dictationFinished 解除） ----
    if (uiState.dictationFinished) {
        DictationFinishDialog(
            totalCount = uiState.subtitles.size,
            onRestart = viewModel::restartDictation,
            onExit = { viewModel.setTranscriptMode(TranscriptMode.READ) }
        )
    }
}

/** 听写完成结算：本轮句数统计 + 再来一轮 / 返回精读 */
@Composable
private fun DictationFinishDialog(
    totalCount: Int,
    onRestart: () -> Unit,
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onExit,
        title = { Text(text = "🎉 听写完成") },
        text = { Text(text = "已正确拼写全部 $totalCount 句，完成了一整轮听写！") },
        confirmButton = { TextButton(onClick = onRestart) { Text("再来一轮") } },
        dismissButton = { TextButton(onClick = onExit) { Text("返回精读") } }
    )
}

/** 精读 / 听写模式 Tab（复刻 Web：📖 精读 | ✍️ 听写 胶囊分段） */
@Composable
private fun ModeTabs(
    mode: TranscriptMode,
    onSelect: (TranscriptMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkAppearance()
    Row(
        modifier = modifier
            .padding(vertical = 8.dp)
            .width(200.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDark) Ink800 else Color(0xFFF2EFE8))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab("📖 精读", mode == TranscriptMode.READ, isDark, Modifier.weight(1f)) { onSelect(TranscriptMode.READ) }
        ModeTab("✍️ 听写", mode == TranscriptMode.DICTATE, isDark, Modifier.weight(1f)) { onSelect(TranscriptMode.DICTATE) }
    }
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) (if (isDark) Ink900 else Color.White) else Color.Transparent)
            .clickable(onClickLabel = label) { onClick() }
            .padding(vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = when {
                selected -> if (isDark) Primary400 else Primary600
                else -> if (isDark) Ink400 else Ink500
            }
        )
    }
}

/**
 * 双语字幕列表。滚动逻辑复刻 Web useTranscriptScroll：
 * 当前句滚到视口 30% 处停住，仅当其越过上安全区（<120dp）或下安全区（>65% 高度）时才滚动。
 * 精读模式渲染词级高亮句子（点词查词）；听写模式当前句渲染听写槽位。
 */
@Composable
private fun SubtitleList(
    subtitles: List<Subtitle>,
    activeIndex: Int,
    isPlaying: Boolean,
    positionSec: Double,
    showTranslation: Boolean,
    loopingIndex: Int?,
    /** 听写模式显式当前句（成功流转 / 点句跳转更新），null 表示非听写 */
    dictationIndex: Int?,
    transcriptMode: TranscriptMode,
    seekEnabled: Boolean,
    onSubtitleClick: (Subtitle) -> Unit,
    onToggleSentenceLoop: (Int) -> Unit,
    onWordClick: (word: String, contextEn: String, contextCn: String, timestampSec: Double) -> Unit,
    onDictationSuccess: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 听写模式跟随显式 dictationIndex（位置推导在字幕 gap 间会闪跳）
    val followIndex = if (transcriptMode == TranscriptMode.DICTATE) dictationIndex ?: activeIndex else activeIndex
    LaunchedEffect(followIndex) {
        if (followIndex < 0) return@LaunchedEffect
        val viewport = listState.layoutInfo.viewportSize.height.toFloat()
        if (viewport <= 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == followIndex }
        if (visible == null) {
            // 不在视口内：直接滚到目标位（当前句顶部停在视口 30% 处）
            val anchor = with(density) { (viewport * 0.3f).toInt() }
            listState.animateScrollToItem(activeIndex, scrollOffset = -anchor)
        } else {
            val safetyTop = with(density) { 120.dp.toPx() }
            val safetyBottom = viewport - viewport * 0.35f
            val top = visible.offset.toFloat()
            val bottom = top + visible.size
            if (top < safetyTop || bottom > safetyBottom) {
                // 复刻 Web：把当前句顶部对齐到视口 30% 高度处
                listState.animateScrollBy(top - viewport * 0.3f)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = navBarBottom + MiniBarHeight + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(subtitles, key = { _, sub -> sub.id }) { index, subtitle ->
            when {
                // 听写模式：显式当前句渲染听写槽位
                transcriptMode == TranscriptMode.DICTATE && index == dictationIndex -> DictationRow(
                    index = index,
                    subtitle = subtitle,
                    isPlaying = isPlaying,
                    showTranslation = showTranslation,
                    onJump = { onSubtitleClick(subtitle) },
                    onSuccess = onDictationSuccess
                )
                // 听写模式：非当前句淡化展示（点击跳播）
                transcriptMode == TranscriptMode.DICTATE -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.5f)
                        .clickable(enabled = seekEnabled) { onSubtitleClick(subtitle) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = subtitle.textEn,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 17.sp,
                        lineHeight = 30.sp,
                        color = if (isDarkAppearance()) Ink500 else Ink400
                    )
                }
                // 精读模式：词级高亮句子 + 点词查词
                else -> SubtitleRow(
                    subtitle = subtitle,
                    isActive = index == activeIndex,
                    isRead = activeIndex >= 0 && index < activeIndex,
                    isPlaying = isPlaying,
                    positionSec = positionSec,
                    showTranslation = showTranslation,
                    isLooping = loopingIndex == index,
                    seekEnabled = seekEnabled,
                    onClick = { onSubtitleClick(subtitle) },
                    onToggleLoop = { onToggleSentenceLoop(index) },
                    onWordClick = { word, startSec ->
                        onWordClick(word, subtitle.textEn, subtitle.textCn ?: "", startSec)
                    }
                )
            }
        }
    }
}

/** 词级点击信息：单词与其在 annotated string 中的字符区间 */
private data class WordSpan(val word: String, val start: Int, val end: Int, val timeStart: Double)

/**
 * 单条字幕（精读模式，复刻 Web SubtitleItem）：当前句卡片底 + 句内已读词 accent
 * 前景色 + 正在读词背景光斑 + 点词查词 + 译文折叠 + 单句循环按钮。
 */
@Composable
private fun SubtitleRow(
    subtitle: Subtitle,
    isActive: Boolean,
    isRead: Boolean,
    isPlaying: Boolean,
    positionSec: Double,
    showTranslation: Boolean,
    isLooping: Boolean,
    seekEnabled: Boolean,
    onClick: () -> Unit,
    onToggleLoop: () -> Unit,
    onWordClick: (word: String, startSec: Double) -> Unit
) {
    val isDark = isDarkAppearance()
    // 句级配色（Web：active bg-primary-50 dark:primary-900/20；无左侧边框）
    val cardBg = when {
        isActive && isDark -> Primary900.copy(alpha = 0.2f)
        isActive -> Primary50
        else -> Color.Transparent
    }
    val enColor = when {
        isActive -> if (isDark) Primary400 else Primary600
        isRead -> if (isDark) Ink200 else Ink800   // 已读：前景色加深
        else -> if (isDark) Ink500 else Ink400     // 未读：淡化
    }
    val cnColor = if (isActive) {
        if (isDark) Ink300 else Ink600
    } else {
        if (isDark) Ink500 else Ink400
    }
    // 词级配色（Web useWordHighlight）
    val wordHighlight = if (isDark) Accent900.copy(alpha = 0.4f) else Accent100
    val readWordColor = if (isDark) Accent300 else Accent700
    val loopTint = if (isLooping) (if (isDark) Primary400 else Primary600) else (if (isDark) Ink500 else Ink400)

    val words = subtitle.words.orEmpty()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }

    // 英文词级内容（对齐 Web SubtitleItem 的双分支）：
    // JSON 带词级时间戳 → 逐词配色 + 正在读光斑 + 点词查词；
    // SRT/纯文本 → 按空白分词仅绑点词查词（无词级时间，时间戳退化为句子 start）。
    val spans: List<WordSpan>
    val annotated: AnnotatedString = if (words.isNotEmpty()) {
        val wordSpans = mutableListOf<WordSpan>()
        val built = buildAnnotatedString {
            words.forEachIndexed { i, w ->
                val start = this.length
                val isCurrentWord = isActive && isPlaying && positionSec >= w.start && positionSec <= w.end
                val isReadWord = isActive && isPlaying && w.end < positionSec
                withStyle(
                    SpanStyle(
                        color = if (isReadWord) readWordColor else Color.Unspecified,
                        background = if (isCurrentWord) wordHighlight else Color.Transparent
                    )
                ) { append(w.word) }
                wordSpans.add(WordSpan(w.word, start, this.length - 1, w.start))
                if (i != words.lastIndex) append(" ")
            }
        }
        spans = wordSpans
        built
    } else {
        val tokens = srtWordTokens(subtitle.textEn)
        val built = buildAnnotatedString {
            tokens.forEachIndexed { i, token ->
                append(token.word)
                if (i != tokens.lastIndex) append(" ")
            }
        }
        spans = tokens.map { WordSpan(it.word, it.start, it.end, subtitle.start) }
        built
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable(enabled = seekEnabled, onClickLabel = "跳播到此句") { onClick() }
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                text = annotated,
                onTextLayout = { textLayout = it },
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 17.sp,
                    lineHeight = 30.sp,
                    color = enColor
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(subtitle.id) {
                        detectTapGestures { pos ->
                            val layout = textLayout ?: return@detectTapGestures
                            val offset = layout.getOffsetForPosition(pos)
                            spans.firstOrNull { offset >= it.start && offset <= it.end + 1 }
                                ?.let { onWordClick(it.word, it.timeStart) }
                        }
                    }
            )

            // 中文译文（右下角翻译浮动按钮控制折叠）
            subtitle.textCn?.takeIf { it.isNotBlank() }?.let { cn ->
                AnimatedVisibility(
                    visible = showTranslation,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Text(
                        text = cn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                        color = cnColor,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // 单句循环按钮：句卡片右下角
        if (isActive || isLooping) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 2.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = if (isLooping) "取消单句循环" else "单句循环") { onToggleLoop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLooping) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    contentDescription = null,
                    tint = loopTint,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ---------- 听写模式（复刻 Web DictationItem） ----------

/**
 * 听写行（复刻 Web DictationItem）：当前句渲染逐词槽位——
 * 已对 primary 粗体 / 输满且错红色删除线（错 3 次显示提示）/ 待填虚线下划槽；
 * 透明输入框覆盖全行捕获键入，整句正确给完成反馈（✓ + 底色高亮）后自动流转下一句，
 * 下一句以 subtitle.id 重建槽位（输入天然清空），回车（Done）错误计数。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DictationRow(
    index: Int,
    subtitle: Subtitle,
    isPlaying: Boolean,
    showTranslation: Boolean,
    onJump: () -> Unit,
    onSuccess: (Int) -> Unit
) {
    val isDark = isDarkAppearance()
    val targets = remember(subtitle.id) { dictationTargets(subtitle.textEn) }
    var input by remember(subtitle.id) { mutableStateOf("") }
    var errorCount by remember(subtitle.id) { mutableStateOf(0) }
    var completed by remember(subtitle.id) { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val inputWords = remember(input, subtitle.id) { chunkDictationInput(input, targets) }
    val isCorrect = isDictationCorrect(targets, inputWords)

    // 整句正确 → 完成反馈可见约 0.4s（期间本句继续循环）→ 自动流转下一句（每句只触发一次）
    LaunchedEffect(isCorrect, subtitle.id) {
        if (isCorrect && !completed) {
            completed = true
            delay(400)
            onSuccess(index)
        }
    }
    // 成为当前句时自动聚焦（弹出键盘）
    LaunchedEffect(subtitle.id) {
        try {
            focusRequester.requestFocus()
        } catch (e: Exception) {
            // 焦点请求落空忽略
        }
    }

    val matchedColor = if (isDark) Primary400 else Primary600
    val hintColor = Color(0xFF4A7FA5) // 远青青蓝 info
    // 完成反馈：底色向 primary 加深 + 播放键换 ✓
    val rowBackground by animateColorAsState(
        targetValue = when {
            completed -> matchedColor.copy(alpha = if (isDark) 0.32f else 0.24f)
            else -> if (isDark) Primary900.copy(alpha = 0.2f) else Primary50
        },
        animationSpec = tween(durationMillis = 250),
        label = "dictationRowBackground"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(rowBackground)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 左侧播放键：点击重播本句
            Icon(
                imageVector = if (completed) Icons.Filled.CheckCircle else Icons.Filled.PlayCircle,
                contentDescription = if (completed) "拼写正确" else "重播本句",
                tint = matchedColor,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !completed) { onJump() }
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Box {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        targets.forEachIndexed { i, target ->
                            val cw = cleanDictation(target)
                            val inputWord = inputWords.getOrNull(i)
                            val showHint = errorCount >= 3
                            when {
                                // 标点词自动通过 / 已对
                                cw.isEmpty() || (inputWord != null && inputWord.length == cw.length && inputWord.lowercase() == cw) -> {
                                    Text(
                                        text = target,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = matchedColor
                                    )
                                }
                                // 输满且错误：红色删除线 + 提示
                                inputWord != null && inputWord.length == cw.length -> {
                                    Column {
                                        Text(
                                            text = inputWord,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 17.sp,
                                            color = Color(0xFFD2503F),
                                            textDecoration = TextDecoration.LineThrough
                                        )
                                        if (showHint) {
                                            Text(
                                                text = target,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = hintColor
                                            )
                                        }
                                    }
                                }
                                // 待填：虚线下划槽 + 已输部分（错 3 次直接显示提示）
                                else -> DictationSlot(
                                    partialInput = inputWord.orEmpty(),
                                    hint = if (showHint) target else null,
                                    hintColor = hintColor,
                                    slotColor = if (isDark) Ink600 else Ink300
                                )
                            }
                        }
                    }
                    // 透明输入框覆盖全行捕获键入（对齐 Web 隐藏 input）
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (!isCorrect && !completed) errorCount += 1
                            }
                        ),
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0f)
                            .focusRequester(focusRequester)
                    )
                }

                // 译文（翻译浮动按钮控制）
                subtitle.textCn?.takeIf { it.isNotBlank() }?.let { cn ->
                    AnimatedVisibility(
                        visible = showTranslation,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            text = cn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) Ink300 else Ink600,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** 听写待填槽：虚线下划线 + 已输入字符（错 3 次后显示目标词提示） */
@Composable
private fun DictationSlot(
    partialInput: String,
    hint: String?,
    hintColor: Color,
    slotColor: Color
) {
    Box(
        modifier = Modifier
            .height(26.dp)
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = slotColor,
                    start = androidx.compose.ui.geometry.Offset(0f, y),
                    end = androidx.compose.ui.geometry.Offset(size.width, y),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                )
            }
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = hint ?: partialInput,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (hint != null) FontWeight.Normal else FontWeight.Bold,
            fontSize = 17.sp,
            color = if (hint != null) hintColor.copy(alpha = 0.7f) else Color.Unspecified
        )
    }
}

/** 右下角浮动圆形按钮：半透明底 + 阴影（Web quick actions 样式），激活态主题色 */
@Composable
private fun FloatingAction(
    icon: @Composable (Color) -> Unit,
    active: Boolean,
    onClick: () -> Unit
) {
    val isDark = isDarkAppearance()
    val bg = when {
        active && isDark -> Primary900.copy(alpha = 0.4f)
        active -> Primary50
        isDark -> Ink900.copy(alpha = 0.85f)
        else -> Color.White.copy(alpha = 0.92f)
    }
    val tint = when {
        active && isDark -> Primary400
        active -> Primary600
        else -> if (isDark) Ink400 else Ink500
    }
    Box(
        modifier = Modifier
            .size(40.dp)
            .shadow(4.dp, CircleShape)
            .clip(CircleShape)
            .background(bg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        icon(tint)
    }
}
