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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "auth_prefs")

@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TOKEN_KEY = stringPreferencesKey("jwt_token")

    val tokenFlow: Flow<String?> = context.dataStore.data.map { preferences ->
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

    /** 解码 JWT payload（base64url 无填充）中的指定声明 */
    private fun decodeJwtClaim(token: String?, claim: String): String? {
        if (token.isNullOrBlank()) return null
        return try {
            val payload = token.split(".").getOrNull(1) ?: return null
            val json = String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8)
            Json.parseToJsonElement(json).jsonObject[claim]?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
