package com.wxkzd.yuanlu.feature.vocabulary

import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 点词查词共享控制器（精听 / 语音评测两页共用，对齐 Web handleWordClick /
 * handleSaveVocabulary 全量口径）：
 * - 点词：清洗标点 → 打开弹层 → 查词典回填释义/音标/发音/词源；
 * - 登录用户懒加载已保存生词集合：驱动弹层收藏态与句内已保存词 primary 标色
 *   （Web globalVocabWords 同语义）；
 * - 保存生词：拼装 definition/speakUrl 调 /api/vocabulary/add，成功回写本地缓存。
 *
 * 播放控制（如精听点词时暂停跟读）由宿主 ViewModel 在调用前自行处理，
 * 控制器本身不触碰任何播放器。
 */
class WordLookupController(
    private val contentRepository: ContentRepository,
    private val tokenStore: TokenStore,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit
) {

    private val _wordSheet = MutableStateFlow<WordSheetState?>(null)
    val wordSheet: StateFlow<WordSheetState?> = _wordSheet.asStateFlow()

    /** 已保存生词集合（小写）；null = 尚未加载（登录后首次点词时懒加载） */
    private val _savedWords = MutableStateFlow<Set<String>?>(null)
    val savedWords: StateFlow<Set<String>?> = _savedWords.asStateFlow()

    /** 点词查词：游客可查，保存需登录 */
    fun onWordClick(rawWord: String, contextEn: String, contextCn: String, timestampSec: Double) {
        val cleanWord = rawWord.replace(Regex("[.,!?;:\\\"()\\[\\]]"), "").trim()
        if (cleanWord.isEmpty()) return

        val alreadySaved = _savedWords.value?.contains(cleanWord.lowercase()) ?: false
        _wordSheet.value = WordSheetState(
            word = cleanWord,
            contextEn = contextEn,
            contextCn = contextCn,
            timestampSec = timestampSec.toInt(),
            isSaved = alreadySaved
        )
        scope.launch {
            when (val result = contentRepository.lookupWord(cleanWord)) {
                is Result.Success -> _wordSheet.update { it?.copy(entry = result.data, isLoading = false) }
                is Result.Error -> {
                    _wordSheet.update { it?.copy(isLoading = false) }
                    onToast(result.message)
                }
                Result.NetworkError -> {
                    _wordSheet.update { it?.copy(isLoading = false) }
                    onToast("网络错误，请重试")
                }
            }
        }
        // 登录用户懒加载已保存生词集合（仅修正同词弹层的收藏态，不覆盖后来打开的词）
        if (_savedWords.value == null) {
            scope.launch {
                if (tokenStore.getToken() == null) return@launch
                when (val result = contentRepository.getVocabularyWords()) {
                    is Result.Success -> {
                        _savedWords.value = result.data
                        _wordSheet.update { sheet ->
                            if (sheet?.word?.equals(cleanWord, ignoreCase = true) == true && !sheet.isSaved) {
                                sheet.copy(isSaved = result.data.contains(cleanWord.lowercase()))
                            } else {
                                sheet
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    fun closeWordSheet() {
        _wordSheet.value = null
    }

    /** 保存当前查词单词进生词本（对齐 Web handleSaveVocabulary 的字段拼装） */
    fun saveCurrentWord(episodeid: String) {
        val sheet = _wordSheet.value ?: return
        if (sheet.isSaving || sheet.isSaved) return
        val definition = sheet.entry?.definitions
            ?.joinToString("; ") { "[${it.pos}] ${it.meaningCn}" }
            .orEmpty()
        scope.launch {
            if (tokenStore.getToken() == null) {
                onToast("请先登录后再保存生词")
                return@launch
            }
            _wordSheet.update { it?.takeIf { s -> s.word == sheet.word }?.copy(isSaving = true) }
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
                    _savedWords.value = (_savedWords.value ?: emptySet()) + sheet.word.lowercase()
                    _wordSheet.update { it?.takeIf { s -> s.word == sheet.word }?.copy(isSaving = false, isSaved = true) }
                    onToast("已加入生词本")
                }
                is Result.Error -> {
                    _wordSheet.update { it?.takeIf { s -> s.word == sheet.word }?.copy(isSaving = false) }
                    onToast(result.message)
                }
                Result.NetworkError -> {
                    _wordSheet.update { it?.takeIf { s -> s.word == sheet.word }?.copy(isSaving = false) }
                    onToast("网络错误，请重试")
                }
            }
        }
    }
}
