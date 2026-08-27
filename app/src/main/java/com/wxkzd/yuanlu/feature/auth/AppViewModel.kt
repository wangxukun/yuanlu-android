package com.wxkzd.yuanlu.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    tokenStore: TokenStore
) : ViewModel() {

    /** null = DataStore 尚未发出首个值（冷启动），避免误闪登录页 */
    val isLoggedIn: StateFlow<Boolean?> = tokenStore.tokenFlow
        .map { !it.isNullOrBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
