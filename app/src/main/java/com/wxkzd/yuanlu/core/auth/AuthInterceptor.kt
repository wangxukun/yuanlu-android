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

        if (response.code == 401) {
            runBlocking {
                tokenStore.clear()
            }
        }

        return response
    }
}
