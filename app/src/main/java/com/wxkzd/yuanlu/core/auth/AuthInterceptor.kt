package com.wxkzd.yuanlu.core.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenStore.getToken() }

        val requestBuilder = chain.request().newBuilder()
            .header("X-Client", "android")

        if (!token.isNullOrEmpty()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        // 401 → 清除登录态。但必须排除 /api/auth/**：登录接口自身的 401
        // 表示"邮箱或密码错误"，若不排除会把当前已登录账号的有效 Token 误清掉。
        val isAuthEndpoint = response.request.url.encodedPath.startsWith("/api/auth/")
        if (response.code == 401 && !isAuthEndpoint) {
            runBlocking {
                tokenStore.clear()
            }
        }

        return response
    }
}
