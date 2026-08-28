package com.wxkzd.yuanlu.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val profile: UserProfile? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** 登录态变化时由 UI 层调用（游客 → 清空；登录 → 拉取资料） */
    fun onAuthStateChanged(loggedIn: Boolean) {
        if (!loggedIn) {
            _uiState.value = ProfileUiState()
            return
        }
        if (_uiState.value.profile != null || _uiState.value.isLoading) return
        load()
    }

    fun load() {
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.getProfile()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, profile = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, error = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, error = "网络连接失败")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.value = ProfileUiState()
        }
    }
}
