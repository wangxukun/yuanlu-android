package com.wxkzd.yuanlu.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wxkzd.yuanlu.domain.model.PracticeSettings
import com.wxkzd.yuanlu.domain.model.PracticeStrictness
import com.wxkzd.yuanlu.domain.model.PracticeTextMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.practiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "practice_settings")

/**
 * 语音评测设置持久化（key = practice_settings），
 * 字段与默认值对齐 Web localStorage "practice-settings"（zustand persist）。
 */
@Singleton
class PracticeSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val FONT_SIZE_LEVEL = intPreferencesKey("font_size_level")
        val SHOW_TRANSLATION = booleanPreferencesKey("show_translation")
        val SHOW_IPA = booleanPreferencesKey("show_ipa")
        val TEXT_MODE = intPreferencesKey("text_mode")
        val PASS_THRESHOLD = intPreferencesKey("pass_threshold")
        val STRICTNESS = intPreferencesKey("strictness")
        val WEAK_THRESHOLD = intPreferencesKey("weak_threshold")
        val MIN_WORDS = intPreferencesKey("min_words")
        val MAX_WORDS = intPreferencesKey("max_words")
        val ONLY_UNMASTERED = booleanPreferencesKey("only_unmastered")
        val AUTO_ADVANCE = booleanPreferencesKey("auto_advance")
    }

    val settingsFlow: Flow<PracticeSettings> = context.practiceDataStore.data.map { p ->
        PracticeSettings(
            fontSizeLevel = (p[Keys.FONT_SIZE_LEVEL] ?: 1).coerceIn(0, 2),
            showTranslation = p[Keys.SHOW_TRANSLATION] ?: true,
            showIpa = p[Keys.SHOW_IPA] ?: true,
            textMode = when (p[Keys.TEXT_MODE] ?: 0) {
                1 -> PracticeTextMode.IPA
                2 -> PracticeTextMode.BLIND
                else -> PracticeTextMode.NORMAL
            },
            passThreshold = (p[Keys.PASS_THRESHOLD] ?: 80).coerceIn(60, 95),
            strictness = when (p[Keys.STRICTNESS] ?: 1) {
                0 -> PracticeStrictness.LENIENT
                2 -> PracticeStrictness.STRICT
                else -> PracticeStrictness.STANDARD
            },
            weakThreshold = (p[Keys.WEAK_THRESHOLD] ?: 80).coerceIn(60, 95),
            minWords = (p[Keys.MIN_WORDS] ?: 0).coerceIn(0, 50),
            maxWords = (p[Keys.MAX_WORDS] ?: 50).coerceIn(0, 50),
            onlyUnmastered = p[Keys.ONLY_UNMASTERED] ?: false,
            autoAdvance = p[Keys.AUTO_ADVANCE] ?: true
        )
    }

    /** 原子更新：读取-变换-写回（transform 内做 clamp，避免并发交错覆盖） */
    suspend fun update(transform: (PracticeSettings) -> PracticeSettings) {
        context.practiceDataStore.edit { p ->
            val current = PracticeSettings(
                fontSizeLevel = (p[Keys.FONT_SIZE_LEVEL] ?: 1).coerceIn(0, 2),
                showTranslation = p[Keys.SHOW_TRANSLATION] ?: true,
                showIpa = p[Keys.SHOW_IPA] ?: true,
                textMode = when (p[Keys.TEXT_MODE] ?: 0) {
                    1 -> PracticeTextMode.IPA
                    2 -> PracticeTextMode.BLIND
                    else -> PracticeTextMode.NORMAL
                },
                passThreshold = (p[Keys.PASS_THRESHOLD] ?: 80).coerceIn(60, 95),
                strictness = when (p[Keys.STRICTNESS] ?: 1) {
                    0 -> PracticeStrictness.LENIENT
                    2 -> PracticeStrictness.STRICT
                    else -> PracticeStrictness.STANDARD
                },
                weakThreshold = (p[Keys.WEAK_THRESHOLD] ?: 80).coerceIn(60, 95),
                minWords = (p[Keys.MIN_WORDS] ?: 0).coerceIn(0, 50),
                maxWords = (p[Keys.MAX_WORDS] ?: 50).coerceIn(0, 50),
                onlyUnmastered = p[Keys.ONLY_UNMASTERED] ?: false,
                autoAdvance = p[Keys.AUTO_ADVANCE] ?: true
            )
            val next = transform(current)
            p[Keys.FONT_SIZE_LEVEL] = next.fontSizeLevel.coerceIn(0, 2)
            p[Keys.SHOW_TRANSLATION] = next.showTranslation
            p[Keys.SHOW_IPA] = next.showIpa
            p[Keys.TEXT_MODE] = when (next.textMode) {
                PracticeTextMode.NORMAL -> 0
                PracticeTextMode.IPA -> 1
                PracticeTextMode.BLIND -> 2
            }
            p[Keys.PASS_THRESHOLD] = next.passThreshold.coerceIn(60, 95)
            p[Keys.STRICTNESS] = when (next.strictness) {
                PracticeStrictness.LENIENT -> 0
                PracticeStrictness.STANDARD -> 1
                PracticeStrictness.STRICT -> 2
            }
            p[Keys.WEAK_THRESHOLD] = next.weakThreshold.coerceIn(60, 95)
            p[Keys.MIN_WORDS] = next.minWords.coerceIn(0, 50)
            p[Keys.MAX_WORDS] = next.maxWords.coerceIn(0, 50)
            p[Keys.ONLY_UNMASTERED] = next.onlyUnmastered
            p[Keys.AUTO_ADVANCE] = next.autoAdvance
        }
    }
}
