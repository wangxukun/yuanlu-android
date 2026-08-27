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
import com.wxkzd.yuanlu.feature.auth.LoginScreen
import com.wxkzd.yuanlu.feature.discover.ChannelRoute
import com.wxkzd.yuanlu.feature.player.PlayerRoute
import com.wxkzd.yuanlu.feature.podcast.PodcastDetailRoute
import com.wxkzd.yuanlu.ui.main.MainScreen

@Composable
fun MainNavigation(
    appViewModel: AppViewModel = hiltViewModel()
) {
    val isLoggedIn by appViewModel.isLoggedIn.collectAsStateWithLifecycle()

    when (isLoggedIn) {
        null -> Box(modifier = Modifier.fillMaxSize())
        // 登录态由 TokenStore 驱动：登录成功/登出后该状态自动翻转，无需手动跳转
        false -> LoginScreen(onLoginSuccess = { /* state flips via tokenFlow */ })
        true -> AppNavHost()
    }
}

@Composable
private fun AppNavHost() {
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
                    onOpenPodcast = { open(PodcastDetailNav(it)) },
                    onOpenChannel = { open(ChannelNav(it)) },
                    onOpenEpisode = { open(PlayerNav(it)) }
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
                    onBack = { back() }
                )
            }
        }
    )
}
