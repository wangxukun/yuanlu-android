package com.wxkzd.yuanlu.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wxkzd.yuanlu.core.media.PlayerState
import com.wxkzd.yuanlu.ui.components.CoverImage

/** 迷你条内容行高度（对齐 Web --mini-player-height: 60px） */
private val MiniPlayerHeight = 60.dp

/**
 * 全局迷你播放条——1:1 复刻 Web 端 components/player/MobilePlayerBar.tsx：
 * - 通栏贴边：左右 0 外边距、上缘 2dp 播放进度线（远青渐变填充）；
 * - 内容：40dp 封面（播放中叠加均衡器跳动动效）+ 标题/播客名 + 播放暂停圆钮 + 关闭图标；
 * - 深浅色随外观设置切换（浅色 white/95、深色 ink-900/95，边框 ink-100/ink-800）；
 * - 整条点击展开全屏播放器；关闭图标停止播放并隐藏浮条（Web closePlayer）。
 *
 * @param applyNavigationInsets 非 Main 页面无底部导航，由本条自处理手势区 inset
 *          （背景仍通栏铺满，保证与屏幕底部 0 外边距）；Main 页传 false（上方导航已消费 inset）。
 */
@Composable
fun MiniPlayerBar(
    state: PlayerState,
    onTogglePlay: () -> Unit,
    onClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationInsets: Boolean = true
) {
    val episode = state.currentEpisode ?: return
    val isDark = isDarkAppearance()
    // Web：bg-white/95 dark:bg-ink-900/95 + border-t ink-100/ink-800
    val barBg = if (isDark) Ink900.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
    val borderTop = if (isDark) Ink800 else Ink100
    val titleColor = if (isDark) Ink100 else Color(0xFF322D23) // ink-800
    val podcastColor = if (isDark) Ink400 else Ink500
    val closeTint = if (isDark) Ink500 else Ink400
    val trackColor = if (isDark) Ink800 else Ink200

    val progress = if (state.duration > 0) {
        (state.currentPosition.toFloat() / state.duration).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .clickable(onClickLabel = "展开全屏播放器") { onClick() }
    ) {
        // ---- 上缘 2dp 进度线（absolute top edge，Web：gradient primary-500 → primary-600） ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(
                        Brush.horizontalGradient(
                            if (isDark) listOf(Primary500, Primary400) else listOf(Primary500, Primary600)
                        )
                    )
            )
        }
        // ---- 上边框线（border-t） ----
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(borderTop))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (applyNavigationInsets) Modifier.navigationBarsPadding() else Modifier)
                .padding(horizontal = 16.dp)
                .heightIn(min = MiniPlayerHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ---- 封面 40dp：播放中叠加均衡器动效（Web animate-eq） ----
            Box(modifier = Modifier.size(40.dp)) {
                CoverImage(
                    url = episode.coverUrl,
                    contentDescription = "正在播放：${episode.title}",
                    modifier = Modifier
                        .fillMaxSize()
                        .border(
                            width = 1.dp,
                            color = if (isDark) Ink700.copy(alpha = 0.5f) else Ink200.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp)
                        ),
                    cornerRadius = 8.dp
                )
                if (state.isPlaying) {
                    EqualizerOverlay(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }

            // ---- 标题 / 播客名（精听模式下带小指示器） ----
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (state.isIntensiveMode) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Primary600.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = if (isDark) Primary400 else Primary600,
                                modifier = Modifier.size(9.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "精听",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Primary400 else Primary600,
                                fontSize = 9.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                    Text(
                        text = episode.podcastTitle ?: "远路播客",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = podcastColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ---- 播放/暂停：40dp 远青圆钮，按压 0.9 缩放（Web active:scale-90） ----
            CircleIconButton(
                size = 40,
                backgroundColor = Primary600,
                contentColor = Color.White,
                contentDescription = if (state.isPlaying) "暂停" else "播放",
                onClick = onTogglePlay
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // ---- 关闭图标：停止播放并收起浮条（Web closePlayer） ----
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "关闭播放器") { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭播放器",
                    tint = closeTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * 均衡器跳动覆层：半透明黑底 + 4 根白色小柱 scaleY 往复（0.3 ↔ 1，1s 周期，
 * 依次延迟 0/200/400/600ms），复刻 Web tailwind animate-eq。
 */
@Composable
private fun EqualizerOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.4f))
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(4) { index ->
            val scale by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(offsetMillis = index * 200)
                ),
                label = "eq$index"
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .graphicsLayer {
                        scaleY = scale
                        scaleX = 1f
                    }
                    .background(Color.White, RoundedCornerShape(50))
            )
        }
    }
}

/** 圆形图标按钮：按压 0.9 缩放 + Ripple（复刻 Web 圆钮 active:scale-90 观感） */
@Composable
private fun CircleIconButton(
    size: Int,
    backgroundColor: Color,
    contentColor: Color,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(if (pressed) 0.9f else 1f)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                onClickLabel = contentDescription
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) { content() }
}
