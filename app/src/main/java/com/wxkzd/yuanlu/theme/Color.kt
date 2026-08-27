package com.wxkzd.yuanlu.theme

import androidx.compose.ui.graphics.Color

/**
 * 品牌色对齐 Web 端（yuanlu/app/globals.css）：
 * 远青 primary + 暖纸 ink 中性色 + 曙光橙 accent。
 * 阶梯值与 tailwind.config.mjs 的 primary-50..950 / ink-50..950 一一对应。
 */

// ---------- Light ----------
// 远青 green
val LightPrimary = Color(0xFF1F7A5C)          // primary-600
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFEDF7F2) // primary-50
val LightOnPrimaryContainer = Color(0xFF0A241B) // primary-950
// 曙光橙 accent
val LightSecondary = Color(0xFFD98A17)        // accent-500
val LightOnSecondary = Color(0xFFFFFFFF)
// 远青青蓝 info
val LightTertiary = Color(0xFF4A7FA5)
// 暖纸 ink
val LightBackground = Color(0xFFFAF8F3)       // ink-50
val LightOnBackground = Color(0xFF1C1917)     // ink-950 近似
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C1917)
val LightSurfaceVariant = Color(0xFFF2EFE8)   // ink-100 近似
val LightOnSurfaceVariant = Color(0xFF57534E) // ink-600 近似
val LightOutline = Color(0xFFE5E0D5)          // ink-200 近似
val LightError = Color(0xFFD2503F)

// ---------- Dark ----------
val DarkPrimary = Color(0xFF4DA989)           // primary-400
val DarkOnPrimary = Color(0xFF0A241B)         // primary-950
val DarkPrimaryContainer = Color(0xFF0F3B2C)  // primary-900 近似
val DarkOnPrimaryContainer = Color(0xFFD5EFE3)
val DarkSecondary = Color(0xFFD98A17)         // accent-500
val DarkOnSecondary = Color(0xFF151310)
val DarkTertiary = Color(0xFF7FA8C8)
val DarkBackground = Color(0xFF151310)        // ink-950
val DarkOnBackground = Color(0xFFE8E3D9)
val DarkSurface = Color(0xFF1E1B16)
val DarkOnSurface = Color(0xFFE8E3D9)
val DarkSurfaceVariant = Color(0xFF26221C)
val DarkOnSurfaceVariant = Color(0xFFA8A29E)
val DarkOutline = Color(0xFF3A342C)
val DarkError = Color(0xFFD2503F)
