package com.wxkzd.yuanlu.feature.intensive

/**
 * 精听页核心算法（对齐 Web 端口径）：
 * - 听写：连续输入按目标词"清洗后长度"逐词切块，标点词自动跳过；
 * - 精读 SRT：按空白分词给出字符区间，供无词级时间戳的字幕绑定点词查词。
 */
internal fun cleanDictation(s: String): String =
    s.lowercase().filterNot { it in ",.!?:;\"()[]" }

/** 目标句分词（按空白切分，保留词上原有标点用于展示） */
internal fun dictationTargets(textEn: String): List<String> =
    textEn.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

/**
 * SRT/纯文本字幕的点词分词（对齐 Web split(/(\s+)/) 点击口径）：
 * 返回单词及其在「单词 + 单空格」拼接文本中的字符区间（start/end 为闭区间下标），
 * 空白片段不可点（与 Web 一致），标点跟随在词上、由查词入口统一清洗。
 */
internal data class SrtWordToken(val word: String, val start: Int, val end: Int)

internal fun srtWordTokens(textEn: String): List<SrtWordToken> {
    val tokens = mutableListOf<SrtWordToken>()
    var cursor = 0
    for (word in dictationTargets(textEn)) {
        tokens.add(SrtWordToken(word, cursor, cursor + word.length - 1))
        cursor += word.length + 1 // 词后拼一个空格（末词多计的 1 不再消费）
    }
    return tokens
}

/** 把去掉空白的连续输入按目标词清洗后长度切块（对齐 Web inputWords） */
internal fun chunkDictationInput(raw: String, targets: List<String>): List<String> {
    val rawInput = raw.filterNot { it.isWhitespace() }
    val words = mutableListOf<String>()
    var cursor = 0
    for (target in targets) {
        val len = cleanDictation(target).length
        if (len == 0) {
            words.add("")
            continue
        }
        if (cursor >= rawInput.length) break
        val chunk = rawInput.substring(cursor, minOf(cursor + len, rawInput.length))
        words.add(chunk)
        cursor += chunk.length
    }
    return words
}

/** 整句是否听写正确：块数齐 + 逐块清洗后小写相等（标点词恒过） */
internal fun isDictationCorrect(targets: List<String>, inputWords: List<String>): Boolean {
    if (inputWords.size != targets.size) return false
    return targets.indices.all { i ->
        val cw = cleanDictation(targets[i])
        cw.isEmpty() || cw == (inputWords[i]).lowercase()
    }
}
