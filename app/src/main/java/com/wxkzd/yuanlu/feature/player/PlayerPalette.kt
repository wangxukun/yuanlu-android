package com.wxkzd.yuanlu.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 远路 Web 端色板（globals.css）摘录，播放器相关页面共用：
 * 剧集详情页、迷你播放条、全屏播放器、精听页。阶梯值与 tailwind.config 一一对应。
 */
internal val Primary600 = Color(0xFF1F7A5C)
internal val Primary700 = Color(0xFF1A6349)
internal val Primary500 = Color(0xFF2E8F6F)
internal val Primary400 = Color(0xFF4DA989)
internal val Primary50 = Color(0xFFEDF7F2)
internal val Primary900 = Color(0xFF0F3628)
internal val Accent500 = Color(0xFFD98A17)
internal val Accent100 = Color(0xFFFAE5C6)
internal val Accent300 = Color(0xFFECB35E)
internal val Accent700 = Color(0xFF96580D)
internal val Accent900 = Color(0xFF4E2E0B)
internal val Ink50 = Color(0xFFFAF8F3)
internal val Ink100 = Color(0xFFF1EDE4)
internal val Ink200 = Color(0xFFE3DDCF)
internal val Ink300 = Color(0xFFCFC7B4)
internal val Ink400 = Color(0xFFA79E8A)
internal val Ink500 = Color(0xFF857C68)
internal val Ink600 = Color(0xFF655D4C)
internal val Ink700 = Color(0xFF4A4436)
internal val Ink800 = Color(0xFF322D23)
internal val Ink900 = Color(0xFF221F18)
internal val Ink950 = Color(0xFF151310)

/**
 * 当前是否处于深色外观。外观设置（跟随系统/浅色/深色）由 AppViewModel 驱动
 * YuanluTheme 的 colorScheme 切换，这里从 background 亮度反推，避免各组件
 * 各自读取设置源造成口径不一致。
 */
@Composable
internal fun isDarkAppearance(): Boolean =
    MaterialTheme.colorScheme.background.luminance() < 0.5f
