package com.wxkzd.yuanlu.feature.profile

import com.wxkzd.yuanlu.domain.model.AchievementItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 个人中心纯逻辑单测：口径逐条对齐 Web 端实现 */
class ProfileUtilsTest {

    // ---------- 里程换算 ----------

    @Test
    fun `total km converts hours at five km per hour`() {
        assertEquals(61.5, ProfileUtils.totalKm(12.3), 0.0001)
        assertEquals(0.0, ProfileUtils.totalKm(0.0), 0.0001)
    }

    @Test
    fun `format km keeps one decimal`() {
        assertEquals("61.5", ProfileUtils.formatKm(61.54))
        assertEquals("0.0", ProfileUtils.formatKm(0.0))
        assertEquals("100.0", ProfileUtils.formatKm(100.0))
    }

    @Test
    fun `format hours drops trailing zero like js`() {
        assertEquals("12", ProfileUtils.formatHours(12.0))
        assertEquals("12.3", ProfileUtils.formatHours(12.3))
        assertEquals("0", ProfileUtils.formatHours(0.0))
    }

    @Test
    fun `milestones match web ladder`() {
        assertEquals(listOf(1.0, 5.0, 21.1, 42.2, 100.0), ProfileUtils.MILESTONES.map { it.first })
        assertEquals("半马", ProfileUtils.MILESTONES[2].second)
    }

    // ---------- 等级与展示回退 ----------

    @Test
    fun `level label maps english keys to chinese`() {
        assertEquals("初级", ProfileUtils.levelLabel("Beginner"))
        assertEquals("中级", ProfileUtils.levelLabel("Intermediate"))
        assertEquals("高级", ProfileUtils.levelLabel("Advanced"))
        assertEquals("未分级", ProfileUtils.levelLabel("General"))
    }

    @Test
    fun `level label falls back to raw or default`() {
        assertEquals("未知等级", ProfileUtils.levelLabel("未知等级"))
        // Web fetchProfile 的 `data.learnLevel || "中级"` 口径
        assertEquals("中级", ProfileUtils.levelLabel(null))
        assertEquals("中级", ProfileUtils.levelLabel(""))
    }

    @Test
    fun `display name falls back to email prefix then user`() {
        assertEquals("远路客", ProfileUtils.displayName("远路客", "a@b.com"))
        assertEquals("walker", ProfileUtils.displayName(null, "walker@yuanlu.com"))
        assertEquals("walker", ProfileUtils.displayName("  ", "walker@yuanlu.com"))
        assertEquals("User", ProfileUtils.displayName(null, null))
    }

    @Test
    fun `display bio falls back to motto`() {
        assertEquals("自我介绍", ProfileUtils.displayBio("自我介绍"))
        assertEquals(ProfileUtils.DEFAULT_BIO, ProfileUtils.displayBio(null))
        assertEquals(ProfileUtils.DEFAULT_BIO, ProfileUtils.displayBio("   "))
    }

    // ---------- 账号安全脱敏 ----------

    @Test
    fun `mask phone hides middle four digits`() {
        assertEquals("138****8000", ProfileUtils.maskPhone("13812348000"))
        // 位数不符原样返回（Web replace 未命中行为）
        assertEquals("12345", ProfileUtils.maskPhone("12345"))
        assertEquals("", ProfileUtils.maskPhone(null))
    }

    @Test
    fun `placeholder email detection`() {
        assertTrue(ProfileUtils.isPlaceholderEmail("a@placeholder.yuanlu.com"))
        assertFalse(ProfileUtils.isPlaceholderEmail("a@b.com"))
        assertFalse(ProfileUtils.isPlaceholderEmail(null))
    }

    @Test
    fun `mask email keeps first char and domain with up to four stars`() {
        // 中间段（不含首字符与域名）整体替换为至多 4 个星（Web 正则同口径）
        assertEquals("a****@example.com", ProfileUtils.maskEmail("aliceb@example.com"))
        assertEquals("a**@example.com", ProfileUtils.maskEmail("abc@example.com"))
        assertEquals("a@example.com", ProfileUtils.maskEmail("a@example.com"))
        // 占位邮箱与空值不展示
        assertEquals("", ProfileUtils.maskEmail("a@placeholder.yuanlu.com"))
        assertEquals("", ProfileUtils.maskEmail(null))
    }

    // ---------- 表单校验 ----------

    @Test
    fun `nickname validation`() {
        assertNull(ProfileUtils.validateNickname("远路客"))
        assertNull(ProfileUtils.validateNickname("a".repeat(20)))
        assertEquals("请输入昵称", ProfileUtils.validateNickname(""))
        assertEquals("请输入昵称", ProfileUtils.validateNickname("   "))
        assertEquals("昵称不能超过 20 个字", ProfileUtils.validateNickname("a".repeat(21)))
    }

    @Test
    fun `bio validation`() {
        assertNull(ProfileUtils.validateBio(""))
        assertNull(ProfileUtils.validateBio("长".repeat(100)))
        assertEquals("简介不能超过 100 个字", ProfileUtils.validateBio("长".repeat(101)))
    }

    // ---------- 成就排序 ----------

    @Test
    fun `sort achievements puts unlocked first and keeps order stable`() {
        val locked1 = AchievementItem("a", "A", "", "🎯")
        val locked2 = AchievementItem("b", "B", "", "🎯")
        val unlocked1 = AchievementItem("c", "C", "", "🏆", unlocked = true)
        val unlocked2 = AchievementItem("d", "D", "", "🏆", unlocked = true)
        val sorted = ProfileUtils.sortAchievements(listOf(locked1, unlocked1, locked2, unlocked2))
        assertEquals(listOf(unlocked1, unlocked2, locked1, locked2), sorted)
    }

    // ---------- 图表刻度 ----------

    @Test
    fun `chart y max picks smallest nice multiple of four like recharts`() {
        assertEquals(4, ProfileUtils.chartYMax(listOf()))
        assertEquals(4, ProfileUtils.chartYMax(listOf(0, 0)))
        // Web 示例：峰值约 13 分钟 → 上限 16m（刻度 0/4/8/12/16）
        assertEquals(16, ProfileUtils.chartYMax(listOf(13)))
        assertEquals(16, ProfileUtils.chartYMax(listOf(4, 13, 2)))
        assertEquals(20, ProfileUtils.chartYMax(listOf(18)))
        assertEquals(40, ProfileUtils.chartYMax(listOf(25)))
        assertEquals(80, ProfileUtils.chartYMax(listOf(61)))
        assertEquals(120, ProfileUtils.chartYMax(listOf(100, 30)))
        assertEquals(160, ProfileUtils.chartYMax(listOf(121, 10)))
    }

    // ---------- 日期 ----------

    @Test
    fun `format join date parses iso to yyyy slash mm slash dd`() {
        assertEquals("2026/01/15", ProfileUtils.formatDate("2026-01-15T08:00:00.000Z"))
        assertEquals("未知日期", ProfileUtils.formatDate(null))
        assertEquals("未知日期", ProfileUtils.formatDate("not-a-date"))
    }
}
