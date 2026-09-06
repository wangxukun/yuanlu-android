package com.wxkzd.yuanlu.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Comment
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.feature.favorites.FavoriteCenter
import com.wxkzd.yuanlu.ui.components.isLoadableCoverUrl
import com.wxkzd.yuanlu.ui.components.resolveEpisodeCoverUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 剧集详情页 UI 状态（对齐 Web 端 episode/[id] 页面能力）：
 * 头部播放/翻译、互动评论、相关剧集、文稿（字幕）弹层。
 */
data class EpisodeDetailUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val episode: Episode? = null,
    /** 登录后签发的音频直链；null 表示需登录（字幕仅 3 分钟预览） */
    val audioUrl: String? = null,
    val isLoggedIn: Boolean = false,
    // 标题/简介翻译（有道，需登录）
    val translatedTitle: String? = null,
    val isTranslatingTitle: Boolean = false,
    val translatedDesc: String? = null,
    val isTranslatingDesc: Boolean = false,
    // 互动讨论
    val comments: List<Comment> = emptyList(),
    val isLoadingComments: Boolean = true,
    val isSubmittingComment: Boolean = false,
    // 相关剧集（同播客，最多 5 条）
    val relatedEpisodes: List<Episode> = emptyList(),
    // 从 JWT 解出的用户角色（USER | PREMIUM | ADMIN）；专属剧集对非会员展示锁图标用
    val userRole: String? = null,
    // 文稿（双语字幕）弹层
    val isTranscriptOpen: Boolean = false,
    // 收藏态（全局 FavoriteCenter 驱动：列表页取消收藏后返回本页同步空心）
    val isFavorited: Boolean = false,
    // 收藏请求在途（对齐 Web isLoadingFavorite）
    val isFavoriteBusy: Boolean = false
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val contentRepository: ContentRepository,
    private val tokenStore: TokenStore,
    private val favoriteCenter: FavoriteCenter
) : ViewModel() {

    val playerState = playerController.playerState

    private val _uiState = MutableStateFlow(EpisodeDetailUiState())
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    private val _subtitles = MutableStateFlow<List<Subtitle>>(emptyList())
    val subtitles: StateFlow<List<Subtitle>> = _subtitles.asStateFlow()

    /** 一次性提示（Toast），消费后置空 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    val isLoggedIn: StateFlow<Boolean> = tokenStore.tokenFlow
        .map { !it.isNullOrEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activeSubtitleIndex: StateFlow<Int> = combine(
        playerController.playerState.map { it.currentPosition },
        _subtitles
    ) { positionMs, subs ->
        val positionSec = positionMs / 1000.0
        subs.indexOfFirst { positionSec >= it.start && positionSec <= it.end }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = -1
    )

    private var loadedEpisodeId: String? = null
    private var favoriteKey: FavoriteCenter.Key? = null

    init {
        // 登录态与角色变化同步进详情状态（评论表单/专属剧集锁图标等据此切换）
        viewModelScope.launch {
            isLoggedIn.collect { logged ->
                _uiState.update { it.copy(isLoggedIn = logged) }
            }
        }
        viewModelScope.launch {
            tokenStore.roleFlow.collect { role ->
                _uiState.update { it.copy(userRole = role) }
            }
        }
        // 全局收藏中心驱动：任意页面（收藏列表/播客详情）变更后本页图标同步翻转
        viewModelScope.launch {
            favoriteCenter.states.collect { table ->
                val key = favoriteKey ?: return@collect
                table[key]?.let { favorited ->
                    _uiState.update { it.copy(isFavorited = favorited) }
                }
            }
        }
        viewModelScope.launch {
            favoriteCenter.pendingKeys.collect { pending ->
                val key = favoriteKey ?: return@collect
                _uiState.update { it.copy(isFavoriteBusy = key in pending) }
            }
        }
        viewModelScope.launch {
            favoriteCenter.messages.collect { _toast.value = it }
        }
    }

    /**
     * Nav3 entry 不做 SavedStateHandle 参数注入，由路由层在 LaunchedEffect 中调用；
     * 同一剧集重复调用幂等。进入页面不自动播放（对齐 Web），点"开始精听"才起播。
     */
    fun load(episodeid: String) {
        if (loadedEpisodeId == episodeid) return
        loadedEpisodeId = episodeid
        // 重置加载态时保留登录态/角色，避免 auth Flow 已发射过、登录用户被误判为游客
        _uiState.update {
            EpisodeDetailUiState(
                isLoading = true,
                isLoggedIn = it.isLoggedIn,
                userRole = it.userRole
            )
        }
        _subtitles.value = emptyList()

        viewModelScope.launch {
            val episode = when (val episodeResult = contentRepository.getEpisode(episodeid)) {
                is Result.Success -> episodeResult.data
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = episodeResult.message) }
                    return@launch
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isLoading = false, error = "网络连接失败") }
                    return@launch
                }
            }

            // 字幕接口一次性给出归一化字幕与登录后签发的音频直链；失败不阻断详情页
            var audioUrl: String? = null
            when (val subtitlesResult = contentRepository.getSubtitles(episodeid)) {
                is Result.Success -> {
                    _subtitles.value = subtitlesResult.data.subtitles
                    audioUrl = subtitlesResult.data.audioUrl
                }
                is Result.Error -> _toast.value = subtitlesResult.message
                Result.NetworkError -> _toast.value = "字幕加载失败，请检查网络"
            }
            // 收藏态初值：episode detail 的 userState.isFavorited 走 authWithMobile（Bearer 可靠），
            // 全局中心已有更新值时优先（列表页/其他页面刚变更过）
            val key = FavoriteCenter.Key(FavoriteCenter.Target.EPISODE, episodeid)
            favoriteKey = key
            val initialFavorite = favoriteCenter.knownState(key) ?: episode.isFavorited
            favoriteCenter.seedIfAbsent(key, initialFavorite)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    episode = episode,
                    audioUrl = audioUrl,
                    isFavorited = initialFavorite
                )
            }

            // 播客详情：detail 接口的 coverUrl 未签名（Web 端在服务端重签），Android 在此替换。
            // 用本接口而非 list-by-podcastid 分页：后者首页仅 100 条，更早的剧集（本播客
            // 2023-05-03 及之前）取不到签名封面，会拿未签名 URL 加载 403 而空白；
            // 这里一次拿到全量剧集签名封面做主封面，同时把播客签名封面挂到 coverFallbackUrl
            // 供加载期逐级回退（如剧集封面是 AVIF、低版本 Android 解码失败时降级到专辑封面）
            episode.podcastid?.let { podcastid ->
                launch {
                    when (val detail = contentRepository.getPodcastDetail(podcastid)) {
                        is Result.Success -> {
                            val all = detail.data.episodes
                            val podcastCover = detail.data.podcast.coverUrl
                                ?.takeIf { isLoadableCoverUrl(it) }
                            val signedCover = resolveEpisodeCoverUrl(
                                episodeCoverUrl = all.firstOrNull { it.episodeid == episodeid }?.coverUrl,
                                podcastCoverUrl = detail.data.podcast.coverUrl
                            )
                            _uiState.update { s ->
                                s.copy(
                                    episode = s.episode?.let { e ->
                                        e.copy(
                                            coverUrl = signedCover ?: e.coverUrl,
                                            coverFallbackUrl = podcastCover
                                        )
                                    },
                                    relatedEpisodes = all
                                        .filter { it.episodeid != episodeid }
                                        .take(5)
                                        .map { it.copy(coverFallbackUrl = podcastCover) }
                                )
                            }
                        }
                        else -> Unit
                    }
                }
            }

            // 评论
            launch {
                when (val comments = contentRepository.getComments(episodeid)) {
                    is Result.Success -> _uiState.update { it.copy(comments = comments.data, isLoadingComments = false) }
                    is Result.Error -> _uiState.update { it.copy(isLoadingComments = false) }
                    Result.NetworkError -> _uiState.update { it.copy(isLoadingComments = false) }
                }
            }
        }
    }

    fun retry() {
        loadedEpisodeId?.let { load(it) }
    }

    /** 收藏/取消收藏：乐观翻转由 FavoriteCenter 统一处理并广播（对齐 Web handleToggleFavorite） */
    fun toggleFavorite() {
        loadedEpisodeId?.let { favoriteCenter.toggleEpisode(it) }
    }

    fun consumeToast() {
        _toast.value = null
    }

    /**
     * 封面/播放键/开始精听共用：当前剧集在播则暂停，否则断点续播起播。
     * [intensive] = true 时以精听模式起播（PlayerState 带精听标记，迷你条/全屏播放器显示指示器）。
     */
    fun togglePlayback(intensive: Boolean = false) {
        val state = _uiState.value
        val episode = state.episode ?: return
        val audioUrl = state.audioUrl
        if (audioUrl.isNullOrBlank()) return // 未登录：由 UI 层引导登录
        if (playerState.value.currentEpisode?.episodeid == episode.episodeid) {
            if (intensive) playerController.setIntensiveMode(true)
            playerController.togglePlayPause()
        } else {
            playerController.play(
                episode.copy(audioUrl = audioUrl),
                startPositionMs = resumePositionMs(episode),
                intensive = intensive
            )
        }
    }

    /**
     * 开始精听：起播并进入精听模式——全局底部弹出迷你播放条（含精听指示器），
     * 由迷你条 → 全屏播放器 → 精听按钮逐级进入精听页。
     * 无音频直链（游客/无权限）时退回打开文稿弹层做 3 分钟字幕预览。
     */
    fun startIntensiveListening() {
        if (_uiState.value.audioUrl.isNullOrBlank()) {
            _uiState.update { it.copy(isTranscriptOpen = true) }
        } else {
            togglePlayback(intensive = true)
        }
    }

    fun setTranscriptOpen(open: Boolean) {
        _uiState.update { it.copy(isTranscriptOpen = open) }
    }

    fun seekToSubtitle(subtitle: Subtitle) {
        playerController.seekTo((subtitle.start * 1000).toLong())
    }

    // ---------- 翻译（标题/简介共用一套逻辑） ----------

    private enum class TranslateTarget { TITLE, DESCRIPTION }

    fun translateTitle() = translateText(TranslateTarget.TITLE)

    fun translateDescription() = translateText(TranslateTarget.DESCRIPTION)

    private fun translateText(target: TranslateTarget) {
        val state = _uiState.value
        val text = when (target) {
            TranslateTarget.TITLE -> state.episode?.title ?: return
            TranslateTarget.DESCRIPTION -> state.episode?.description?.takeIf { it.isNotBlank() } ?: return
        }
        val (busy, current) = when (target) {
            TranslateTarget.TITLE -> state.isTranslatingTitle to state.translatedTitle
            TranslateTarget.DESCRIPTION -> state.isTranslatingDesc to state.translatedDesc
        }
        if (busy) return
        // 已有译文再点一次 = 收起译文
        if (!current.isNullOrEmpty()) {
            _uiState.update {
                when (target) {
                    TranslateTarget.TITLE -> it.copy(translatedTitle = null)
                    TranslateTarget.DESCRIPTION -> it.copy(translatedDesc = null)
                }
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                when (target) {
                    TranslateTarget.TITLE -> it.copy(isTranslatingTitle = true)
                    TranslateTarget.DESCRIPTION -> it.copy(isTranslatingDesc = true)
                }
            }
            when (val result = contentRepository.translate(text)) {
                is Result.Success -> _uiState.update {
                    when (target) {
                        TranslateTarget.TITLE -> it.copy(translatedTitle = result.data)
                        TranslateTarget.DESCRIPTION -> it.copy(translatedDesc = result.data)
                    }
                }
                is Result.Error -> _toast.value = result.message
                Result.NetworkError -> _toast.value = "网络错误，请重试"
            }
            _uiState.update {
                when (target) {
                    TranslateTarget.TITLE -> it.copy(isTranslatingTitle = false)
                    TranslateTarget.DESCRIPTION -> it.copy(isTranslatingDesc = false)
                }
            }
        }
    }

    // ---------- 评论 ----------

    fun submitComment(content: String, parentId: Int? = null) {
        val episodeid = loadedEpisodeId ?: return
        if (content.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingComment = true) }
            when (val result = contentRepository.createComment(episodeid, content.trim(), parentId)) {
                is Result.Success -> {
                    val newComment = result.data.copy(replies = emptyList())
                    _uiState.update { state ->
                        val comments = if (parentId == null) {
                            listOf(newComment) + state.comments
                        } else {
                            state.comments.map { root ->
                                if (root.commentid == parentId) {
                                    root.copy(replies = root.replies + newComment)
                                } else {
                                    root.copy(replies = root.replies.map { reply ->
                                        if (reply.commentid == parentId) reply.copy(replies = reply.replies + newComment) else reply
                                    })
                                }
                            }
                        }
                        state.copy(comments = comments, isSubmittingComment = false)
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSubmittingComment = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isSubmittingComment = false) }
                    _toast.value = "网络错误，请重试"
                }
            }
        }
    }

    /** 点赞：乐观更新，失败回滚 */
    fun toggleCommentLike(commentid: Int) {
        val snapshot = _uiState.value.comments
        _uiState.update { it.copy(comments = it.comments.map { c -> c.mapLike(commentid, optimistic = true) }) }
        viewModelScope.launch {
            when (val result = contentRepository.toggleCommentLike(commentid)) {
                is Result.Success -> _uiState.update { s ->
                    s.copy(comments = s.comments.map { c ->
                        c.mapLike(commentid, liked = result.data)
                    })
                }
                is Result.Error -> {
                    _uiState.update { it.copy(comments = snapshot) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(comments = snapshot) }
                    _toast.value = "网络错误，请重试"
                }
            }
        }
    }

    /** 断点续播：跳过开头 30s 内的进度，接近结尾视为已听完从头播 */
    private fun resumePositionMs(episode: Episode): Long {
        val progress = episode.progressSeconds
        if (progress <= 30) return 0L
        val duration = episode.duration
        if (duration > 0 && progress >= duration - 15) return 0L
        return progress * 1000L
    }
}

/** 递归更新树中目标评论的点赞态（乐观翻转或以服务端结果为准） */
private fun Comment.mapLike(commentid: Int, liked: Boolean? = null, optimistic: Boolean = false): Comment {
    if (commentid == this.commentid) {
        return if (optimistic) {
            val newLiked = !isLiked
            copy(isLiked = newLiked, likesCount = likesCount + if (newLiked) 1 else -1)
        } else {
            val target = liked ?: isLiked
            copy(isLiked = target, likesCount = likesCount + if (target && !isLiked) 1 else if (!target && isLiked) -1 else 0)
        }
    }
    if (replies.isEmpty()) return this
    return copy(replies = replies.map { it.mapLike(commentid, liked, optimistic) })
}
