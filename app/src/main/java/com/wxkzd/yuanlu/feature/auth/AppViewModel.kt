package com.wxkzd.yuanlu.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.datastore.SettingsStore
import com.wxkzd.yuanlu.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 应用级状态：登录态 + 全局登录弹层 + 主题偏好。
 * 登录弹层对齐 Web 的全局 ModalProvider：任意入口 showLogin()，登录成功自动收起。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    tokenStore: TokenStore,
    private val settingsStore: SettingsStore
) : ViewModel() {

    /** null = DataStore 尚未发出首个值（冷启动），避免误闪游客视图 */
    val isLoggedIn: StateFlow<Boolean?> = tokenStore.tokenFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _showLoginSheet = MutableStateFlow(false)
    val showLoginSheet: StateFlow<Boolean> = _showLoginSheet.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = settingsStore.themeModeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private var lastLoggedIn: Boolean? = null

    init {
        // 登录成功（token 出现）自动收起弹层
        viewModelScope.launch {
            isLoggedIn.collect { loggedIn ->
                if (loggedIn == true && lastLoggedIn != true) {
                    _showLoginSheet.value = false
                }
                lastLoggedIn = loggedIn
            }
        }
    }

    fun showLogin() {
        _showLoginSheet.value = true
    }

    fun hideLogin() {
        _showLoginSheet.value = false
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsStore.setThemeMode(mode)
        }
    }
}
