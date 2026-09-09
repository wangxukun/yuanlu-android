package com.wxkzd.yuanlu

import com.wxkzd.yuanlu.core.auth.TokenSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 测试用登录态替身：初始 token 模拟冷启动会话，
 * login/logout 即时发射 token 变化（与 TokenStore 的 DataStore 行为一致）。
 */
class FakeTokenSource(initial: String? = null) : TokenSource {
    private val tokens = MutableStateFlow(initial)
    override val tokenFlow: Flow<String?> = tokens
    fun login(token: String = "jwt-test") {
        tokens.value = token
    }
    fun logout() {
        tokens.value = null
    }
}
