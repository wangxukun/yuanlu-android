package com.wxkzd.yuanlu

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.feature.auth.AppViewModel
import com.wxkzd.yuanlu.theme.ThemeMode
import com.wxkzd.yuanlu.theme.YuanluTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  /** 待消费的 deep link（yuanlu://speech | yuanlu://intensive），导航层消费后置 null */
  private val pendingDeepLink: MutableState<Uri?> = mutableStateOf(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    pendingDeepLink.value = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

    enableEdgeToEdge()
    setContent {
      MainRoot(pendingDeepLink = pendingDeepLink)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // singleTop：应用已在前台时再次 deep link 走这里，无需重建 Activity
    pendingDeepLink.value = intent.takeIf { it.action == Intent.ACTION_VIEW }?.data
  }
}

/** 主题三态（跟随系统/浅色/深色）由 settings_prefs 驱动，包裹在导航之外 */
@Composable
private fun MainRoot(pendingDeepLink: MutableState<Uri?>) {
  val appViewModel: AppViewModel = hiltViewModel()
  val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()
  val darkTheme = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  }
  YuanluTheme(darkTheme = darkTheme) {
      Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(appViewModel = appViewModel, pendingDeepLink = pendingDeepLink)
      }
  }
}
