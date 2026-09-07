package com.wxkzd.yuanlu.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.HistoryItem
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.VocabularyItem
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** 本周里程卡数据（收听时长 → 行走距离，对齐 Web WeeklyMileageCard） */
data class WeeklyMileage(
    val kmCurrent: Double = 0.0,
    val kmGoal: Double = 10.0,
    /** 本周收听时长环比上周（%）；上周无数据且本周 >0 时为 100 */
    val weeklyProgress: Int = 0,
    /** 距周目标还差的分钟数（已达成为 0） */
    val remainingMins: Int = 0,
    val wordsCurrent: Int = 0,
    val wordsGoal: Int = 50
)

/** 「我的路」一周 7 天节点（周一…周日，今日高亮） */
data class JourneyDay(
    val label: String,
    val minutes: Int,
    val isToday: Boolean
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    // ---- 头部问候区 ----
    val greeting: String = "你好",
    val displayName: String = "朋友",
    val bio: String = DEFAULT_BIO,
    val streakDays: Int = 0,
    // ---- 核心卡片 ----
    val latestHistory: HistoryItem? = null,
    val mileage: WeeklyMileage = WeeklyMileage(),
    val journeyDays: List<JourneyDay> = emptyList(),
    // ---- 剧集列表区 ----
    val continueListening: List<HistoryItem> = emptyList(),
    val recommended: List<Episode> = emptyList(),
    val recommendedLevel: String = "General",
    val latestEpisodes: List<Episode> = emptyList()
) {
    companion object {
        const val DEFAULT_BIO = "路虽远行则将至，事虽难做则可成。"
    }
}

/**
 * 首页 ViewModel（复刻 Web app/(main)/home/page.tsx 的服务端聚合）：
 * 一次刷新内以 coroutineScope + async 并发拉取 7 路数据
 * （profile / stats / 本周+上周活动 / 收听历史 / 生词 / 最新剧集），
 * 与 Web 的 Promise.all 语义一致；每路独立 fail-soft，
 * 仅当全部请求失败时进入整页错误态，否则用默认值补齐缺失模块。
 *
 * Web 专用的 /api/user/stats home 聚合端点不对外，本周里程与词汇路标
 * 由 overview + weekly-activity(0/1) + profile + vocabulary/all 在端上重算。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val contentRepository: ContentRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** 一次聚合刷新是否在途（防重入；与 UI 的 Loading/Refreshing 展示解耦） */
    private var refreshing = false

    init {
        refresh()
    }

    /**
     * @param silent true = 下拉刷新（保留现有内容，仅转圈）；
     *               false = 首次/重试（回到骨架屏 Loading）
     */
    fun refresh(silent: Boolean = false) {
        if (refreshing) return
        refreshing = true
        viewModelScope.launch {
            try {
            if (silent) {
                _uiState.update { it.copy(isRefreshing = true) }
            } else {
                _uiState.value = HomeUiState(isLoading = true)
            }
            var anySuccess = false
            var lastError: String? = null
            var networkFailed = false
            coroutineScope {
                // 8 路并发（仓库层已把异常折叠为 Result，async 不会向上抛）
                val profileDeferred = async { authRepository.getProfile() }
                val statsDeferred = async { authRepository.getStatsOverview() }
                val weekNowDeferred = async { authRepository.getWeeklyActivity(0) }
                val weekLastDeferred = async { authRepository.getWeeklyActivity(1) }
                val historyDeferred =
                    async { contentRepository.getListeningHistory(1, HISTORY_SIZE, "all") }
                val vocabDeferred = async { contentRepository.getAllVocabulary() }
                val episodesDeferred =
                    async { contentRepository.getLatestEpisodes(1, EPISODE_FETCH_SIZE) }
                // /api/episode/list 的 coverUrl 未签名（Web 端在服务端签名），
                // 借 /api/podcast/list 的已签名专辑封面做剧集封面回退
                val podcastsDeferred = async { contentRepository.getPodcasts() }

                fun <T> Result<T>.folded(): T? = when (this) {
                    is Result.Success -> {
                        anySuccess = true
                        data
                    }
                    is Result.Error -> {
                        lastError = message
                        null
                    }
                    Result.NetworkError -> {
                        networkFailed = true
                        null
                    }
                }

                val profile = profileDeferred.await().folded()
                val stats = statsDeferred.await().folded()
                val weekNow = weekNowDeferred.await().folded()
                val weekLast = weekLastDeferred.await().folded()
                val history = historyDeferred.await().folded()
                val vocab = vocabDeferred.await().folded()
                val episodes = episodesDeferred.await().folded()
                val podcasts = podcastsDeferred.await().folded()

                if (anySuccess) {
                    _uiState.update {
                        buildState(
                            it, profile, stats?.streakDays, weekNow, weekLast,
                            history, vocab, episodes, podcasts
                        )
                    }
                }
            }
            if (!anySuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (networkFailed) "网络连接失败" else lastError ?: "加载失败，请稍后重试"
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = null) }
            }
            } finally {
                refreshing = false
            }
        }
    }

    private fun buildState(
        previous: HomeUiState,
        profile: UserProfile?,
        streakDays: Int?,
        weekNow: List<com.wxkzd.yuanlu.domain.model.WeeklyActivityItem>?,
        weekLast: List<com.wxkzd.yuanlu.domain.model.WeeklyActivityItem>?,
        history: com.wxkzd.yuanlu.domain.model.HistoryPage?,
        vocab: List<VocabularyItem>?,
        episodes: List<Episode>?,
        podcasts: List<com.wxkzd.yuanlu.domain.model.Podcast>?
    ): HomeUiState {
        val displayName = profile?.nickname?.takeIf { it.isNotBlank() } ?: "朋友"
        val bio = profile?.bio?.takeIf { it.isNotBlank() } ?: HomeUiState.DEFAULT_BIO

        // ---- 本周里程 ----
        val thisWeekMinutes = weekNow?.sumOf { it.minutes } ?: 0
        val lastWeekMinutes = weekLast?.sumOf { it.minutes } ?: 0
        val goalHours = (profile?.weeklyListeningGoalHours ?: DEFAULT_LISTENING_GOAL_HOURS)
            .coerceAtLeast(0)
        val mileage = WeeklyMileage(
            kmCurrent = thisWeekMinutes / 60.0 * KM_PER_HOUR,
            kmGoal = goalHours * KM_PER_HOUR,
            weeklyProgress = when {
                lastWeekMinutes > 0 ->
                    ((thisWeekMinutes - lastWeekMinutes) * 100 / lastWeekMinutes)
                thisWeekMinutes > 0 -> 100
                else -> 0
            },
            remainingMins = (goalHours * 60 - thisWeekMinutes).coerceAtLeast(0),
            wordsCurrent = vocab?.let(::countWordsThisWeek) ?: 0,
            wordsGoal = (profile?.weeklyWordsGoal ?: DEFAULT_WORDS_GOAL).coerceAtLeast(0)
        )

        // ---- 我的路：weekly-activity 为周一~周日 7 天，标记今天 ----
        val todayIndex = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7
        val journeyDays = if (weekNow?.size == 7) {
            weekNow.mapIndexed { i, d -> JourneyDay(d.day, d.minutes, i == todayIndex) }
        } else {
            DAY_LABELS.mapIndexed { i, label -> JourneyDay(label, 0, i == todayIndex) }
        }

        // ---- 继续收听：第 1 条给顶部续播卡，其余给横向列表 ----
        val historyItems = history?.items.orEmpty()
        // 剧集封面回退：podcast.list 的封面已签名（episode.list 的 coverUrl
        // 可能缺省或未签名 403），CoverImage 主 URL 失败后自动降级到专辑封面
        val podcastCovers = podcasts?.filterNot { it.coverUrl.isNullOrBlank() }
            ?.associate { it.podcastid to it.coverUrl }
            .orEmpty()
        val episodesSorted = episodes.orEmpty().map { ep ->
            ep.copy(coverFallbackUrl = ep.podcastid?.let { podcastCovers[it] })
        }

        // ---- 为你推荐：learnLevel → CEFR 难度映射（对齐 Web LEVEL_MAPPING） ----
        val level = profile?.learnLevel?.takeIf { it.isNotBlank() } ?: "General"
        val targetDifficulties = LEVEL_MAPPING[level] ?: emptyList()
        val (recLevel, recommended) = if (targetDifficulties.isNotEmpty()) {
            val matched = episodesSorted.filter { it.difficulty in targetDifficulties }
            if (matched.isEmpty()) "General" to episodesSorted.take(RECOMMEND_LIMIT)
            else level to matched.take(RECOMMEND_LIMIT)
        } else {
            "General" to episodesSorted.take(RECOMMEND_LIMIT)
        }

        return previous.copy(
            greeting = currentGreeting(),
            displayName = displayName,
            bio = bio,
            streakDays = streakDays ?: 0,
            latestHistory = historyItems.firstOrNull(),
            mileage = mileage,
            journeyDays = journeyDays,
            continueListening = historyItems.drop(1),
            recommended = recommended,
            recommendedLevel = recLevel,
            latestEpisodes = episodesSorted.take(LATEST_LIMIT)
        )
    }

    /** 本周新增生词数（addedDate 落在周一 00:00 之后；口径对齐 Web getWeeklyWordsCount） */
    private fun countWordsThisWeek(items: List<VocabularyItem>): Int {
        val weekStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) + 5) % 7)
        }.timeInMillis
        return items.count { item ->
            parseIsoMillis(item.addedDate)?.let { it >= weekStart } == true
        }
    }

    private fun parseIsoMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(iso)?.time
                ?: iso.take(10).let { date ->
                    SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)?.time
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun currentGreeting(): String {
        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 0..11 -> "早上好"
            in 12..17 -> "下午好"
            else -> "晚上好"
        }
    }

    companion object {
        /** 首页收听历史拉取量：1 条给顶部续播卡 + 4 条给继续收听列表（Web 同口径取 4） */
        const val HISTORY_SIZE = 5
        /** 最新发布展示数（Web getRecentPublishedEpisodes(8)） */
        const val LATEST_LIMIT = 8
        /** 为你推荐展示数（Web getRecommendedEpisodes 默认 4） */
        const val RECOMMEND_LIMIT = 4
        /** 推荐难度过滤的候选池大小 */
        const val EPISODE_FETCH_SIZE = 50

        /** 步行速度 5km/h → 1 小时收听 = 5km 里程（对齐 Web WeeklyMileageCard） */
        const val KM_PER_HOUR = 5.0

        /** Web getUserHomeStats 的兜底默认值 */
        const val DEFAULT_LISTENING_GOAL_HOURS = 2
        const val DEFAULT_WORDS_GOAL = 50

        val DAY_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        /** learnLevel → 剧集 CEFR 难度映射（对齐 Web episode.service 的 LEVEL_MAPPING） */
        val LEVEL_MAPPING = mapOf(
            "Beginner" to listOf("A1", "A2"),
            "Intermediate" to listOf("B1", "B2"),
            "Advanced" to listOf("C1", "C2")
        )
    }
}
