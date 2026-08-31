package com.wxkzd.yuanlu.feature.intensive

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.media.LoopMode
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.media.PlayerState
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.DictEntry
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.repository.ContentRepository
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

/** 查词弹层状态（对齐 Web VocabularyModal 所需数据） */
data class WordSheetState(
    val word: String,
    val contextEn: String,
    val contextCn: String,
    val timestampSec: Int,
    val entry: DictEntry? = null,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false
)

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
    /** 精读 / 听写模式（听写：0.8 倍速 + 当前句自动循环 + 拼写校验） */
    val transcriptMode: TranscriptMode = TranscriptMode.READ,
    /** 查词弹层；null 关闭 */
    val wordSheet: WordSheetState? = null
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

    /** 已保存生词缓存（登录后懒加载，用于查词弹层已保存态） */
    private var savedWords: Set<String>? = null

    init {
        // 循环边界：单句循环锁定句；听写模式下自动跟随当前句（对齐 Web loopTarget 逻辑）
        viewModelScope.launch {
            combine(
                playerController.playerState,
                _uiState
            ) { p, ui ->
                val positionSec = p.currentPosition / 1000.0
                val activeIdx = ui.subtitles.indexOfFirst { positionSec >= it.start && positionSec <= it.end }
                val target = ui.loopingIndex
                    ?: if (ui.transcriptMode == TranscriptMode.DICTATE && activeIdx >= 0) activeIdx else null
                Triple(p.isPlaying, p.currentPosition, target?.let { ui.subtitles.getOrNull(it) })
            }.distinctUntilChanged().collect { (isPlaying, positionMs, loopSub) ->
                if (isPlaying && loopSub != null &&
                    positionMs >= loopSub.end * 1000 &&
                    SystemClock.elapsedRealtime() - lastJumpAtMs > 500
                ) {
                    playerController.seekTo((loopSub.start * 1000).toLong())
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
            _uiState.update {
                it.copy(isLoading = false, episode = episode, audioUrl = audioUrl, subtitles = subtitles)
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
     * 听写模式下当前句自动循环（见 init 收集器的 DICTATE 分支）。
     */
    fun setTranscriptMode(mode: TranscriptMode) {
        val previous = _uiState.value.transcriptMode
        if (previous == mode) return
        if (mode == TranscriptMode.DICTATE) {
            rateBeforeDictate = playerState.value.playbackRate
            playerController.setPlaybackRate(0.8f)
        } else if (previous == TranscriptMode.DICTATE) {
            // 恢复进入听写前的倍速（用户原本可能是 1.25x/1.5x）
            playerController.setPlaybackRate(rateBeforeDictate)
        }
        _uiState.update { it.copy(transcriptMode = mode) }
    }

    /** 听写完成整句：跳下一句继续（对齐 Web handleDictationSuccess）；末句则暂停 */
    fun onDictationSuccess() {
        val ui = _uiState.value
        val active = activeSubtitleIndex.value
        if (active < 0) return
        val next = ui.subtitles.getOrNull(active + 1)
        lastJumpAtMs = SystemClock.elapsedRealtime()
        if (next != null) {
            playerController.seekTo((next.start * 1000).toLong())
        } else {
            playerController.pause()
        }
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

    // ---------- 点词查词（对齐 Web handleWordClick / handleSaveVocabulary） ----------

    /** 点词：清理标点后暂停播放并打开查词弹层（游客可查，保存需登录） */
    fun onWordClick(rawWord: String, contextEn: String, contextCn: String, timestampSec: Double) {
        val cleanWord = rawWord.replace(Regex("[.,!?;:\\\"()\\[\\]]"), "").trim()
        if (cleanWord.isEmpty()) return
        if (playerState.value.isPlaying) playerController.pause()

        val alreadySaved = savedWords?.contains(cleanWord.lowercase()) ?: false
        _uiState.update {
            it.copy(
                wordSheet = WordSheetState(
                    word = cleanWord,
                    contextEn = contextEn,
                    contextCn = contextCn,
                    timestampSec = timestampSec.toInt(),
                    isSaved = alreadySaved
                )
            )
        }
        viewModelScope.launch {
            when (val result = contentRepository.lookupWord(cleanWord)) {
                is Result.Success -> _uiState.update { s ->
                    s.copy(wordSheet = s.wordSheet?.copy(entry = result.data, isLoading = false))
                }
                is Result.Error -> {
                    _uiState.update { s ->
                        s.copy(wordSheet = s.wordSheet?.copy(isLoading = false))
                    }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { s -> s.copy(wordSheet = s.wordSheet?.copy(isLoading = false)) }
                    _toast.value = "网络错误，请重试"
                }
            }
        }
        // 登录用户懒加载已保存生词集合
        if (savedWords == null) {
            viewModelScope.launch {
                if (tokenStore.getToken() == null) return@launch
                when (val result = contentRepository.getVocabularyWords()) {
                    is Result.Success -> {
                        savedWords = result.data
                        _uiState.update { s ->
                            s.copy(wordSheet = s.wordSheet?.copy(
                                isSaved = result.data.contains(cleanWord.lowercase())
                            ))
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun closeWordSheet() {
        _uiState.update { it.copy(wordSheet = null) }
    }

    /** 保存当前查词单词进生词本（对齐 Web handleSaveVocabulary 的字段拼装） */
    fun saveCurrentWord() {
        val ui = _uiState.value
        val sheet = ui.wordSheet ?: return
        if (sheet.isSaving || sheet.isSaved) return
        val episodeid = loadedEpisodeId ?: return
        val definition = sheet.entry?.definitions
            ?.joinToString("; ") { "[${it.pos}] ${it.meaningCn}" }
            .orEmpty()
        viewModelScope.launch {
            // 登录检查（DataStore 挂起读取）
            if (tokenStore.getToken() == null) {
                _toast.value = "请先登录后再保存生词"
                return@launch
            }
            _uiState.update { s -> s.copy(wordSheet = s.wordSheet?.copy(isSaving = true)) }
            val speakUrl = sheet.entry?.audioUs ?: sheet.entry?.audioUk ?: ""
            when (val result = contentRepository.addVocabulary(
                word = sheet.word,
                definition = definition,
                contextSentence = sheet.contextEn,
                translation = sheet.contextCn,
                episodeid = episodeid,
                timestampSec = sheet.timestampSec,
                speakUrl = speakUrl
            )) {
                is Result.Success -> {
                    savedWords = (savedWords ?: emptySet()) + sheet.word.lowercase()
                    _uiState.update { s -> s.copy(wordSheet = s.wordSheet?.copy(isSaving = false, isSaved = true)) }
                    _toast.value = "已加入生词本"
                }
                is Result.Error -> {
                    _uiState.update { s -> s.copy(wordSheet = s.wordSheet?.copy(isSaving = false)) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { s -> s.copy(wordSheet = s.wordSheet?.copy(isSaving = false)) }
                    _toast.value = "网络错误，请重试"
                }
            }
        }
    }

    // ---------- 播放控制直通全局 PlayerController（与迷你条/全屏播放器实时同步） ----------

    fun seekToSubtitle(subtitle: Subtitle) {
        lastJumpAtMs = SystemClock.elapsedRealtime()
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
