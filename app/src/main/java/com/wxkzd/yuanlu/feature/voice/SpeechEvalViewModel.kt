package com.wxkzd.yuanlu.feature.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.datastore.PracticeSettingsStore
import com.wxkzd.yuanlu.core.datastore.SettingsStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.core.recorder.WavRecorder
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.domain.model.PracticeSettings
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.SpeechPracticeRecord
import com.wxkzd.yuanlu.domain.model.Subtitle
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import com.wxkzd.yuanlu.feature.pronunciation.PronunciationUtils
import com.wxkzd.yuanlu.feature.vocabulary.WordLookupController
import com.wxkzd.yuanlu.feature.vocabulary.WordSheetState
import com.wxkzd.yuanlu.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** 页面内互斥的播放来源 */
enum class PlaybackKind { NONE, AI, ORIGINAL, SLOW, USER, WORD_US, WORD_UK, WORD_ORIGINAL, WORD_ME }

data class SpeechEvalUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val audioUrl: String? = null,
    val subtitles: List<Subtitle> = emptyList(),
    val index: Int = 0,
    val records: List<SpeechPracticeRecord> = emptyList(),
    val isTrialMode: Boolean = false,
    val phase: EvalPhase = EvalPhase.IDLE,
    val result: SpeechEvalResult? = null,
    /** 录音音量条（滚动窗口 0..100） */
    val amplitudes: List<Int> = emptyList(),
    /** 音素诊断选中的词下标（result.words） */
    val selectedWordIndex: Int? = null,
    val playing: PlaybackKind = PlaybackKind.NONE,
    val settings: PracticeSettings = PracticeSettings(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** 音标模式的逐词音标缓存（词键 = 去词上标点后小写，与渲染键同口径 → US 音标，已剥斜杠） */
    val ipaCache: Map<String, String> = emptyMap(),
    /** 盲读模式是否揭示原文：换句复位、新一轮评测结果产出后自动揭示（Web blindRevealed 同口径） */
    val blindRevealed: Boolean = false,
    /** 剧集标题（查词弹层底栏"来源"展示；加载时顺带拉取，失败静默） */
    val episodeTitle: String? = null
) {
    val current: Subtitle? get() = subtitles.getOrNull(index)
    val effectiveThreshold: Int get() = settings.effectivePassThreshold

    /** 过滤集内已练句数（按匹配口径去重，Web practicedInFilter 同口径） */
    val practicedCount: Int
        get() = subtitles.count { sub -> records.any { matchesRecord(sub, it) } }

    /** 当前句的最新历史评测记录（顶部操作栏"最近得分"入口依据，Web getLatestResult 同匹配口径） */
    val latestRecord: SpeechPracticeRecord?
        get() = current?.let { sub -> records.filter { matchesRecord(sub, it) }.maxByOrNull { it.recognitionid } }

    val progressPercent: Float
        get() = if (subtitles.isEmpty()) 0f else practicedCount.toFloat() / subtitles.size
}

/** 历史记录与字幕的匹配口径（Web：subtitleId 相同 || 文本相同且起点差 < 0.5s） */
private fun matchesRecord(sub: Subtitle, record: SpeechPracticeRecord): Boolean =
    record.subtitleId == sub.id ||
        (record.targetText == sub.textEn && kotlin.math.abs(record.targetStartTime - sub.start) < 0.5)

/**
 * 语音评测状态机：加载练习数据 → 句子过滤/切换 → 录音（16kHz WAV）→ 评测 → 结果/回放。
 * 页面内音频（AI 朗读/原声/慢速/录音回放/词级四路）统一由单个 MediaPlayer 互斥管理。
 */
@HiltViewModel
class SpeechEvalViewModel @Inject constructor(
    private val speechRepository: SpeechRepository,
    private val contentRepository: ContentRepository,
    private val practiceSettingsStore: PracticeSettingsStore,
    private val appSettingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeechEvalUiState())
    val uiState: StateFlow<SpeechEvalUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    /**
     * 原声/慢速播放的实时进度（MediaPlayer 绝对时间轴，与词级时间戳同轴）；
     * null = 不点亮扫光（AI 朗读/录音回放/词级播放/停止，对齐 Web highlightController
     * 的 -1 语义）。独立小流量 + StateFlow 去重，避免 50ms tick 复制整个 UiState。
     */
    private val _highlightPositionMs = MutableStateFlow<Long?>(null)
    val highlightPositionMs: StateFlow<Long?> = _highlightPositionMs.asStateFlow()

    /** 点词查词共享控制器（与精听页同一套查词/保存生词口径） */
    private val wordLookup = WordLookupController(
        contentRepository = contentRepository,
        tokenStore = tokenStore,
        scope = viewModelScope,
        onToast = { _toast.value = it }
    )

    /** 查词弹层状态；null 关闭 */
    val wordSheet: StateFlow<WordSheetState?> get() = wordLookup.wordSheet

    /** 已保存生词集合（登录后懒加载；句内已保存词 primary 标色用） */
    val savedWords: StateFlow<Set<String>?> get() = wordLookup.savedWords

    private var episodeId: String = ""

    private var recorder: WavRecorder? = null
    private var player: MediaPlayer? = null
    private var monitorJob: Job? = null
    private var advanceJob: Job? = null

    init {
        practiceSettingsStore.settingsFlow
            .onEach { settings ->
                _uiState.update { state ->
                    val filtered = if (allSubtitles.isEmpty()) state.subtitles
                    else applyFilters(allSubtitles, state.records, settings)
                    state.copy(
                        settings = settings,
                        subtitles = filtered,
                        index = state.index.coerceIn(0, filtered.lastIndex.coerceAtLeast(0))
                    )
                }
            }
            .launchIn(viewModelScope)
        appSettingsStore.themeModeFlow
            .onEach { mode -> _uiState.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)
    }

    /** 全量字幕的内存副本（过滤时用；loaded 后与 records 一起构成过滤输入） */
    private var allSubtitles: List<Subtitle> = emptyList()

    /**
     * 每句最近一次评测结果缓存（subtitleId → 结果）：
     * 结果对象同时携带本地录音路径（userAudioPath，"回放我的发音"/词级"我"依赖）
     * 与逐词明细（words + 音素，逐词纠错面板依赖）。UI 状态里的 result 只是当前句
     * 的投影，切句时据此字典恢复，对齐 Web previousResult 按句持久、跨卡片不丢的口径。
     */
    private val resultCache = HashMap<Int, SpeechEvalResult>()

    // ---------- 数据加载 ----------

    /**
     * @param focusSubtitleId 定位目标句（发音弱项本句子卡片直达）；null 从首句开始。
     * 加载成功后按 subtitleId 在过滤集中精确匹配，未命中（历史记录无 id 或被
     * 词数/只练未掌握过滤掉）回退首句。
     */
    fun load(episodeId: String, focusSubtitleId: Int? = null) {
        if (this.episodeId == episodeId && _uiState.value.subtitles.isNotEmpty()) return
        this.episodeId = episodeId
        resultCache.clear()
        _uiState.update { SpeechEvalUiState(settings = it.settings, themeMode = it.themeMode) }
        // 剧集标题仅供查词弹层底栏"来源"展示，静默失败不影响练习
        viewModelScope.launch {
            when (val r = contentRepository.getEpisode(episodeId)) {
                is Result.Success -> _uiState.update { it.copy(episodeTitle = r.data.title) }
                else -> Unit
            }
        }
        viewModelScope.launch {
            when (val result = speechRepository.getPracticeData(episodeId)) {
                is Result.Success -> {
                    allSubtitles = result.data.subtitles
                    val state = _uiState.value
                    val filtered = applyFilters(result.data.subtitles, result.data.records, state.settings)
                    // 弱项句子直达定位：过滤集内按 subtitleId 命中；被过滤条件排除时
                    // 定位到目标之后最近的可见句并提示（Web pendingSubtitleId 同口径）
                    val hit = focusSubtitleId?.let { id -> filtered.indexOfFirst { it.id == id } }
                    val focusIndex = when {
                        hit != null && hit >= 0 -> hit
                        focusSubtitleId != null -> {
                            _toast.value = "该句被当前过滤条件排除，已定位到最近的句子"
                            nearestVisibleIndexAfter(filtered, result.data.subtitles, focusSubtitleId)
                        }
                        else -> 0
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            audioUrl = result.data.audioUrl,
                            subtitles = filtered,
                            index = focusIndex,
                            records = result.data.records,
                            isTrialMode = result.data.isTrialMode
                        )
                    }
                    // 重进页面恢复定位句的历史结果（云端录音直链 + 深度明细回填，Web 同口径）
                    filtered.getOrNull(focusIndex)?.let { restoreFromHistory(it.id) }
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, loadError = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, loadError = "网络连接失败")
                }
            }
        }
    }

    /** 重试加载：保持当前练习句（而非重置回首句） */
    fun retry() { load(episodeId, _uiState.value.current?.id) }

    /** 目标句被过滤条件排除时的兜底：完整列表中目标之后（startSeconds ≥ 目标）最近的可见句，末句封底 */
    private fun nearestVisibleIndexAfter(filtered: List<Subtitle>, all: List<Subtitle>, subtitleId: Int): Int {
        val targetStart = all.firstOrNull { it.id == subtitleId }?.start ?: return 0
        val after = filtered.indexOfFirst { it.start >= targetStart }
        return if (after >= 0) after else filtered.lastIndex.coerceAtLeast(0)
    }

    /** Web ImmersiveSpeechPractice 的过滤口径：词数区间 + 只练未掌握 */
    private fun applyFilters(
        all: List<Subtitle>,
        records: List<SpeechPracticeRecord>,
        settings: PracticeSettings
    ): List<Subtitle> = all.filter { sub ->
        val wordCount = countWords(sub.textEn)
        if (wordCount < settings.minWords) return@filter false
        if (settings.maxWords < 50 && wordCount > settings.maxWords) return@filter false
        if (settings.onlyUnmastered) {
            val latest = latestRecordFor(sub, records)
            if (latest != null && latest.bestScore >= settings.effectivePassThreshold) return@filter false
        }
        true
    }

    /** 匹配该句的最新一次记录（recognitionid 自增，取最大即最新） */
    private fun latestRecordFor(sub: Subtitle, records: List<SpeechPracticeRecord>): SpeechPracticeRecord? =
        records.filter { matchesRecord(sub, it) }.maxByOrNull { it.recognitionid }

    /**
     * 从历史记录恢复某句的结果卡（重进页面 / 切到已练句时，对齐 Web previousResult 口径）：
     * 1. 先以记录分数字段 + 云端录音直链（userAudioUrl）立即恢复结果面——
     *    "回放我的发音"与词级"我"即可用（本地文件优先、云端回退）；
     * 2. 记录带 detailUrl 时异步拉 /api/speech/detail 回填逐词/音素明细
     *    （回填前结果面短暂显示"本句未返回逐词明细"，Web 同样先分数后明细）；
     * 3. 会话内若已产生更新的评测结果（cache 的 recognitionId 变化），历史回填作废。
     */
    private fun restoreFromHistory(subtitleId: Int) {
        val state = _uiState.value
        val sub = state.subtitles.firstOrNull { it.id == subtitleId } ?: return
        val record = latestRecordFor(sub, state.records) ?: return
        if (record.recognitionid == 0L) return

        val base = SpeechEvalResult(
            overallScore = record.bestScore,
            pronunciation = record.accuracyScore,
            fluency = record.fluencyScore ?: record.accuracyScore,
            integrity = record.integrityScore ?: record.accuracyScore,
            speed = record.speed ?: 0,
            words = emptyList(),
            recognitionId = record.recognitionid,
            userAudioUrl = record.userAudioUrl
        )
        if (resultCache[subtitleId] == null) {
            resultCache[subtitleId] = base
            val live = _uiState.value
            if (live.current?.id == subtitleId &&
                live.phase != EvalPhase.RECORDING && live.phase != EvalPhase.EVALUATING
            ) {
                _uiState.update { it.copy(phase = EvalPhase.RESULT, result = base, selectedWordIndex = null) }
            }
        }
        if (record.detailUrl == null) return
        viewModelScope.launch {
            when (val detail = speechRepository.getSpeechDetail(record.recognitionid)) {
                is Result.Success -> {
                    // 仅当缓存仍指向这条历史（未被更新的会话内评测覆盖）才回填
                    val cached = resultCache[subtitleId]
                    if (cached?.recognitionId == record.recognitionid && detail.data.words.isNotEmpty()) {
                        val merged = cached.copy(words = detail.data.words)
                        resultCache[subtitleId] = merged
                        val live = _uiState.value
                        if (live.current?.id == subtitleId &&
                            live.result?.recognitionId == record.recognitionid
                        ) {
                            _uiState.update { it.copy(result = merged) }
                        }
                    }
                }
                else -> Unit // 静默降级：旧记录无明细时保持基础结果（文案兜底）
            }
        }
    }

    private fun countWords(text: String): Int = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    // ---------- 设置 ----------

    fun updateSettings(transform: (PracticeSettings) -> PracticeSettings) {
        viewModelScope.launch { practiceSettingsStore.update(transform) }
    }

    /** 设置面板深浅色切换：直接写全局主题偏好（与 Web next-themes 联动一致） */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettingsStore.setThemeMode(mode) }
    }

    // ---------- 句子切换 ----------

    fun prev() { switchSentence(_uiState.value.index - 1) }
    fun next() { switchSentence(_uiState.value.index + 1) }
    fun selectSentence(index: Int) { switchSentence(index) }

    private fun switchSentence(index: Int) {
        val state = _uiState.value
        if (index < 0 || index > state.subtitles.lastIndex || index == state.index) return
        stopPlayback()
        advanceJob?.cancel()
        // 音标缓存按词复用，跨句保留；已评测句优先用会话内结果缓存，
        // 否则尝试从历史记录恢复（云端录音直链 + 深度明细回填，Web previousResult 同口径）
        val target = state.subtitles[index]
        val restored = resultCache[target.id]
        _uiState.update {
            it.copy(
                index = index,
                phase = if (restored != null) EvalPhase.RESULT else EvalPhase.IDLE,
                result = restored,
                selectedWordIndex = null,
                amplitudes = emptyList(),
                blindRevealed = false
            )
        }
        if (restored == null) restoreFromHistory(target.id)
        prefetchIpa(target)
    }

    // ---------- 字幕音标（IPA 文本模式） ----------

    /**
     * 预取当前句（或指定句）的逐词音标；字幕区切到音标模式时由 UI 触发，静默失败。
     * 取词键与渲染键（cleanWordKey）同口径：去词上标点 + 小写 + 去重；缺失词无上限
     * 逐个并发查词典（Web Promise.all 同口径）——若只取前 N 个，长句后半段会永远
     * 停留在英文原词（预取不会因缓存更新而重触发）。
     */
    fun prefetchIpa(subtitle: Subtitle? = null) {
        val sub = subtitle ?: _uiState.value.current ?: return
        if (_uiState.value.settings.textMode != com.wxkzd.yuanlu.domain.model.PracticeTextMode.IPA) return
        val missing = PronunciationUtils.ipaLookupKeys(sub.textEn)
            .filter { it !in _uiState.value.ipaCache }
        missing.forEach { word ->
            viewModelScope.launch {
                when (val r = contentRepository.lookupWord(word)) {
                    is Result.Success -> r.data.phoneticsUs?.let { ipa ->
                        // 词典音标自带斜杠包裹（/sʌm/），入缓存前剥离，句内按空格拼接展示
                        _uiState.update {
                            it.copy(
                                ipaCache = it.ipaCache +
                                    (word to PronunciationUtils.stripIpaSlashes(ipa))
                            )
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun cleanWord(word: String): String = word.trim { !it.isLetter() && it != '\'' }

    // ---------- 录音与评测 ----------

    /** 开始录音（UI 层已确保 RECORD_AUDIO 授权） */
    fun startRecording() {
        if (_uiState.value.phase == EvalPhase.RECORDING || _uiState.value.phase == EvalPhase.EVALUATING) return
        stopPlayback()
        advanceJob?.cancel()
        val rec = WavRecorder { amplitude ->
            _uiState.update { state ->
                state.copy(amplitudes = (state.amplitudes + amplitude).takeLast(28))
            }
        }
        if (!rec.start()) {
            _toast.value = "无法启动麦克风，请检查权限设置"
            return
        }
        recorder = rec
        _uiState.update {
            it.copy(phase = EvalPhase.RECORDING, result = null, selectedWordIndex = null, amplitudes = emptyList())
        }
    }

    /** 停止录音并提交评测（Web stopRecording：WAV → base64 → evaluate） */
    fun stopAndEvaluate() {
        val rec = recorder ?: return
        recorder = null
        _uiState.update { it.copy(phase = EvalPhase.EVALUATING, amplitudes = emptyList()) }
        viewModelScope.launch {
            val wav = withContext(Dispatchers.IO) { rec.stop() }
            if (wav == null) {
                _uiState.update { it.copy(phase = EvalPhase.IDLE) }
                _toast.value = "录音太短，请重试"
                return@launch
            }
            val state = _uiState.value
            val sub = state.current ?: run {
                _uiState.update { it.copy(phase = EvalPhase.IDLE) }
                return@launch
            }
            // 本地录音落盘，供"回放我的发音/我"的词切片播放
            val localPath = withContext(Dispatchers.IO) {
                runCatching {
                    File(appContext.cacheDir, "speech_${System.currentTimeMillis()}.wav")
                        .apply { writeBytes(wav) }
                        .absolutePath
                }.getOrNull()
            }
            when (val r = speechRepository.evaluate(episodeId, sub.id, sub.textEn, wav)) {
                is Result.Success -> {
                    val evaluated = r.data.copy(userAudioPath = localPath)
                    // 按句缓存最近一次结果：切句返回时恢复"回放我的发音"与逐词/音素明细
                    resultCache[sub.id] = evaluated
                    val newRecord = SpeechPracticeRecord(
                        recognitionid = evaluated.recognitionId ?: System.currentTimeMillis(),
                        accuracyScore = evaluated.pronunciation,
                        overallScore = evaluated.overallScore,
                        fluencyScore = evaluated.fluency,
                        integrityScore = evaluated.integrity,
                        speed = evaluated.speed,
                        targetText = sub.textEn,
                        targetStartTime = sub.start.toInt(),
                        subtitleId = sub.id,
                        recognitionDate = ""
                    )
                    _uiState.update {
                        it.copy(
                            phase = EvalPhase.RESULT,
                            result = evaluated,
                            selectedWordIndex = null,
                            // 盲读模式：新一轮评测结果产出即揭示原文对照（Web 同口径）
                            blindRevealed = true,
                            records = it.records + newRecord
                        )
                    }
                    maybeAutoAdvance(evaluated.overallScore)
                }
                is Result.Error -> {
                    _uiState.update { it.copy(phase = EvalPhase.IDLE) }
                    _toast.value = r.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(phase = EvalPhase.IDLE) }
                    _toast.value = "网络连接失败，请重试"
                }
            }
        }
    }

    /** 自动跳下一句（Web：达标后 1.5s） */
    private fun maybeAutoAdvance(score: Int) {
        val state = _uiState.value
        if (!state.settings.autoAdvance || score < state.effectiveThreshold) return
        advanceJob?.cancel()
        advanceJob = viewModelScope.launch {
            delay(1500)
            next()
        }
    }

    fun retryRecording() {
        advanceJob?.cancel()
        _uiState.update { it.copy(phase = EvalPhase.IDLE, result = null, selectedWordIndex = null) }
    }

    // ---------- 点词查词（共享控制器，与精听页完全一致） ----------

    /**
     * 点词查词：语音评测页对齐 Web 行为——不打断当前播放（原声片段到句尾自然停止），
     * 上下文取当前句原文/译文，时间戳优先词级起点。
     */
    fun onWordClick(rawWord: String, timestampSec: Double) {
        val sub = _uiState.value.current ?: return
        wordLookup.onWordClick(rawWord, sub.textEn, sub.textCn ?: "", timestampSec)
    }

    fun closeWordSheet() {
        wordLookup.closeWordSheet()
    }

    fun saveCurrentWord() {
        wordLookup.saveCurrentWord(episodeId)
    }

    // ---------- 结果区交互 ----------

    /** 点词展开音素诊断（Web：<85 分的词可点） */
    fun selectWord(index: Int?) {
        _uiState.update { it.copy(selectedWordIndex = index) }
    }

    /** 盲读模式"显示原文/重新遮挡"手动切换（Web setBlindRevealed） */
    fun toggleBlindReveal() {
        _uiState.update { it.copy(blindRevealed = !it.blindRevealed) }
    }

    /**
     * 查看当前句最近一次历史得分（顶部操作栏"最近得分"入口）：
     * 会话内有缓存直接翻到结果面；否则从历史记录恢复（含云端录音与明细回填）。
     */
    fun showLatestScore() {
        val state = _uiState.value
        if (state.phase == EvalPhase.RECORDING || state.phase == EvalPhase.EVALUATING) return
        val sub = state.current ?: return
        val cached = resultCache[sub.id]
        if (cached == null) {
            restoreFromHistory(sub.id)
            return
        }
        stopPlayback()
        advanceJob?.cancel()
        _uiState.update {
            it.copy(phase = EvalPhase.RESULT, result = cached, selectedWordIndex = null)
        }
    }

    // ---------- 播放（单一 MediaPlayer 互斥） ----------

    fun toggleAiReading() {
        val state = _uiState.value
        if (state.playing == PlaybackKind.AI) { stopPlayback(); return }
        val text = state.current?.textEn ?: return
        viewModelScope.launch {
            when (val r = contentRepository.fetchTtsAudioUrl(text)) {
                is Result.Success -> playUrl(r.data, PlaybackKind.AI)
                is Result.Error -> _toast.value = r.message
                Result.NetworkError -> _toast.value = "网络连接失败"
            }
        }
    }

    /** 原声片段播放（speed 1.0 / 0.75 慢速），起点优先词级时间戳 */
    fun playOriginal(speed: Float) {
        val state = _uiState.value
        val kind = if (speed < 1f) PlaybackKind.SLOW else PlaybackKind.ORIGINAL
        if (state.playing == kind) { stopPlayback(); return }
        val url = state.audioUrl
        val sub = state.current
        if (url.isNullOrBlank() || sub == null) {
            _toast.value = "原声音频不可用"
            return
        }
        val startSec = sub.words?.firstOrNull()?.start ?: sub.start
        val endSec = sub.words?.lastOrNull()?.end ?: sub.end
        playUrl(
            url, kind,
            startMs = (startSec * 1000).toInt(),
            endMs = (endSec * 1000).toInt(),
            speed = speed
        )
    }

    /** 回放我的发音（可只放词切片：startSec/endSec 为相对录音秒；本地文件优先、云端直链回退） */
    fun playUserAudio(startSec: Double? = null, endSec: Double? = null) {
        val state = _uiState.value
        if (state.playing == PlaybackKind.USER || state.playing == PlaybackKind.WORD_ME) { stopPlayback(); return }
        val source = state.result?.audioSource()
        if (source == null) {
            _toast.value = "暂无录音可回放"
            return
        }
        playUrl(
            source, PlaybackKind.USER,
            startMs = startSec?.let { (it * 1000).toInt() },
            endMs = endSec?.let { (it * 1000).toInt() }
        )
    }

    /** 有道词典发音（type 2=美音 1=英音，Web playDictAudio 同源） */
    fun playDictVoice(word: String, us: Boolean) {
        val kind = if (us) PlaybackKind.WORD_US else PlaybackKind.WORD_UK
        if (_uiState.value.playing == kind) { stopPlayback(); return }
        val url = "https://dict.youdao.com/dictvoice?audio=${android.net.Uri.encode(word)}&type=${if (us) 2 else 1}"
        playUrl(url, kind)
    }

    /** 词级原声：在字幕词级时间戳中定位选中词的绝对区间 */
    fun playWordOriginal(word: String) {
        val state = _uiState.value
        if (state.playing == PlaybackKind.WORD_ORIGINAL) { stopPlayback(); return }
        val url = state.audioUrl
        val words = state.current?.words
        if (url.isNullOrBlank() || words.isNullOrEmpty()) {
            _toast.value = "该句无词级时间戳"
            return
        }
        val target = cleanWord(word).lowercase()
        val hit = words.firstOrNull { cleanWord(it.word).lowercase() == target }
            ?: words.minByOrNull { cheapDistance(it.word.lowercase(), target) }
        if (hit == null) {
            _toast.value = "原声中未找到该词"
            return
        }
        playUrl(url, PlaybackKind.WORD_ORIGINAL, (hit.start * 1000).toInt(), (hit.end * 1000).toInt())
    }

    /** 词级"我"：回放录音中该词的切片（本地文件优先、云端直链回退） */
    fun playWordMe(wordIndex: Int) {
        val state = _uiState.value
        if (state.playing == PlaybackKind.WORD_ME) { stopPlayback(); return }
        val w = state.result?.words?.getOrNull(wordIndex) ?: return
        if (w.start == null || w.end == null) {
            _toast.value = "该词无切片时间"
            return
        }
        val source = state.result?.audioSource()
        if (source == null) {
            _toast.value = "暂无录音可回放"
            return
        }
        playUrl(
            source, PlaybackKind.WORD_ME,
            (w.start!! * 1000).toInt(), (w.end!! * 1000).toInt()
        )
    }

    /** 轻量相似度（非精确编辑距离，仅用于词级原声的模糊兜底匹配） */
    private fun cheapDistance(a: String, b: String): Int {
        if (a == b) return 0
        return kotlin.math.abs(a.length - b.length) + a.count { it !in b }
    }

    private fun playUrl(
        url: String,
        kind: PlaybackKind,
        startMs: Int? = null,
        endMs: Int? = null,
        speed: Float = 1f
    ) {
        stopPlayback()
        val mp = MediaPlayer()
        player = mp
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(url)
            mp.setOnPreparedListener {
                if (!viewModelScope.isActive) return@setOnPreparedListener
                if (startMs != null && startMs > 0) mp.seekTo(startMs)
                if (speed != 1f) {
                    runCatching { mp.playbackParams = mp.playbackParams.setSpeed(speed) }
                }
                mp.start()
                _uiState.update { s -> s.copy(playing = kind) }
                // 仅原声/慢速播放上报进度（驱动句内词级扫光；AI 朗读独立时间轴不点亮，
                // 对齐 Web highlightController 在 TTS 时返回 -1 的口径）
                val trackHighlight = kind == PlaybackKind.ORIGINAL || kind == PlaybackKind.SLOW
                monitorJob = viewModelScope.launch {
                    while (isActive) {
                        delay(50)
                        if (!mp.isPlaying) break
                        if (trackHighlight) _highlightPositionMs.value = mp.currentPosition.toLong()
                        if (endMs != null && mp.currentPosition >= endMs) {
                            runCatching { mp.stop() }
                            break
                        }
                    }
                    if (trackHighlight) _highlightPositionMs.value = null
                    _uiState.update { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
                }
            }
            mp.setOnCompletionListener {
                _highlightPositionMs.value = null
                _uiState.update { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
            }
            mp.setOnErrorListener { _, _, _ ->
                _highlightPositionMs.value = null
                _uiState.update { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
                true
            }
            mp.prepareAsync()
        } catch (_: Exception) {
            mp.release()
            if (player === mp) player = null
            _toast.value = "音频播放失败"
        }
    }

    fun stopPlayback() {
        monitorJob?.cancel()
        monitorJob = null
        _highlightPositionMs.value = null
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        player = null
        _uiState.update { if (it.playing != PlaybackKind.NONE) it.copy(playing = PlaybackKind.NONE) else it }
    }

    override fun onCleared() {
        recorder?.release()
        recorder = null
        monitorJob?.cancel()
        advanceJob?.cancel()
        player?.let { runCatching { it.release() } }
        player = null
        super.onCleared()
    }
}
