package com.wxkzd.yuanlu.feature.intensive

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.media.LoopMode
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.media.PlayerState
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.feature.vocabulary.WordLookupController
import com.wxkzd.yuanlu.feature.vocabulary.WordSheetState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 精听模式：精读（跟读）/ 听写（DictationItem），对齐 Web transcriptMode */
enum class TranscriptMode { READ, DICTATE }

/** 精听页 UI 状态：剧集 + 字幕（+音频直链）+ 阅读辅助开关 */
data class IntensiveUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val episode: Episode? = null,
    /** null 表示音频不可用（游客仅 3 分钟字幕预览，起播被跳过） */
    val audioUrl: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    /** 是否显示中文译文（右下角翻译浮动按钮切换，对齐 Web showTranslation，默认隐藏） */
    val showTranslation: Boolean = false,
    /**
     * 单句循环锁定的字幕下标（对齐 Web loopingIndex）：null 关闭；
     * 锁定句播完（越过 end）自动跳回 start 循环。
     */
    val loopingIndex: Int? = null,
    /**
     * 听写模式当前句下标（显式状态，而非播放位置推导）：
     * 拼写正确后 +1 流转 / 点句跳转跟随；进入听写时按播放位置初始化。
     * 显式化后循环不受字幕间 gap 影响（位置落在 gap 时旧逻辑会丢失循环目标）。
     */
    val dictationIndex: Int? = null,
    /** 本轮听写已完成（末句拼写正确后置位，触发结算弹层并停止循环） */
    val dictationFinished: Boolean = false,
    /** 精读 / 听写模式（听写：0.8 倍速 + 当前句自动循环 + 拼写校验） */
    val transcriptMode: TranscriptMode = TranscriptMode.READ
)

/**
 * 精听页 ViewModel（对齐 Web 端「精读/听写」InteractiveTranscript 全量能力）：
 * 进度同步高亮 + 词级扫光 + 句/集级循环 + 译文开关 + 点词查词保存生词 + 听写校验。
 *
 * 播放能力直接复用全局单例 [PlayerController]（Media3 + StateFlow），
 * 因此从全屏播放器携带 episodeId + playbackPosition 进入时：
 * - 已在播该剧集 → 延续当前进度，仅补精听模式标记；
 * - 未在播 → 以携带进度（为 0 时按服务端断点）以精听模式起播。
 */
@HiltViewModel
class IntensiveListeningViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val contentRepository: ContentRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    val playerState: StateFlow<PlayerState> = playerController.playerState

    private val _uiState = MutableStateFlow(IntensiveUiState())
    val uiState: StateFlow<IntensiveUiState> = _uiState.asStateFlow()

    /** 一次性提示（Toast），消费后置空 */
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    /** 当前播放位置对应的字幕下标（与剧集详情页同一套时间对齐口径） */
    val activeSubtitleIndex: StateFlow<Int> = combine(
        playerController.playerState.map { it.currentPosition },
        _uiState.map { it.subtitles }
    ) { positionMs, subs ->
        val positionSec = positionMs / 1000.0
        subs.indexOfFirst { positionSec >= it.start && positionSec <= it.end }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    /** 手动跳播时间戳：循环回跳需避开刚点句后的 500ms（对齐 Web lastJumpTimeRef 口径） */
    private var lastJumpAtMs = 0L

    /** 进入听写模式前的用户倍速（切回精读时恢复，而非硬编码 1x） */
    private var rateBeforeDictate = 1f

    /** 点词查词共享控制器（与语音评测页同一套查词/保存口径） */
    private val wordLookup = WordLookupController(
        contentRepository = contentRepository,
        tokenStore = tokenStore,
        scope = viewModelScope,
        onToast = { _toast.value = it }
    )

    /** 查词弹层状态；null 关闭 */
    val wordSheet: StateFlow<WordSheetState?> get() = wordLookup.wordSheet

    init {
        // 循环边界：单句循环锁定句；听写模式跟随显式 dictationIndex（对齐 Web loopTarget 逻辑）。
        // 到达 endTime 未通过校验 → seek 回 startTime 无限循环；viewModelScope 随页面销毁自动取消收集。
        viewModelScope.launch {
            combine(
                playerController.playerState,
                _uiState
            ) { p, ui ->
                val target = ui.loopingIndex
                    ?: ui.dictationIndex.takeIf {
                        ui.transcriptMode == TranscriptMode.DICTATE && !ui.dictationFinished
                    }
                Triple(p.isPlaying, p.currentPosition, target?.let { ui.subtitles.getOrNull(it) })
            }.distinctUntilChanged().collect { (isPlaying, positionMs, loopSub) ->
                // end <= start 的脏字幕会触发每次 tick 都回跳的 seek 风暴（听感"快进"），跳过
                if (isPlaying && loopSub != null && loopSub.end > loopSub.start &&
                    positionMs >= loopSub.end * 1000 &&
                    SystemClock.elapsedRealtime() - lastJumpAtMs > 500
                ) {
                    playerController.seekTo((loopSub.start * 1000).toLong())
                    // 自动回跳也计入节流窗口，避免 seek 与 200ms 进度 tick 互相追逐
                    lastJumpAtMs = SystemClock.elapsedRealtime()
                }
            }
        }
    }

    /**
     * Nav3 entry 无参数注入，由路由层 LaunchedEffect 调用；同剧集重复进入幂等，
     * 但 ensurePlayback 每次都会执行（保证精听标记与起播兜底）。
     */
    fun load(episodeid: String, playbackPositionMs: Long) {
        if (loadedEpisodeId == episodeid) {
            ensurePlayback(playbackPositionMs)
            return
        }
        loadedEpisodeId = episodeid
        _uiState.update { IntensiveUiState(isLoading = true) }
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

            // 字幕接口失败不阻断页面（列表区展示「暂无字幕」），音频直链一并无则不起播
            var audioUrl: String? = null
            var subtitles: List<Subtitle> = emptyList()
            when (val subtitlesResult = contentRepository.getSubtitles(episodeid)) {
                is Result.Success -> {
                    subtitles = subtitlesResult.data.subtitles
                    audioUrl = subtitlesResult.data.audioUrl
                }
                else -> Unit
            }
            _uiState.update { s ->
                val next = s.copy(isLoading = false, episode = episode, audioUrl = audioUrl, subtitles = subtitles)
                // 边界：用户先切到听写 Tab、字幕异步到达时补初始化当前句，避免无循环目标
                if (next.transcriptMode == TranscriptMode.DICTATE && next.dictationIndex == null) {
                    next.copy(dictationIndex = nearestIndexFor(subtitles, playerState.value.currentPosition))
                } else {
                    next
                }
            }
            ensurePlayback(playbackPositionMs)
        }
    }

    private var loadedEpisodeId: String? = null

    fun retry() {
        loadedEpisodeId?.let { load(it, playerState.value.currentPosition) }
    }

    fun consumeToast() {
        _toast.value = null
    }

    // ---------- 阅读辅助开关（右下角浮动按钮） ----------

    /** 显示/隐藏中文译文 */
    fun toggleTranslation() {
        _uiState.update { it.copy(showTranslation = !it.showTranslation) }
    }

    // ---------- 精读 / 听写模式 ----------

    /**
     * 切换精读/听写（对齐 Web）：听写降为 0.8 倍速、精读恢复进入听写前的倍速；
     * 听写模式下当前句强制循环（见 init 收集器）。
     */
    fun setTranscriptMode(mode: TranscriptMode) {
        val previous = _uiState.value.transcriptMode
        if (previous == mode) return
        if (mode == TranscriptMode.DICTATE) {
            rateBeforeDictate = playerState.value.playbackRate
            playerController.setPlaybackRate(0.8f)
            _uiState.update {
                it.copy(
                    transcriptMode = mode,
                    dictationIndex = nearestDictationIndex(),
                    dictationFinished = false
                )
            }
        } else if (previous == TranscriptMode.DICTATE) {
            // 恢复进入听写前的倍速（用户原本可能是 1.25x/1.5x）
            playerController.setPlaybackRate(rateBeforeDictate)
            _uiState.update {
                it.copy(transcriptMode = mode, dictationIndex = null, dictationFinished = false)
            }
        }
    }

    /**
     * 听写完成整句（对齐 Web handleDictationSuccess + 越界保护）：
     * 正确后切换到下一句 [start, end] 区间并保证起播；末句则停止循环、
     * 暂停播放并进入听写完成结算态。
     */
    fun onDictationSuccess(index: Int) {
        val ui = _uiState.value
        if (ui.transcriptMode != TranscriptMode.DICTATE) return
        if (index !in ui.subtitles.indices) return
        lastJumpAtMs = SystemClock.elapsedRealtime()
        val next = ui.subtitles.getOrNull(index + 1)
        if (next != null) {
            _uiState.update { it.copy(dictationIndex = index + 1, dictationFinished = false) }
            playerController.seekTo((next.start * 1000).toLong())
            playerController.resume() // 用户可能手动暂停过，成功流转必须起播下一句
        } else {
            // 末句：dictationFinished 置位即解除循环目标（见 init 收集器），再显式暂停兜底
            _uiState.update { it.copy(dictationFinished = true) }
            playerController.pause()
        }
    }

    /** 结算弹层「再来一轮」：回到第一句重新开始听写 */
    fun restartDictation() {
        val first = _uiState.value.subtitles.firstOrNull() ?: return
        lastJumpAtMs = SystemClock.elapsedRealtime()
        _uiState.update { it.copy(dictationIndex = 0, dictationFinished = false) }
        playerController.seekTo((first.start * 1000).toLong())
        playerController.resume()
    }

    /** 进入听写时的起始句：优先播放位置所在句，其次下一句，兜底末句 */
    private fun nearestDictationIndex(): Int? =
        nearestIndexFor(_uiState.value.subtitles, playerState.value.currentPosition)

    private fun nearestIndexFor(subs: List<Subtitle>, positionMs: Long): Int? {
        if (subs.isEmpty()) return null
        val posSec = positionMs / 1000.0
        val active = subs.indexOfFirst { posSec >= it.start && posSec <= it.end }
        if (active >= 0) return active
        val next = subs.indexOfFirst { it.start > posSec }
        return if (next >= 0) next else subs.lastIndex
    }

    // ---------- 循环控制 ----------

    /**
     * 单句循环：点击字幕行右下角循环图标，锁定/解锁该句。
     * 锁定后由 init 中的收集器负责越界回跳。
     */
    fun toggleSentenceLoop(index: Int) {
        _uiState.update {
            it.copy(loopingIndex = if (it.loopingIndex == index) null else index)
        }
    }

    /**
     * 单集循环 ↔ 多集顺序播放（右下角浮动按钮）：
     * REPEAT_ONE 循环本集 / REPEAT_OFF 顺序播放（无队列时播完即停）。
     */
    fun toggleEpisodeLoopMode() {
        val next = if (playerState.value.loopMode == LoopMode.NONE) LoopMode.ONE else LoopMode.NONE
        playerController.setLoopMode(next)
    }

    // ---------- 点词查词（共享控制器，对齐 Web handleWordClick / handleSaveVocabulary） ----------

    /** 点词：跟读中先暂停播放，再交给共享查词控制器打开弹层 */
    fun onWordClick(rawWord: String, contextEn: String, contextCn: String, timestampSec: Double) {
        if (playerState.value.isPlaying) playerController.pause()
        wordLookup.onWordClick(rawWord, contextEn, contextCn, timestampSec)
    }

    fun closeWordSheet() {
        wordLookup.closeWordSheet()
    }

    /** 保存当前查词单词进生词本 */
    fun saveCurrentWord() {
        val episodeid = loadedEpisodeId ?: return
        wordLookup.saveCurrentWord(episodeid)
    }

    // ---------- 播放控制直通全局 PlayerController（与迷你条/全屏播放器实时同步） ----------

    fun seekToSubtitle(subtitle: Subtitle) {
        lastJumpAtMs = SystemClock.elapsedRealtime()
        // 听写模式手动切句：显式同步 dictationIndex，循环目标随句切换（输入槽位随 subtitle.id 重建清空）
        _uiState.update { ui ->
            if (ui.transcriptMode == TranscriptMode.DICTATE) {
                val idx = ui.subtitles.indexOfFirst { it.id == subtitle.id }
                if (idx >= 0) ui.copy(dictationIndex = idx, dictationFinished = false) else ui
            } else {
                ui
            }
        }
        playerController.seekTo((subtitle.start * 1000).toLong())
    }

    private fun ensurePlayback(playbackPositionMs: Long) {
        val state = _uiState.value
        val episode = state.episode ?: return
        val audioUrl = state.audioUrl
        if (audioUrl.isNullOrBlank()) return // 游客/无权限：不起播，仅字幕预览
        if (playerState.value.currentEpisode?.episodeid == episode.episodeid) {
            playerController.setIntensiveMode(true)
        } else {
            playerController.play(
                episode = episode.copy(audioUrl = audioUrl),
                startPositionMs = startPositionMs(episode, playbackPositionMs),
                intensive = true
            )
        }
    }

    /** 断点续播口径与剧集详情页一致：携带进度优先，为 0 时用服务端进度（跳过 <30s，接近结尾重播） */
    private fun startPositionMs(episode: Episode, requested: Long): Long {
        if (requested > 0) return requested
        val progress = episode.progressSeconds
        if (progress <= 30) return 0L
        val duration = episode.duration
        if (duration > 0 && progress >= duration - 15) return 0L
        return progress * 1000L
    }
}
