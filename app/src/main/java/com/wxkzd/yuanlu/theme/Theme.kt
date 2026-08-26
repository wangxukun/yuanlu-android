package com.wxkzd.yuanlu.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkAccent,
    background = DarkBase100,
    surface = DarkBase200,
    onPrimary = DarkBaseContent,
    onSecondary = DarkBaseContent,
    onTertiary = DarkBaseContent,
    onBackground = DarkBaseContent,
    onSurface = DarkBaseContent,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightAccent,
    background = LightBase100,
    surface = LightBase200,
    onPrimary = LightBase100,
    onSecondary = LightBase100,
    onTertiary = LightBase100,
    onBackground = LightBaseContent,
    onSurface = LightBaseContent,
)

@Composable
fun YuanluTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false, // Disabled dynamic color to strictly follow DaisyUI
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
