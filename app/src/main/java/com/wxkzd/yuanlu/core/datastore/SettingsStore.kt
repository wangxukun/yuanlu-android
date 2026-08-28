package com.wxkzd.yuanlu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxkzd.yuanlu.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_prefs")

/** 应用偏好（主题等），与登录态的 auth_prefs 分离 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val THEME_KEY = intPreferencesKey("theme_mode")

    val themeModeFlow: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
        when (preferences[THEME_KEY]) {
            1 -> ThemeMode.LIGHT
            2 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { preferences ->
            preferences[THEME_KEY] = when (mode) {
                ThemeMode.SYSTEM -> 0
                ThemeMode.LIGHT -> 1
                ThemeMode.DARK -> 2
            }
        }
    }
}
