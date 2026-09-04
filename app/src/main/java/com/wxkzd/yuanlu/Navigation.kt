package com.wxkzd.yuanlu

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.wxkzd.yuanlu.feature.intensive.IntensiveListeningRoute
import com.wxkzd.yuanlu.feature.player.FullScreenPlayerScreen
import com.wxkzd.yuanlu.feature.player.MiniPlayerBar
import com.wxkzd.yuanlu.feature.player.PlayerRoute
import com.wxkzd.yuanlu.feature.player.PlayerShellViewModel
import com.wxkzd.yuanlu.feature.podcast.PodcastDetailRoute
import com.wxkzd.yuanlu.feature.vocabulary.VocabularyReviewScreen
import com.wxkzd.yuanlu.feature.vocabulary.VocabularyViewModel
import com.wxkzd.yuanlu.theme.ThemeMode
import com.wxkzd.yuanlu.ui.main.MainScreen

/** Main Tab 底部导航栏高度（Material3 NavigationBar 默认 80dp），迷你条在其上方悬浮 */
private val BOTTOM_NAV_HEIGHT = 80.dp

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

    // 全局播放壳（Activity 作用域）：迷你条可见性/播放状态的唯一来源，
    // 任意页面起播后浮条在全局导航切换间保持一致显示。
    val shellViewModel: PlayerShellViewModel = hiltViewModel()
    val playerState by shellViewModel.playerState.collectAsStateWithLifecycle()
    val showMiniPlayer by shellViewModel.showMiniPlayer.collectAsStateWithLifecycle()
    val hasTrack = playerState.currentEpisode != null

    // 生词本 VM（Activity 作用域）：Tab 列表页与全局复习层共享同一状态源
    val vocabularyViewModel: VocabularyViewModel = hiltViewModel()
    val vocabularyState by vocabularyViewModel.uiState.collectAsStateWithLifecycle()

    var isFullScreenPlayerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasTrack) {
        // 无在播音轨时兜底收起全屏播放器
        if (!hasTrack) isFullScreenPlayerOpen = false
    }

    fun open(key: NavKey) {
        backStack.add(key)
    }

    fun back() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    val topKey = backStack.lastOrNull()
    // 精听页底部播放控制由全局迷你播放条接管（替代页内控制条），仅展开全屏播放器时收起
    val miniPlayerVisible = showMiniPlayer && !isFullScreenPlayerOpen

    Box(modifier = Modifier.fillMaxSize()) {
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
                        onThemeModeChange = onThemeModeChange,
                        vocabularyViewModel = vocabularyViewModel
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
                // 精听页：全屏播放器「精听模式」按钮携带进度进入
                entry<IntensiveListeningNav> { key ->
                    IntensiveListeningRoute(
                        episodeid = key.episodeid,
                        playbackPositionMs = key.positionMs,
                        onBack = { back() }
                    )
                }
                entry<ChannelListNav> {
                    ChannelListRoute(
                        onOpenChannel = { open(ChannelNav(it)) }
                    )
                }
            }
        )

        // ---- 全局迷你播放条：通栏贴底（左右 0 外边距），Main Tab 时紧贴底部导航上缘 ----
        AnimatedVisibility(
            visible = miniPlayerVisible,
            enter = slideInVertically(
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
            ) { it } + fadeIn(),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            MiniPlayerBar(
                state = playerState,
                onTogglePlay = shellViewModel::togglePlayPause,
                onClick = { isFullScreenPlayerOpen = true },
                onClose = shellViewModel::stop,
                modifier = Modifier.padding(bottom = if (topKey is Main) BOTTOM_NAV_HEIGHT else 0.dp),
                // Main Tab 的 NavigationBar 已消费手势区 inset；其余页面由迷你条自处理（背景仍铺满）
                applyNavigationInsets = topKey !is Main
            )
        }

        // ---- 全屏播放器：从迷你条平滑展开（底部弹入沉浸层，复刻 Web MobilePlayerSheet） ----
        AnimatedVisibility(
            visible = isFullScreenPlayerOpen && hasTrack,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { it } + fadeIn(tween(220)),
            exit = slideOutVertically(animationSpec = tween(240)) { it } + fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            FullScreenPlayerScreen(
                state = playerState,
                onCollapse = { isFullScreenPlayerOpen = false },
                onClosePlayer = shellViewModel::stop,
                onTogglePlay = shellViewModel::togglePlayPause,
                onSeekTo = shellViewModel::seekTo,
                onOpenEpisode = { episodeid ->
                    // 封面点击 → 剧集详情页（对齐 Web router.push(/episode/[id])）
                    isFullScreenPlayerOpen = false
                    open(PlayerNav(episodeid))
                },
                onOpenIntensive = { episodeid, playbackPositionMs ->
                    // 精听按钮：收起全屏播放器并携带当前进度进入精听页
                    isFullScreenPlayerOpen = false
                    open(IntensiveListeningNav(episodeid, playbackPositionMs))
                },
                onCyclePlaybackRate = shellViewModel::cyclePlaybackRate,
                onToggleLoopMode = shellViewModel::toggleLoopMode,
                onApplySleepConfig = shellViewModel::applySleepConfig,
                onCancelSleepTimer = shellViewModel::cancelSleepTimer
            )
        }

        // ---- 全屏卡片复习层：挂在迷你条/全屏播放器之上（复刻 Web ReviewModal 全屏形态） ----
        AnimatedVisibility(
            visible = vocabularyState.isReviewOpen,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.fillMaxSize()
        ) {
            VocabularyReviewScreen(
                state = vocabularyState,
                onFlip = vocabularyViewModel::flipCard,
                onSubmit = vocabularyViewModel::submitReview,
                onPrevCard = vocabularyViewModel::goToPrevCard,
                onNextCard = vocabularyViewModel::goToNextCard,
                onClose = vocabularyViewModel::closeReview,
                onRetry = vocabularyViewModel::retryForgotten
            )
        }
    }
}
