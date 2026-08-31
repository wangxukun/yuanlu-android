package com.wxkzd.yuanlu.feature.intensive

/**
 * 听写模式核心算法（对齐 Web DictationItem 的 tokenization / clean / inputWords 口径）：
 * 连续输入按目标词"清洗后长度"逐词切块，标点词自动跳过。
 */
internal fun cleanDictation(s: String): String =
    s.lowercase().filterNot { it in ",.!?:;\"()[]" }

/** 目标句分词（按空白切分，保留词上原有标点用于展示） */
internal fun dictationTargets(textEn: String): List<String> =
    textEn.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

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
