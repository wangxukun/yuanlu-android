package com.wxkzd.yuanlu.feature.intensive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 听写模式核心算法单测（对齐 Web DictationItem 口径）：
 * 清洗、分词、按目标词长切块、整句正确性判定。
 */
class DictationUtilsTest {

    @Test
    fun `cleanDictation 小写并去常见标点`() {
        assertEquals("smells", cleanDictation("Smells,"))
        assertEquals("everywhere", cleanDictation("everywhere."))
        assertEquals("don't", cleanDictation("Don't"))
        assertEquals("", cleanDictation("\",.\""))
    }

    @Test
    fun `dictationTargets 按空白分词并保留标点`() {
        assertEquals(
            listOf("Smells", "are", "everywhere,", "right?"),
            dictationTargets("  Smells are everywhere,   right? ")
        )
        assertEquals(emptyList<String>(), dictationTargets("   "))
    }

    @Test
    fun `chunkDictationInput 按清洗后长度切块且忽略输入空格`() {
        val targets = listOf("Smells", "are", "everywhere,")
        // smells=6 are=3 everywhere=9（逗号不计长度）
        val chunks = chunkDictationInput("smells are everywhere", targets)
        assertEquals(listOf("smells", "are", "everywhere"), chunks)
        // 连续无空格输入同样按长度切块
        val tight = chunkDictationInput("smellsareeverywhere", targets)
        assertEquals(listOf("smells", "are", "everywhere"), tight)
    }

    @Test
    fun `chunkDictationInput 标点词占位且输入不足时截断`() {
        val targets = listOf("yes", "!", "sir")
        // "!" 清洗后长度 0 → 自动占位空串，不消耗输入
        val chunks = chunkDictationInput("yes sir", targets)
        assertEquals(listOf("yes", "", "sir"), chunks)
        // 输入不足时在下一个实词处截断（标点占位先行，对齐 Web 顺序）
        assertEquals(listOf("yes", ""), chunkDictationInput("yes", targets))
    }

    @Test
    fun `isDictationCorrect 大小写与标点容错`() {
        val targets = listOf("Smells", "are", "everywhere,")
        assertTrue(isDictationCorrect(targets, listOf("smells", "ARE", "everywhere")))
        assertFalse(isDictationCorrect(targets, listOf("smells", "are")))
        assertFalse(isDictationCorrect(targets, listOf("smell", "are", "everywhere")))
    }

    @Test
    fun `isDictationCorrect 标点词恒过`() {
        val targets = listOf("wow", "!")
        assertTrue(isDictationCorrect(targets, listOf("wow", "")))
        assertTrue(isDictationCorrect(targets, listOf("WOW", "anything")))
    }
}
