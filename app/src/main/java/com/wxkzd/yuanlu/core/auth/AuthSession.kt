package com.wxkzd.yuanlu.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * token 来源抽象：页面 ViewModel 只依赖此接口即可订阅全局登录态，
 * 生产实现为 TokenStore（DataStore），单元测试可用内存 Flow 伪造。
 */
interface TokenSource {
    val tokenFlow: Flow<String?>
}

/**
 * 账号会话事件：由 tokenFlow 派生，供各页面 ViewModel 响应全局登录态变化。
 *
 * 与 AppViewModel.isLoggedIn（布尔 UI 态）不同，这里保留 token 本身：
 * - distinctUntilChanged 过滤 DataStore 其它键写入引起的重复发射；
 * - 换号登录（tokenA → tokenB）会发射两个不同的 SessionStarted，页面据此整页重拉，
 *   杜绝上一账号的缓存数据串号。
 */
sealed interface AuthSessionEvent {
    /** 登出 / 游客态：应取消在途请求并清空本地缓存的用户数据 */
    data object SessionCleared : AuthSessionEvent

    /** 建立会话（冷启动已登录 / 首次登录 / 切换账号）：应自动触发整页数据拉取 */
    data class SessionStarted(val token: String) : AuthSessionEvent
}

/** tokenFlow → 会话事件流；null/空串统一归为 SessionCleared */
fun Flow<String?>.toAuthSessionEvents(): Flow<AuthSessionEvent> = map { token ->
    if (token.isNullOrBlank()) AuthSessionEvent.SessionCleared
    else AuthSessionEvent.SessionStarted(token)
}.distinctUntilChanged()
