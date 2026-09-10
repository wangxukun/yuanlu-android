package com.wxkzd.yuanlu.data.repository

import com.wxkzd.yuanlu.core.auth.SessionClaims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * user_profile 行缺失（GET api/user/profile 404）时的会话声明兜底映射：
 * 邮箱刚注册、尚未编辑资料的用户，用户卡应能以 JWT 声明渲染，
 * 昵称留空交由 UI 层回退邮箱前缀（对齐 Web mine 页 session 展示口径）。
 */
class AuthRepositoryFallbackTest {

    @Test
    fun `email signup claims map to minimal profile`() {
        val profile = SessionClaims(
            userid = "u1",
            email = "wangxiaoli220327@example.com",
            phone = null,
            role = "USER",
            nickname = null
        ).toFallbackProfile()

        assertEquals("u1", profile?.userid)
        assertEquals("wangxiaoli220327@example.com", profile?.email)
        assertEquals("USER", profile?.role)
        assertNull(profile?.nickname)
        assertNull(profile?.phone)
        assertNull(profile?.avatarUrl)
    }

    @Test
    fun `claims keep nickname and phone when present`() {
        val profile = SessionClaims(
            userid = "u2",
            email = "a@b.com",
            phone = "13812348000",
            role = "PREMIUM",
            nickname = "远路客"
        ).toFallbackProfile()

        assertEquals("远路客", profile?.nickname)
        assertEquals("13812348000", profile?.phone)
        assertEquals("PREMIUM", profile?.role)
    }

    @Test
    fun `claims without userid and email yield no fallback`() {
        assertNull(SessionClaims().toFallbackProfile())
        assertNull(SessionClaims(nickname = "远路客").toFallbackProfile())
    }

    @Test
    fun `email only claims still produce fallback`() {
        val profile = SessionClaims(email = "walker@example.com").toFallbackProfile()
        assertEquals("walker@example.com", profile?.email)
        assertEquals("", profile?.userid)
    }
}
