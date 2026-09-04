package com.wxkzd.yuanlu.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxkzd.yuanlu.feature.auth.LoginGateScreen
import com.wxkzd.yuanlu.feature.discover.DiscoverScreen
import com.wxkzd.yuanlu.feature.discover.DiscoverViewModel
import com.wxkzd.yuanlu.feature.home.HomeScreen
import com.wxkzd.yuanlu.feature.home.HomeViewModel
import com.wxkzd.yuanlu.feature.profile.ProfileScreen
import com.wxkzd.yuanlu.feature.profile.ProfileViewModel
import com.wxkzd.yuanlu.feature.profile.UserProfileViewModel
import com.wxkzd.yuanlu.feature.vocabulary.VocabularyScreen
import com.wxkzd.yuanlu.feature.vocabulary.VocabularyViewModel
import com.wxkzd.yuanlu.theme.ThemeMode

private data class TabItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/** 对齐 Web MobileBottomNav：首页 / 发现 / 生词本 / 我的 */
private val TABS = listOf(
    TabItem("首页", Icons.Filled.Home),
    TabItem("发现", Icons.Filled.Explore),
    TabItem("生词本", Icons.Filled.Translate),
    TabItem("我的", Icons.Filled.Person)
)

@Composable
fun MainScreen(
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    onOpenPodcast: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenEpisode: (String) -> Unit,
    onViewAllChannels: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    // 生词本 VM 由 AppNavHost 以 Activity 作用域创建（与播放壳同款），
    // 列表页与全局复习层共享同一状态源
    vocabularyViewModel: VocabularyViewModel,
    // 个人中心 VM（Activity 作用域）：编辑资料保存后「我的」Tab 借 profileRevision 刷新用户卡
    userProfileViewModel: UserProfileViewModel,
    onOpenPersonalCenter: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    // 全屏卡片复习打开时隐藏底部导航（复习层挂在全局根层级，盖住迷你播放条）
    val vocabularyState by vocabularyViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            if (!vocabularyState.isReviewOpen) {
                NavigationBar {
                    TABS.forEachIndexed { index, tab ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                // 首页：游客显示「立即登录」引导页（对齐 Web /home 游客态）
                0 -> {
                    if (isLoggedIn) {
                        HomeScreen(
                            viewModel = hiltViewModel<HomeViewModel>(),
                            onOpenPodcast = onOpenPodcast,
                            onOpenEpisode = onOpenEpisode
                        )
                    } else {
                        LoginGateScreen(onLogin = onLogin)
                    }
                }
                // 发现：公开内容，游客可浏览
                1 -> DiscoverScreen(
                    viewModel = hiltViewModel<DiscoverViewModel>(),
                    onOpenPodcast = onOpenPodcast,
                    onOpenChannel = onOpenChannel,
                    onViewAllChannels = onViewAllChannels
                )
                // 生词本：列表 + 全屏卡片复习（游客引导，对齐 Web /library/vocabulary）
                2 -> {
                    if (isLoggedIn) {
                        VocabularyScreen(viewModel = vocabularyViewModel)
                    } else {
                        LoginGateScreen(
                            onLogin = onLogin,
                            title = "构建你的生词本",
                            description = "收听时点词即存，随时随地复习你积累的每一个表达。"
                        )
                    }
                }
                // 我的
                3 -> ProfileScreen(
                    viewModel = hiltViewModel<ProfileViewModel>(),
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange,
                    userProfileViewModel = userProfileViewModel,
                    onOpenPersonalCenter = onOpenPersonalCenter
                )
            }
        }
    }
}
