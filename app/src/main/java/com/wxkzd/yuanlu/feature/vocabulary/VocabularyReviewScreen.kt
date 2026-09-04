package com.wxkzd.yuanlu.feature.vocabulary

import android.media.MediaPlayer
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.feature.player.Accent500
import com.wxkzd.yuanlu.feature.player.Ink100
import com.wxkzd.yuanlu.feature.player.Ink400
import com.wxkzd.yuanlu.feature.player.Ink50
import com.wxkzd.yuanlu.feature.player.Ink800
import com.wxkzd.yuanlu.feature.player.Primary400
import com.wxkzd.yuanlu.feature.player.Primary500
import com.wxkzd.yuanlu.feature.player.Primary600
import com.wxkzd.yuanlu.feature.player.isDarkAppearance

/** SRS 四档语义色（对齐 Web error/warning/success/info） */
private val ForgotColor = Color(0xFFD2503F)
private val HardColor = Accent500
private val GoodColor = Primary500
private val EasyColor = Color(0xFF4A7FA5)

/**
 * 全屏沉浸式复习卡片（复刻 Web ReviewModal 移动端全屏形态）：
 * 顶部渐变进度条 + 计数；卡片区点击翻面（rotateY 180° 3D 翻转）、
 * 左右滑动切换上/下一张；底部「显示答案」或 忘记/模糊/认识/简单 四档 SRS 按钮
 * （按钮副文案为按 Leitner 算法预演的下次间隔）；完成后进入总结页（忘记的词可再来一轮）。
 * 挂载于全局导航根层级（迷你播放条之上），开卡与翻面自动播一次词典发音。
 */
@Composable
fun VocabularyReviewScreen(
    state: VocabularyUiState,
    onFlip: () -> Unit,
    onSubmit: (Int) -> Unit,
    onPrevCard: () -> Unit,
    onNextCard: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit
) {
    val isDark = isDarkAppearance()
    val contentColor = MaterialTheme.colorScheme.onSurface
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 发音播放器：随复习层生命周期创建/释放；开卡/翻面自动播一次（对齐 Web）
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
        }
    }

    val currentCard = state.currentCard
    LaunchedEffect(state.currentIndex, state.isFlipped, state.reviewQueue) {
        val card = state.currentCard ?: return@LaunchedEffect
        val url = card.dictEntry?.audioUs
            ?: card.dictEntry?.audioUk
            ?: card.speakUrl
            ?: youdaoDictVoiceUrl(card.word)
        playUrl(url)
    }

    BackHandler(onBack = onClose)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ---- 顶部：渐变进度条 + 计数/关闭 ----
            ReviewProgressHeader(state = state, onClose = onClose)
            HorizontalDivider(color = if (isDark) Ink800 else Ink100)

            if (state.reviewFinished || currentCard == null) {
                ReviewSummary(state = state, onRetry = onRetry, onClose = onClose)
            } else {
                // ---- 卡片区：滑动切换 + 点击翻面 ----
                CardArea(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = state,
                    onFlip = onFlip,
                    onPrevCard = onPrevCard,
                    onNextCard = onNextCard,
                    onPlayUrl = ::playUrl
                )
                HorizontalDivider(color = if (isDark) Ink800 else Ink100)
                // ---- 底部：显示答案 / SRS 四档 ----
                ReviewFooter(
                    card = currentCard,
                    isFlipped = state.isFlipped,
                    isSubmitting = state.isSubmitting,
                    onFlip = onFlip,
                    onSubmit = onSubmit
                )
            }
        }
    }
}

// ---------------- 顶部进度 ----------------

@Composable
private fun ReviewProgressHeader(state: VocabularyUiState, onClose: () -> Unit) {
    val total = state.reviewQueue.size.coerceAtLeast(1)
    val rawFraction = if (state.reviewFinished) 1f
    else (state.currentIndex + (if (state.isFlipped) 1 else 0)).toFloat() / total
    val fraction by animateFloatAsState(
        targetValue = rawFraction.coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "reviewProgress"
    )
    val isDark = isDarkAppearance()

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(if (isDark) Ink800 else Ink100)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Primary500, Accent500)))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = null,
                tint = if (isDark) Primary400 else Primary600,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state.reviewFinished) "复习总结"
                else "${state.currentIndex + 1} / ${state.reviewQueue.size}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            if (!state.reviewFinished) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "闪卡",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isDark) Ink800 else Ink100)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "关闭复习") { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭复习",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ---------------- 卡片区：滑动 + 翻转 ----------------

@Composable
private fun CardArea(
    modifier: Modifier = Modifier,
    state: VocabularyUiState,
    onFlip: () -> Unit,
    onPrevCard: () -> Unit,
    onNextCard: () -> Unit,
    onPlayUrl: (String?) -> Unit
) {
    // 切换方向（1=前进 -1=后退）驱动滑入滑出方向；拖拽偏移做跟手反馈
    var direction by remember { mutableIntStateOf(1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .graphicsLayer { translationX = dragOffset }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, amount ->
                        dragOffset += amount
                        change.consume()
                    },
                    onDragEnd = {
                        val threshold = 80.dp.toPx()
                        when {
                            dragOffset <= -threshold -> {
                                direction = 1
                                onNextCard()
                            }
                            dragOffset >= threshold -> {
                                direction = -1
                                onPrevCard()
                            }
                        }
                        dragOffset = 0f
                    }
                )
            }
            .padding(vertical = 8.dp)
    ) {
        AnimatedContent(
            targetState = state.currentIndex,
            transitionSpec = {
                val enter = slideInHorizontally(
                    animationSpec = tween(300),
                    initialOffsetX = { if (direction >= 0) it / 2 else -it / 2 }
                ) + fadeIn(tween(300))
                val exit = slideOutHorizontally(
                    animationSpec = tween(300),
                    targetOffsetX = { if (direction >= 0) -it / 2 else it / 2 }
                ) + fadeOut(tween(200))
                enter togetherWith exit
            },
            label = "cardSwitch"
        ) { index ->
            val card = state.reviewQueue.getOrNull(index)
            if (card != null) {
                FlipCard(
                    card = card,
                    isFlipped = state.isFlipped,
                    onFlip = onFlip,
                    onPlayUrl = onPlayUrl
                )
            }
        }
    }
}

/**
 * 3D 翻转卡片（对齐 Web perspective:1200px + rotateY(180deg) 0.6s cubic-bezier(0.4,0,0.2,1)）：
 * rotationY ∈ [0,90) 显示正面，[90,180] 显示背面（背面自带 -180° 抵消父级翻转）。
 */
@Composable
private fun FlipCard(
    card: VocabularyItem,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onPlayUrl: (String?) -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        // cubic-bezier(0.4,0,0.2,1) = Material 标准 FastOutSlowInEasing（对齐 Web 翻面曲线）
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "flipRotation"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // 相机距离越大透视越弱（Web 1200px 视觉 ≈ 16 倍密度）
                cameraDistance = 16 * density
            },
        contentAlignment = Alignment.Center
    ) {
        if (rotation <= 90f) {
            CardFrontFace(
                card = card,
                onFlip = onFlip,
                onPlayWord = {
                    val url = card.dictEntry?.audioUs
                        ?: card.dictEntry?.audioUk
                        ?: card.speakUrl
                        ?: youdaoDictVoiceUrl(card.word)
                    onPlayUrl(url)
                },
                modifier = Modifier.graphicsLayer { rotationY = rotation }
            )
        } else {
            CardBackFace(
                card = card,
                onPlayUrl = onPlayUrl,
                modifier = Modifier.graphicsLayer { rotationY = rotation - 180f }
            )
        }
    }
}

/** 正面：单词大字（默认态，点击翻面看答案） */
@Composable
private fun CardFrontFace(
    card: VocabularyItem,
    onFlip: () -> Unit,
    onPlayWord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkAppearance()
    val masteredHint = card.episodeTitle
    Column(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onFlip() }) }
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = card.word,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (isDark) Primary400 else Primary600,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background((if (isDark) Primary400 else Primary600).copy(alpha = 0.1f))
                .clickable(onClickLabel = "播放发音") { onPlayWord() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = "播放发音",
                tint = if (isDark) Primary400 else Primary600,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = "回忆词义，点击卡片查看答案",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        )
        masteredHint?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "来自《$it》",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 背面：完整释义 + 音标发音 + 原声例句（可滚动） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBackFace(
    card: VocabularyItem,
    onPlayUrl: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isDarkAppearance()
    val entry = card.dictEntry
    val contentColor = if (isDark) Color(0xFFE8E3D9) else Color(0xFF322D23)
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = if (isDark) Primary400 else Primary600

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = card.word,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 英美音标 + 发音
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReviewPhonetic(
                label = "US",
                phonetic = entry?.phoneticsUs,
                labelColor = primary,
                onPlay = { onPlayUrl(entry?.audioUs ?: youdaoDictVoiceUrl(card.word)) }
            )
            ReviewPhonetic(
                label = "UK",
                phonetic = entry?.phoneticsUk,
                labelColor = Accent500,
                onPlay = { onPlayUrl(entry?.audioUk ?: youdaoDictVoiceUrl(card.word, us = false)) }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // 释义卡
        val definitions = entry?.definitions.orEmpty()
        if (definitions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Ink800.copy(alpha = 0.5f) else Ink100.copy(alpha = 0.5f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ReviewSectionLabel("释义", subColor)
                definitions.forEach { def ->
                    Row {
                        Text(
                            text = def.pos,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(primary.copy(alpha = 0.1f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = def.meaningCn,
                                style = MaterialTheme.typography.bodyMedium,
                                color = contentColor
                            )
                            def.meaningEn?.let { en ->
                                Text(
                                    text = en,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = subColor.copy(alpha = 0.7f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        } else {
            card.definition?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // 原声出处
        card.contextSentence?.let { sentence ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(primary.copy(alpha = 0.06f), Accent500.copy(alpha = 0.06f))
                        )
                    )
                    .border(1.dp, primary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                ReviewSectionLabel("原声出处", subColor)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = buildHighlightedContext(
                        sentence = sentence,
                        word = card.word,
                        baseColor = contentColor,
                        highlightColor = primary
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                card.translation?.let { cn ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = cn,
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor.copy(alpha = 0.7f)
                    )
                }
                card.episodeTitle?.let { title ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "来自《$title》",
                        style = MaterialTheme.typography.labelSmall,
                        color = subColor.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 词源速记
        entry?.etymology?.let { ety ->
            if (!ety.mnemonic.isNullOrBlank() || ety.prefix != null || ety.root != null || ety.suffix != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Accent500.copy(alpha = if (isDark) 0.12f else 0.07f))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ReviewSectionLabel("词源记忆", subColor)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOfNotNull(
                            ety.prefix?.let { "前缀 · $it" },
                            ety.root?.let { "词根 · $it" },
                            ety.suffix?.let { "后缀 · $it" }
                        ).forEach { part ->
                            Text(
                                text = part,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Ink400 else Color(0xFF655D4C),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDark) Ink800 else Color.White)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    ety.mnemonic?.takeIf { it.isNotBlank() }?.let { mnemonic ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "💡 $mnemonic",
                            style = MaterialTheme.typography.labelSmall,
                            color = subColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewPhonetic(
    label: String,
    phonetic: String?,
    labelColor: Color,
    onPlay: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Black,
            color = labelColor
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = phonetic ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(4.dp))
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

@Composable
private fun ReviewSectionLabel(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        color = color.copy(alpha = 0.55f)
    )
}

// ---------------- 底部按钮区 ----------------

@Composable
private fun ReviewFooter(
    card: VocabularyItem,
    isFlipped: Boolean,
    isSubmitting: Boolean,
    onFlip: () -> Unit,
    onSubmit: (Int) -> Unit
) {
    val isDark = isDarkAppearance()
    Surface(
        color = if (isDark) Ink800.copy(alpha = 0.35f) else Ink50.copy(alpha = 0.6f)
    ) {
        if (!isFlipped) {
            // 未翻面：显示答案
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Primary500 else Primary600)
                    .clickable(onClickLabel = "显示答案") { onFlip() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "显示答案",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
        } else {
            // 翻面后：忘记 / 模糊 / 认识 / 简单（副文案 = Leitner 下次间隔预演）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrsButton(
                        label = "忘记",
                        interval = nextIntervalLabel(card.proficiency, ReviewQuality.FORGOT),
                        color = ForgotColor,
                        enabled = !isSubmitting,
                        icon = Icons.Filled.Replay,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSubmit(ReviewQuality.FORGOT) }
                    )
                    SrsButton(
                        label = "模糊",
                        interval = nextIntervalLabel(card.proficiency, ReviewQuality.HARD),
                        color = HardColor,
                        enabled = !isSubmitting,
                        icon = Icons.Filled.Schedule,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSubmit(ReviewQuality.HARD) }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SrsButton(
                        label = "认识",
                        interval = nextIntervalLabel(card.proficiency, ReviewQuality.GOOD),
                        color = GoodColor,
                        enabled = !isSubmitting,
                        icon = Icons.Filled.CheckCircle,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSubmit(ReviewQuality.GOOD) }
                    )
                    SrsButton(
                        label = "简单",
                        interval = nextIntervalLabel(card.proficiency, ReviewQuality.EASY),
                        color = EasyColor,
                        enabled = !isSubmitting,
                        icon = Icons.Filled.MilitaryTech,
                        isDark = isDark,
                        modifier = Modifier.weight(1f),
                        onClick = { onSubmit(ReviewQuality.EASY) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SrsButton(
    label: String,
    interval: String,
    color: Color,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClickLabel = label) { onClick() } else it },
        shape = RoundedCornerShape(12.dp),
        color = if (isDark) Ink800 else Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color.White.copy(alpha = 0.08f) else Ink100
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp)
                .animateContentSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled) color else color.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                text = interval,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

// ---------------- 总结页 ----------------

/** 复习结果四档统计行（忘记/模糊/认识/简单 → error/warning/success/info） */
private fun qualityLabel(quality: Int): String = when (quality) {
    ReviewQuality.FORGOT -> "忘记"
    ReviewQuality.HARD -> "模糊"
    ReviewQuality.GOOD -> "认识"
    ReviewQuality.EASY -> "简单"
    else -> "认识"
}

private fun qualityColor(quality: Int): Color = when (quality) {
    ReviewQuality.FORGOT -> ForgotColor
    ReviewQuality.HARD -> HardColor
    ReviewQuality.EASY -> EasyColor
    else -> GoodColor
}

@Composable
private fun ReviewSummary(
    state: VocabularyUiState,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val isDark = isDarkAppearance()
    val results = state.reviewResults
    val forgot = results.count { it.quality == ReviewQuality.FORGOT }
    val hard = results.count { it.quality == ReviewQuality.HARD }
    val good = results.count { it.quality == ReviewQuality.GOOD }
    val easy = results.count { it.quality == ReviewQuality.EASY }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(GoodColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.EmojiEvents,
                contentDescription = null,
                tint = GoodColor,
                modifier = Modifier.size(44.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "复习完成！",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "本轮共复习了 ${results.size} 个生词",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))

        // 四档统计
        @Composable
        fun StatCell(label: String, count: Int, color: Color) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.1f))
                    .padding(vertical = 12.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = color.copy(alpha = 0.7f)
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { StatCell("忘记", forgot, ForgotColor) }
                Box(Modifier.weight(1f)) { StatCell("模糊", hard, HardColor) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { StatCell("认识", good, GoodColor) }
                Box(Modifier.weight(1f)) { StatCell("简单", easy, EasyColor) }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // 逐词结果
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            results.forEach { r ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) Ink800.copy(alpha = 0.5f) else Ink100.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = r.word,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = qualityLabel(r.quality),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = qualityColor(r.quality)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))

        if (forgot > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Primary500 else Primary600)
                    .clickable(onClickLabel = "再来一轮") { onRetry() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "再来一轮（${forgot}个）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isDark) Ink800 else Ink100)
                .clickable(onClickLabel = "完成") { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "完成",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 14.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
