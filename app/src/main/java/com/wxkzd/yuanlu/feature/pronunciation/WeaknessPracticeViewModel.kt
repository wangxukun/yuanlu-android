package com.wxkzd.yuanlu.feature.pronunciation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.datastore.PracticeSettingsStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.core.recorder.WavRecorder
import com.wxkzd.yuanlu.domain.model.EvalPhase
import com.wxkzd.yuanlu.domain.model.SpeechEvalResult
import com.wxkzd.yuanlu.domain.model.WeakSentenceRecord
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import com.wxkzd.yuanlu.feature.voice.PlaybackKind
import com.wxkzd.yuanlu.feature.voice.SpeechEvalUiState
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

data class WeaknessPracticeUiState(
    val isLoading: Boolean = true,
    /** 403 = 弱项练习为 PRO 会员功能（整页锁定态） */
    val isLocked: Boolean = false,
    val loadError: String? = null,
    val records: List<WeakSentenceRecord> = emptyList(),
    val index: Int = 0,
    /** 已达标题目下标集（score ≥ 弱项分数线，顶栏"已达标"徽章依据） */
    val completed: Set<Int> = emptySet(),
    /** 复用语音评测卡的卡片态（subtitles = 弱项句子伪字幕，audioUrl = 当前集直链） */
    val card: SpeechEvalUiState = SpeechEvalUiState()
) {
    val current: WeakSentenceRecord? get() = records.getOrNull(index)
    val isLast: Boolean get() = index == records.lastIndex
    val isCompleted: Boolean get() = index in completed
    val progressPercent: Float
        get() = if (records.isEmpty()) 0f else (index + 1f) / records.size
}

/**
 * 发音闯关复习状态机（复刻 Web /library/pronunciation/practice）：
 * 加载弱项全量（PRO）→ 逐题录音评测（每题独立 episodeId 提交）→ 达标标记 → 上一题/下一题流转。
 * 评测卡直接复用 SpeechEvalCard：本 VM 维护兼容的 SpeechEvalUiState 子状态。
 */
@HiltViewModel
class WeaknessPracticeViewModel @Inject constructor(
    private val speechRepository: SpeechRepository,
    private val contentRepository: ContentRepository,
    private val practiceSettingsStore: PracticeSettingsStore,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(WeaknessPracticeUiState())
    val uiState: StateFlow<WeaknessPracticeUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    fun consumeToast() { _toast.value = null }

    private var recorder: WavRecorder? = null
    private var player: MediaPlayer? = null
    private var monitorJob: Job? = null

    init {
        // 练习设置（翻译开关/字号/文本模式等）变化时同步进卡片态
        practiceSettingsStore.settingsFlow
            .onEach { settings -> updateCard { it.copy(settings = settings) } }
            .launchIn(viewModelScope)
        load()
    }

    // ---------- 数据加载 ----------

    fun load() {
        _uiState.update { WeaknessPracticeUiState(card = it.card) }
        viewModelScope.launch {
            when (val r = speechRepository.getWeakErrors()) {
                is Result.Success -> {
                    val records = r.data
                    if (records.isEmpty()) {
                        _uiState.update { it.copy(isLoading = false) }
                        return@launch
                    }
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            records = records,
                            index = 0,
                            card = buildCard(records, 0, state.card.settings)
                        )
                    }
                    prefetchIpa(records.first())
                }
                is Result.Error -> {
                    // 403 = 弱项练习为 PRO 会员功能，整页进入锁定态
                    val locked = r.code == 403
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLocked = locked,
                            loadError = if (locked) null else r.message
                        )
                    }
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, loadError = "网络连接失败，请重试")
                }
            }
        }
    }

    fun retry() = load()

    /** 由弱项记录构建评测卡状态（Web mockSubtitle 口径：subtitleId/字幕上下文/精确结束秒） */
    private fun buildCard(
        records: List<WeakSentenceRecord>,
        index: Int,
        settings: com.wxkzd.yuanlu.domain.model.PracticeSettings
    ): SpeechEvalUiState {
        val current = records.getOrNull(index)
        return SpeechEvalUiState(
            isLoading = false,
            audioUrl = current?.episodeAudioUrl,
            subtitles = records.map { it.toSubtitle() },
            index = index,
            records = records.map { it.toPracticeRecord() },
            settings = settings
        )
    }

    private fun updateCard(transform: (SpeechEvalUiState) -> SpeechEvalUiState) {
        _uiState.update { it.copy(card = transform(it.card)) }
    }

    // ---------- 题目流转 ----------

    fun prev() = switchQuestion(_uiState.value.index - 1)
    fun next() = switchQuestion(_uiState.value.index + 1)

    private fun switchQuestion(index: Int) {
        val state = _uiState.value
        if (index < 0 || index > state.records.lastIndex || index == state.index) return
        stopPlayback()
        _uiState.update {
            it.copy(
                index = index,
                card = it.card.copy(
                    index = index,
                    audioUrl = state.records[index].episodeAudioUrl,
                    phase = EvalPhase.IDLE,
                    result = null,
                    selectedWordIndex = null,
                    amplitudes = emptyList(),
                    blindRevealed = false
                )
            )
        }
        prefetchIpa(state.records[index])
    }

    // ---------- 字幕音标（音标文本模式预取） ----------

    fun prefetchIpa(record: WeakSentenceRecord? = null) {
        val target = record ?: _uiState.value.current ?: return
        if (_uiState.value.card.settings.textMode != com.wxkzd.yuanlu.domain.model.PracticeTextMode.IPA) return
        val missing = wordsOf(target.targetText).filter { it.lowercase() !in _uiState.value.card.ipaCache }
        missing.take(6).forEach { word ->
            viewModelScope.launch {
                when (val r = contentRepository.lookupWord(cleanWord(word))) {
                    is Result.Success -> r.data.phoneticsUs?.let { ipa ->
                        updateCard { it.copy(ipaCache = it.ipaCache + (word.lowercase() to ipa)) }
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun wordsOf(text: String): List<String> =
        text.split(Regex("[\\s]+")).filter { it.any { c -> c.isLetter() } }

    private fun cleanWord(word: String): String = word.trim { !it.isLetter() && it != '\'' }

    // ---------- 录音与评测 ----------

    fun startRecording() {
        if (_uiState.value.card.phase == EvalPhase.RECORDING ||
            _uiState.value.card.phase == EvalPhase.EVALUATING
        ) return
        stopPlayback()
        val rec = WavRecorder { amplitude ->
            updateCard { it.copy(amplitudes = (it.amplitudes + amplitude).takeLast(28)) }
        }
        if (!rec.start()) {
            _toast.value = "无法启动麦克风，请检查权限设置"
            return
        }
        recorder = rec
        updateCard {
            it.copy(phase = EvalPhase.RECORDING, result = null, selectedWordIndex = null, amplitudes = emptyList())
        }
    }

    /** 停止录音并提交评测（每题用其所属剧集的 episodeId，后端同步更新弱项集与音素统计） */
    fun stopAndEvaluate() {
        val rec = recorder ?: return
        recorder = null
        updateCard { it.copy(phase = EvalPhase.EVALUATING, amplitudes = emptyList()) }
        viewModelScope.launch {
            val wav = withContext(Dispatchers.IO) { rec.stop() }
            if (wav == null) {
                updateCard { it.copy(phase = EvalPhase.IDLE) }
                _toast.value = "录音太短，请重试"
                return@launch
            }
            val state = _uiState.value
            val record = state.current ?: run {
                updateCard { it.copy(phase = EvalPhase.IDLE) }
                return@launch
            }
            // 本地录音落盘，供"回放我的发音"与词切片回放
            val localPath = withContext(Dispatchers.IO) {
                runCatching {
                    File(appContext.cacheDir, "weak_${System.currentTimeMillis()}.wav")
                        .apply { writeBytes(wav) }
                        .absolutePath
                }.getOrNull()
            }
            val sub = record.toSubtitle()
            when (val r = speechRepository.evaluate(record.episodeid.orEmpty(), sub.id, sub.textEn, wav)) {
                is Result.Success -> {
                    val evaluated = r.data.copy(userAudioPath = localPath)
                    // 达标标记（Web 硬编码 80，此处用可配置弱项分数线，默认同 80）
                    val threshold = state.card.settings.weakThreshold
                    val index = state.index
                    _uiState.update { s ->
                        s.copy(
                            completed = if (evaluated.overallScore >= threshold) s.completed + index else s.completed,
                            card = s.card.copy(
                                phase = EvalPhase.RESULT,
                                result = evaluated,
                                selectedWordIndex = null,
                                blindRevealed = true
                            )
                        )
                    }
                }
                is Result.Error -> {
                    updateCard { it.copy(phase = EvalPhase.IDLE) }
                    _toast.value = r.message
                }
                Result.NetworkError -> {
                    updateCard { it.copy(phase = EvalPhase.IDLE) }
                    _toast.value = "网络连接失败，请重试"
                }
            }
        }
    }

    fun retryRecording() {
        updateCard { it.copy(phase = EvalPhase.IDLE, result = null, selectedWordIndex = null) }
    }

    // ---------- 结果区交互 ----------

    fun selectWord(index: Int?) = updateCard { it.copy(selectedWordIndex = index) }

    fun toggleBlindReveal() = updateCard { it.copy(blindRevealed = !it.blindRevealed) }

    /** 查看本题弱项历史得分（进入结果面仅展示分数维度） */
    fun showLatestScore() {
        val state = _uiState.value
        if (state.card.phase == EvalPhase.RECORDING || state.card.phase == EvalPhase.EVALUATING) return
        val record = state.current ?: return
        stopPlayback()
        updateCard {
            it.copy(
                phase = EvalPhase.RESULT,
                result = SpeechEvalResult(
                    overallScore = record.lastScore,
                    pronunciation = record.accuracyScore,
                    fluency = record.accuracyScore,
                    integrity = record.accuracyScore,
                    speed = record.speed ?: 0,
                    words = emptyList()
                ),
                selectedWordIndex = null
            )
        }
    }

    // ---------- 播放（单一 MediaPlayer 互斥，与 SpeechEvalViewModel 同款） ----------

    fun toggleAiReading() {
        val state = _uiState.value
        if (state.card.playing == PlaybackKind.AI) { stopPlayback(); return }
        val text = state.current?.targetText ?: return
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
        if (state.card.playing == kind) { stopPlayback(); return }
        val url = state.card.audioUrl
        if (url.isNullOrBlank()) {
            _toast.value = "原声音频不可用"
            return
        }
        val sub = state.current?.toSubtitle() ?: return
        val startSec = sub.words?.firstOrNull()?.start ?: sub.start
        val endSec = sub.words?.lastOrNull()?.end ?: sub.end
        playUrl(
            url, kind,
            startMs = (startSec * 1000).toInt(),
            endMs = (endSec * 1000).toInt(),
            speed = speed
        )
    }

    fun playUserAudio(startSec: Double? = null, endSec: Double? = null) {
        val state = _uiState.value
        if (state.card.playing == PlaybackKind.USER || state.card.playing == PlaybackKind.WORD_ME) {
            stopPlayback(); return
        }
        val path = state.card.result?.userAudioPath
        if (path == null) {
            _toast.value = "暂无录音可回放"
            return
        }
        playUrl(
            File(path).toURI().toString(), PlaybackKind.USER,
            startMs = startSec?.let { (it * 1000).toInt() },
            endMs = endSec?.let { (it * 1000).toInt() }
        )
    }

    fun playDictVoice(word: String, us: Boolean) {
        val kind = if (us) PlaybackKind.WORD_US else PlaybackKind.WORD_UK
        if (_uiState.value.card.playing == kind) { stopPlayback(); return }
        val url = "https://dict.youdao.com/dictvoice?audio=${android.net.Uri.encode(word)}&type=${if (us) 2 else 1}"
        playUrl(url, kind)
    }

    fun playWordOriginal(word: String) {
        val state = _uiState.value
        if (state.card.playing == PlaybackKind.WORD_ORIGINAL) { stopPlayback(); return }
        val url = state.card.audioUrl
        val words = state.current?.subtitleWords
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

    fun playWordMe(wordIndex: Int) {
        val state = _uiState.value
        if (state.card.playing == PlaybackKind.WORD_ME) { stopPlayback(); return }
        val w = state.card.result?.words?.getOrNull(wordIndex) ?: return
        if (w.start == null || w.end == null) {
            _toast.value = "该词无切片时间"
            return
        }
        val path = state.card.result?.userAudioPath
        if (path == null) {
            _toast.value = "暂无录音可回放"
            return
        }
        playUrl(
            File(path).toURI().toString(), PlaybackKind.WORD_ME,
            (w.start!! * 1000).toInt(), (w.end!! * 1000).toInt()
        )
    }

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
                updateCard { s -> s.copy(playing = kind) }
                monitorJob = viewModelScope.launch {
                    while (isActive) {
                        delay(50)
                        if (!mp.isPlaying) break
                        if (endMs != null && mp.currentPosition >= endMs) {
                            runCatching { mp.stop() }
                            break
                        }
                    }
                    updateCard { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
                }
            }
            mp.setOnCompletionListener {
                updateCard { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
            }
            mp.setOnErrorListener { _, _, _ ->
                updateCard { s -> if (s.playing == kind) s.copy(playing = PlaybackKind.NONE) else s }
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
        player?.let { mp ->
            runCatching {
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        }
        player = null
        updateCard { if (it.playing != PlaybackKind.NONE) it.copy(playing = PlaybackKind.NONE) else it }
    }

    override fun onCleared() {
        recorder?.release()
        recorder = null
        monitorJob?.cancel()
        player?.let { runCatching { it.release() } }
        player = null
        super.onCleared()
    }
}
