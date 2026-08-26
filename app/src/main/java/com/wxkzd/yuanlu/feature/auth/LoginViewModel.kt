package com.wxkzd.yuanlu.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun loginWithPassword(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(error = "Email and password cannot be empty") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.loginWithPassword(email, pass)
            handleResult(result)
        }
    }

    fun loginWithSms(phone: String, code: String) {
        if (phone.isBlank() || code.isBlank()) {
            _uiState.update { it.copy(error = "Phone and code cannot be empty") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = repository.loginWithSms(phone, code)
            handleResult(result)
        }
    }

    private fun handleResult(result: Result<Unit>) {
        when (result) {
            is Result.Success -> {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
            is Result.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            Result.NetworkError -> {
                _uiState.update { it.copy(isLoading = false, error = "Network connection failed") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
