package com.wxkzd.yuanlu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.wxkzd.yuanlu.feature.auth.AppViewModel
import com.wxkzd.yuanlu.feature.auth.LoginSheet
import com.wxkzd.yuanlu.feature.discover.ChannelRoute
import com.wxkzd.yuanlu.feature.discover.ChannelListRoute
import com.wxkzd.yuanlu.feature.player.PlayerRoute
import com.wxkzd.yuanlu.feature.podcast.PodcastDetailRoute
import com.wxkzd.yuanlu.theme.ThemeMode
import com.wxkzd.yuanlu.ui.main.MainScreen

/**
 * 游客模式（对齐 Web）：未登录也进入主界面，公开内容可浏览；
 * 受限入口（首页/生词本/我的菜单）引导登录，登录弹层全局挂载。
 */
@Composable
fun MainNavigation(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val isLoggedIn by appViewModel.isLoggedIn.collectAsStateWithLifecycle()
    val showLoginSheet by appViewModel.showLoginSheet.collectAsStateWithLifecycle()
    val themeMode by appViewModel.themeMode.collectAsStateWithLifecycle()

    when (isLoggedIn) {
        null -> Box(modifier = Modifier.fillMaxSize())
        else -> AppNavHost(
            isLoggedIn = isLoggedIn == true,
            onLogin = appViewModel::showLogin,
            themeMode = themeMode,
            onThemeModeChange = appViewModel::setThemeMode
        )
    }

    // 全局登录弹层（对齐 Web 的全局 ModalProvider），登录成功由 AppViewModel 自动收起
    if (showLoginSheet && isLoggedIn == false) {
        LoginSheet(onDismiss = appViewModel::hideLogin)
    }
}

@Composable
private fun AppNavHost(
    isLoggedIn: Boolean,
    onLogin: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val backStack = rememberNavBackStack(Main)

    fun open(key: NavKey) {
        backStack.add(key)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    NavDisplay(
        backStack = backStack,
        onBack = { back() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            entry<Main> {
                MainScreen(
                    isLoggedIn = isLoggedIn,
                    onLogin = onLogin,
                    onOpenPodcast = { open(PodcastDetailNav(it)) },
                    onOpenChannel = { open(ChannelNav(it)) },
                    onOpenEpisode = { open(PlayerNav(it)) },
                    onViewAllChannels = { open(ChannelListNav) },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
                )
            }
            entry<PodcastDetailNav> { key ->
                PodcastDetailRoute(
                    podcastid = key.podcastid,
                    onBack = { back() },
                    onOpenPodcast = { open(PodcastDetailNav(it)) },
                    onOpenChannel = { open(ChannelNav(it)) },
                    onOpenEpisode = { open(PlayerNav(it)) }
                )
            }
            entry<ChannelNav> { key ->
                ChannelRoute(
                    name = key.name,
                    onBack = { back() },
                    onOpenPodcast = { open(PodcastDetailNav(it)) },
                    onOpenEpisode = { open(PlayerNav(it)) }
                )
            }
            entry<PlayerNav> { key ->
                PlayerRoute(
                    episodeid = key.episodeid,
                    onBack = { back() },
                    onLogin = onLogin,
                    onOpenPodcast = { open(PodcastDetailNav(it)) },
                    onOpenEpisode = { open(PlayerNav(it)) }
                )
            }
            entry<ChannelListNav> {
                ChannelListRoute(
                    onOpenChannel = { open(ChannelNav(it)) }
                )
            }
        }
    )
}
