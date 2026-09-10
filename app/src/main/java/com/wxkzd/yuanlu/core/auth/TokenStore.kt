package com.wxkzd.yuanlu.core.auth

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

/**
 * 移动端 JWT payload 携带的会话声明（签发口径见 Web core/auth/mobile-token.service.ts）。
 * 邮箱注册用户在首次编辑资料前没有 user_profile 行（GET api/user/profile 返回 404），
 * 此时这些声明与 Web 端 session.user 同源，是渲染用户卡（昵称兜底邮箱前缀/角色/邮箱）的数据来源。
 */
data class SessionClaims(
    val userid: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val nickname: String? = null
)

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) : TokenSource {
    private val TOKEN_KEY = stringPreferencesKey("jwt_token")

    override val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[TOKEN_KEY]
    }

    /** 从 JWT payload 解出的当前用户角色（USER | PREMIUM | ADMIN），未登录/解析失败为 null */
    val roleFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        decodeJwtClaim(preferences[TOKEN_KEY], "role")
    }

    /** 从 JWT payload 解出的当前用户 id（收藏等接口请求参数需要），未登录/解析失败为 null */
    val useridFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        decodeJwtClaim(preferences[TOKEN_KEY], "userid")
    }

    suspend fun saveToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[TOKEN_KEY] = token
        }
    }

    suspend fun clear() {
        context.dataStore.edit { preferences ->
            preferences.remove(TOKEN_KEY)
        }
    }

    suspend fun getToken(): String? {
        return context.dataStore.data.first()[TOKEN_KEY]
    }

    /** 读取指定声明（如 userid/role）；供不便订阅 Flow 的调用方同步获取 */
    suspend fun getUserid(): String? {
        return decodeJwtClaim(getToken(), "userid")
    }

    /** 解码当前 token 的全部会话声明；未登录/解析失败时各字段为 null */
    suspend fun getSessionClaims(): SessionClaims = SessionClaims(
        userid = decodeJwtClaim(getToken(), "userid"),
        email = decodeJwtClaim(getToken(), "email"),
        phone = decodeJwtClaim(getToken(), "phone"),
        role = decodeJwtClaim(getToken(), "role"),
        nickname = decodeJwtClaim(getToken(), "nickname")
    )

    /** 解码 JWT payload（base64url 无填充）中的指定声明 */
    private fun decodeJwtClaim(token: String?, claim: String): String? {
        if (token.isNullOrBlank()) return null
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8)
            // JsonNull 是 JsonPrimitive 的子类，必须显式排除：
            // 否则邮箱注册用户的 nickname/phone（JSON null）会解出字符串 "null"
            val element = Json.parseToJsonElement(json).jsonObject[claim]
            (element as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
        } catch (e: Exception) {
            null
        }
    }
}
