package com.wxkzd.yuanlu.feature.auth

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor

/**
 * 「立即登录」整页引导，对齐 Web 端 PodcastAuthPrompt（游客访问受限页时展示）。
 * 文案参数化，供首页/生词本等受限 Tab 复用。
 */
@Composable
fun LoginGateScreen(
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "跟上您的节目",
    description: String = "保存您的位置，关注节目并查看最新剧集，生词收藏等获取最多功能。"
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = PodcastWaveVector,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(180.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        PressablePillButton(
            text = "立即登录",
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 主胶囊按钮：按压缩放反馈（对齐 Web 的 active:scale-95） */
@Composable
fun PressablePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        label = "pillScale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        modifier = modifier
            .height(52.dp)
            .scale(buttonScale)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 品牌插画：播客信号图标（对齐 Web PodcastIcon 的 SVG，24dp viewBox）。
 * 由外到内：外层信号弧 → 内层信号弧 → 中心圆点 → 底部基座。
 * 使用 ImageVector.Builder 显式构建，每条路径指定 fill + EvenOdd 规则。
 * 渲染时由 Icon(tint) 统一着色覆盖黑色填充。
 */
val PodcastWaveVector: ImageVector by lazy {
    ImageVector.Builder(
        name = "PodcastSignal",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // 外层信号弧（对齐 Web path4：最外围广播弧线）
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(17.2989f, 7.25116f)
            curveTo(14.371f, 4.24961f, 9.62904f, 4.24961f, 6.70106f, 7.25116f)
            curveTo(3.76631f, 10.2596f, 3.76631f, 15.1424f, 6.70106f, 18.1509f)
            lineTo(5.98523f, 18.8491f)
            curveTo(2.67159f, 15.4522f, 2.67159f, 9.94978f, 5.98523f, 6.55288f)
            curveTo(9.30564f, 3.14904f, 14.6944f, 3.14904f, 18.0148f, 6.55288f)
            curveTo(21.3284f, 9.94978f, 21.3284f, 15.4522f, 18.0148f, 18.8491f)
            lineTo(17.2989f, 18.1509f)
            curveTo(20.2337f, 15.1424f, 20.2337f, 10.2596f, 17.2989f, 7.25116f)
            close()
        }
        // 内层信号弧（对齐 Web path3：内圈弧线）
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(15.182f, 9.81802f)
            curveTo(13.4246f, 8.06066f, 10.5754f, 8.06066f, 8.81802f, 9.81802f)
            curveTo(7.06066f, 11.5754f, 7.06066f, 14.4246f, 8.81802f, 16.182f)
            lineTo(8.11091f, 16.8891f)
            curveTo(5.96303f, 14.7412f, 5.96303f, 11.2588f, 8.11091f, 9.11091f)
            curveTo(10.2588f, 6.96303f, 13.7412f, 6.96303f, 15.8891f, 9.11091f)
            curveTo(18.037f, 11.2588f, 18.037f, 14.7412f, 15.8891f, 16.8891f)
            lineTo(15.182f, 16.182f)
            curveTo(16.9393f, 14.4246f, 16.9393f, 11.5754f, 15.182f, 9.81802f)
            close()
        }
        // 中心圆点（对齐 Web path2：环形圆点，外圈 r=2 / 内圈 r=1）
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(12f, 12.5f)
            curveTo(11.4477f, 12.5f, 11f, 12.9477f, 11f, 13.5f)
            curveTo(11f, 14.0523f, 11.4477f, 14.5f, 12f, 14.5f)
            curveTo(12.5523f, 14.5f, 13f, 14.0523f, 13f, 13.5f)
            curveTo(13f, 12.9477f, 12.5523f, 12.5f, 12f, 12.5f)
            close()
            moveTo(10f, 13.5f)
            curveTo(10f, 12.3954f, 10.8954f, 11.5f, 12f, 11.5f)
            curveTo(13.1046f, 11.5f, 14f, 12.3954f, 14f, 13.5f)
            curveTo(14f, 14.6046f, 13.1046f, 15.5f, 12f, 15.5f)
            curveTo(10.8954f, 15.5f, 10f, 14.6046f, 10f, 13.5f)
            close()
        }
        // 底部基座（对齐 Web path1：半圆形底座 pedestal）
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(10.8519f, 16.7284f)
            curveTo(11.2159f, 16.5776f, 11.606f, 16.5f, 12f, 16.5f)
            curveTo(12.394f, 16.5f, 12.7841f, 16.5776f, 13.1481f, 16.7284f)
            curveTo(13.512f, 16.8791f, 13.8427f, 17.1001f, 14.1213f, 17.3787f)
            curveTo(14.3999f, 17.6573f, 14.6209f, 17.988f, 14.7716f, 18.3519f)
            curveTo(14.9224f, 18.7159f, 15f, 19.106f, 15f, 19.5f)
            curveTo(15f, 19.7761f, 14.7761f, 20f, 14.5f, 20f)
            lineTo(9.5f, 20f)
            curveTo(9.22386f, 20f, 9f, 19.7761f, 9f, 19.5f)
            curveTo(9f, 19.106f, 9.0776f, 18.7159f, 9.22836f, 18.3519f)
            curveTo(9.37913f, 17.988f, 9.6001f, 17.6573f, 9.87868f, 17.3787f)
            curveTo(10.1573f, 17.1001f, 10.488f, 16.8791f, 10.8519f, 16.7284f)
            close()
            moveTo(12f, 17.5f)
            curveTo(11.7374f, 17.5f, 11.4773f, 17.5517f, 11.2346f, 17.6522f)
            curveTo(10.992f, 17.7528f, 10.7715f, 17.9001f, 10.5858f, 18.0858f)
            curveTo(10.4001f, 18.2715f, 10.2528f, 18.492f, 10.1522f, 18.7346f)
            curveTo(10.1164f, 18.8211f, 10.0868f, 18.9098f, 10.0635f, 19f)
            lineTo(13.9365f, 19f)
            curveTo(13.9132f, 18.9098f, 13.8836f, 18.8211f, 13.8478f, 18.7346f)
            curveTo(13.7473f, 18.492f, 13.5999f, 18.2715f, 13.4142f, 18.0858f)
            curveTo(13.2285f, 17.9001f, 13.008f, 17.7528f, 12.7654f, 17.6522f)
            curveTo(12.5227f, 17.5517f, 12.2626f, 17.5f, 12f, 17.5f)
            close()
        }
    }.build()
}

