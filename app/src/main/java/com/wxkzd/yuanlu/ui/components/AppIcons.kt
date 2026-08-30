package com.wxkzd.yuanlu.ui.components

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * material-icons-core 核心集之外的图标，按标准 24dp 网格 path 自绘（对齐 Web 端选型）。
 * 注意必须用 materialPath（默认 SolidColor 填充）而非 path：后者 fill 为 null，画不出任何内容。
 */

/** 耳机（Web 端 lucide Headphones）：播放数角标 */
val HeadsetIcon: ImageVector = materialIcon(name = "Filled.Headset") {
    materialPath {
        moveTo(12.0f, 1.0f)
        curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
        verticalLineToRelative(7.0f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        horizontalLineToRelative(3.0f)
        verticalLineToRelative(-8.0f)
        horizontalLineTo(5.0f)
        verticalLineToRelative(-2.0f)
        curveToRelative(0.0f, -3.87f, 3.13f, -7.0f, 7.0f, -7.0f)
        reflectiveCurveToRelative(7.0f, 3.13f, 7.0f, 7.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineToRelative(8.0f)
        horizontalLineToRelative(3.0f)
        curveToRelative(1.66f, 0.0f, 3.0f, -1.34f, 3.0f, -3.0f)
        verticalLineToRelative(-7.0f)
        curveToRelative(0.0f, -4.97f, -4.03f, -9.0f, -9.0f, -9.0f)
        close()
    }
}

/** 表盘时钟（Web 端 heroicons ClockIcon）：时长 */
val ScheduleIcon: ImageVector = materialIcon(name = "Filled.Schedule") {
    materialPath {
        moveTo(11.99f, 2.0f)
        curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
        reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
        curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
        reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
        close()
        moveTo(12.0f, 20.0f)
        curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
        reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
        reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
        reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
        close()
    }
    materialPath {
        moveTo(12.5f, 7.0f)
        horizontalLineTo(11.0f)
        verticalLineToRelative(6.0f)
        lineToRelative(5.25f, 3.15f)
        lineToRelative(0.75f, -1.23f)
        lineToRelative(-4.5f, -2.67f)
        close()
    }
}

/** 电视（Web 端 heroicons TvIcon）：所属播客 */
val TvIcon: ImageVector = materialIcon(name = "Filled.Tv") {
    materialPath {
        moveTo(21.0f, 3.0f)
        horizontalLineTo(3.0f)
        curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
        verticalLineToRelative(12.0f)
        curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
        horizontalLineToRelative(5.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(5.0f)
        curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
        lineTo(23.0f, 5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(21.0f, 17.0f)
        horizontalLineTo(3.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(18.0f)
        verticalLineToRelative(12.0f)
        close()
    }
}

/** 翻译（Web 端 lucide Languages）：标题/简介翻译按钮 */
val LanguagesIcon: ImageVector = materialIcon(name = "Filled.Translate") {
    materialPath {
        moveTo(12.87f, 15.07f)
        lineToRelative(-2.54f, -2.51f)
        lineToRelative(0.03f, -0.03f)
        arcTo(17.52f, 17.52f, 0.0f, false, false, 14.07f, 6.0f)
        horizontalLineTo(17.0f)
        verticalLineTo(4.0f)
        horizontalLineToRelative(-7.0f)
        verticalLineTo(2.0f)
        horizontalLineTo(8.0f)
        verticalLineToRelative(2.0f)
        horizontalLineTo(1.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(11.17f)
        curveTo(11.5f, 7.92f, 10.44f, 9.75f, 9.0f, 11.35f)
        curveTo(8.07f, 10.32f, 7.3f, 9.19f, 6.69f, 8.0f)
        horizontalLineToRelative(-2.0f)
        curveToRelative(0.73f, 1.63f, 1.73f, 3.17f, 2.98f, 4.56f)
        lineToRelative(-5.09f, 5.02f)
        lineTo(4.0f, 19.0f)
        lineToRelative(5.0f, -5.0f)
        lineToRelative(3.11f, 3.11f)
        lineToRelative(0.76f, -2.04f)
        close()
        moveTo(18.5f, 10.0f)
        horizontalLineToRelative(-2.0f)
        lineTo(12.0f, 22.0f)
        horizontalLineToRelative(2.0f)
        lineToRelative(1.12f, -3.0f)
        horizontalLineToRelative(4.75f)
        lineTo(21.0f, 22.0f)
        horizontalLineToRelative(2.0f)
        lineToRelative(-4.5f, -12.0f)
        close()
        moveTo(15.88f, 17.0f)
        lineToRelative(1.62f, -4.33f)
        lineTo(19.12f, 17.0f)
        horizontalLineToRelative(-3.24f)
        close()
    }
}

/** 下载（Web 端 lucide Download）：音频下载 */
val DownloadIcon: ImageVector = materialIcon(name = "Filled.Download") {
    materialPath {
        moveTo(19.0f, 9.0f)
        horizontalLineToRelative(-4.0f)
        verticalLineToRelative(-6.0f)
        horizontalLineToRelative(-6.0f)
        verticalLineToRelative(6.0f)
        horizontalLineToRelative(-4.0f)
        lineToRelative(7.0f, 7.0f)
        lineToRelative(7.0f, -7.0f)
        close()
        moveTo(5.0f, 18.0f)
        verticalLineToRelative(2.0f)
        horizontalLineToRelative(14.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(-14.0f)
        close()
    }
}

/** 文稿文件（Web 端 lucide FileDown）：文稿下载/预览 */
val DescriptionIcon: ImageVector = materialIcon(name = "Filled.Description") {
    materialPath {
        moveTo(14.0f, 2.0f)
        horizontalLineTo(6.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(4.0f, 20.0f)
        curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 1.99f, 2.0f)
        horizontalLineTo(18.0f)
        curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
        verticalLineTo(8.0f)
        lineToRelative(-6.0f, -6.0f)
        close()
        moveTo(16.0f, 18.0f)
        horizontalLineTo(8.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(2.0f)
        close()
        moveTo(16.0f, 14.0f)
        horizontalLineTo(8.0f)
        verticalLineToRelative(-2.0f)
        horizontalLineToRelative(8.0f)
        verticalLineToRelative(2.0f)
        close()
        moveTo(13.0f, 9.0f)
        verticalLineTo(3.5f)
        lineTo(18.5f, 9.0f)
        horizontalLineTo(13.0f)
        close()
    }
}

/** 实心书签（Web 端 heroicons BookmarkSquare solid）：已收藏 */
val BookmarkIcon: ImageVector = materialIcon(name = "Filled.Bookmark") {
    materialPath {
        moveTo(17.0f, 3.0f)
        horizontalLineTo(7.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(5.0f, 21.0f)
        lineToRelative(7.0f, -3.0f)
        lineToRelative(7.0f, 3.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
    }
}

/** 空心书签（Web 端 heroicons BookmarkSquare outline）：未收藏 */
val BookmarkBorderIcon: ImageVector = materialIcon(name = "Filled.BookmarkBorder") {
    materialPath {
        moveTo(17.0f, 3.0f)
        horizontalLineTo(7.0f)
        curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
        lineTo(5.0f, 21.0f)
        lineToRelative(7.0f, -3.0f)
        lineToRelative(7.0f, 3.0f)
        verticalLineTo(5.0f)
        curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
        close()
        moveTo(17.0f, 18.0f)
        lineToRelative(-5.0f, -2.18f)
        lineTo(7.0f, 18.0f)
        verticalLineTo(5.0f)
        horizontalLineToRelative(10.0f)
        verticalLineToRelative(13.0f)
        close()
    }
}

/** 麦克风（Web 端 lucide Mic）：语音评测 */
val MicIcon: ImageVector = materialIcon(name = "Filled.Mic") {
    materialPath {
        moveTo(12.0f, 14.0f)
        curveToRelative(1.66f, 0.0f, 2.99f, -1.34f, 2.99f, -3.0f)
        lineTo(15.0f, 5.0f)
        curveToRelative(0.0f, -1.66f, -1.34f, -3.0f, -3.0f, -3.0f)
        reflectiveCurveToRelative(-3.0f, 1.34f, -3.0f, 3.0f)
        verticalLineToRelative(6.0f)
        curveToRelative(0.0f, 1.66f, 1.34f, 3.0f, 3.0f, 3.0f)
        close()
        moveTo(17.3f, 11.0f)
        curveToRelative(0.0f, 3.0f, -2.54f, 5.1f, -5.3f, 5.1f)
        reflectiveCurveTo(6.7f, 14.0f, 6.7f, 11.0f)
        horizontalLineTo(5.0f)
        curveToRelative(0.0f, 3.41f, 2.72f, 6.23f, 6.0f, 6.72f)
        verticalLineTo(21.0f)
        horizontalLineToRelative(2.0f)
        verticalLineToRelative(-3.28f)
        curveToRelative(3.28f, -0.48f, 6.0f, -3.3f, 6.0f, -6.72f)
        horizontalLineToRelative(-1.7f)
        close()
    }
}
