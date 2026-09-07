package com.wxkzd.yuanlu.feature.pronunciation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 多维雷达图（Canvas 自绘，复刻 Web recharts RadarChart 视觉）：
 * 同心网格环 + 轴线 + 维度标签 + 半透明数据多边形。
 * 画像卡（五维紫）与薄弱音素卡（六维靛蓝）共用。
 */
@Composable
fun RadarChart(
    labels: List<String>,
    values: List<Float>,
    modifier: Modifier = Modifier,
    strokeColor: Color = Color(0xFF7C3AED),
    gridColor: Color = Color(0xFFE5E7EB),
    labelColor: Color = Color(0xFF6B7280),
    gridRingCount: Int = 4
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val n = minOf(labels.size, values.size)
            if (n < 3) return@Canvas

            // 布局：画布中心为雷达心，外圈半径留出标签空间
            val labelPadding = 34.dp.toPx()
            val outerRadius = min(min(size.width, size.height) / 2f - labelPadding, size.width / 2f - labelPadding)
            if (outerRadius <= 0f) return@Canvas
            val center = Offset(size.width / 2f, size.height / 2f)

            fun vertex(index: Int, radiusRatio: Float): Offset {
                // 首轴正上方，顺时针展开（recharts 默认朝向）
                val angle = -Math.PI / 2 + 2 * Math.PI * index / n
                return Offset(
                    center.x + radiusRatio * outerRadius * cos(angle).toFloat(),
                    center.y + radiusRatio * outerRadius * sin(angle).toFloat()
                )
            }

            // 同心网格环
            for (ring in 1..gridRingCount) {
                val ratio = ring.toFloat() / gridRingCount
                val gridPath = Path()
                for (i in 0 until n) {
                    val p = vertex(i, ratio)
                    if (i == 0) gridPath.moveTo(p.x, p.y) else gridPath.lineTo(p.x, p.y)
                }
                gridPath.close()
                drawPath(gridPath, color = gridColor, style = Stroke(width = 1.dp.toPx()))
            }

            // 轴线
            for (i in 0 until n) {
                drawLine(
                    color = gridColor,
                    start = center,
                    end = vertex(i, 1f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // 数据多边形（填充 20% + 描边，对齐 recharts Radar fillOpacity=0.2）
            val dataPath = Path()
            for (i in 0 until n) {
                val ratio = (values[i].coerceIn(0f, 100f) / 100f)
                val p = vertex(i, ratio)
                if (i == 0) dataPath.moveTo(p.x, p.y) else dataPath.lineTo(p.x, p.y)
            }
            dataPath.close()
            drawPath(dataPath, color = strokeColor.copy(alpha = 0.2f))
            drawPath(dataPath, color = strokeColor, style = Stroke(width = 2.dp.toPx()))

            // 维度标签（外圈之外，按象限对齐）
            for (i in 0 until n) {
                val anchor = vertex(i, 1f)
                val layout = textMeasurer.measure(labels[i], labelStyle)
                val dx = (anchor.x - center.x)
                val dy = (anchor.y - center.y)
                val x = when {
                    dx > 1f -> anchor.x + 6.dp.toPx()
                    dx < -1f -> anchor.x - layout.size.width - 6.dp.toPx()
                    else -> anchor.x - layout.size.width / 2f
                }
                val y = when {
                    dy > 1f -> anchor.y + 2.dp.toPx()
                    dy < -1f -> anchor.y - layout.size.height - 2.dp.toPx()
                    else -> anchor.y - layout.size.height / 2f
                }
                drawText(
                    textLayoutResult = layout,
                    color = labelColor,
                    topLeft = Offset(x, y)
                )
            }
        }
    }
}
