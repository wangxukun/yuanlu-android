package com.wxkzd.yuanlu.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false,
    // ---- 验证码发送 ----
    val isSending: Boolean = false,
    /** >0 时「获取验证码」按钮显示倒计时并禁用 */
    val countdownSeconds: Int = 0,
    // ---- 邮箱 Tab 表单模式 ----
    /** false=登录（默认）；true=注册（对齐 Web 注册对话框：邮箱未注册不能自动建号） */
    val isRegisterMode: Boolean = false,
    /** 非侵扰提示（如「注册成功，正在自动登录」），绿色/primary 展示，区别于 error */
    val notice: String? = null
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun loginWithPassword(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.update { it.copy(error = "请输入邮箱和密码") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            handleResult(repository.loginWithPassword(email, pass))
        }
    }

    fun loginWithSms(phone: String, code: String) {
        if (phone.isBlank() || code.isBlank()) {
            _uiState.update { it.copy(error = "请输入手机号和验证码") }
            return
        }
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            handleResult(repository.loginWithSms(phone, code))
        }
    }

    /** 发送登录验证码：风控（requireCaptcha）时后端已转译为「触发安全验证，请改用邮箱登录」 */
    fun sendSmsCode(phone: String) {
        if (phone.isBlank() || phone.length != 11) {
            _uiState.update { it.copy(error = "请输入11位手机号码") }
            return
        }
        val state = _uiState.value
        if (state.isSending || state.countdownSeconds > 0) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.sendSmsCode(phone)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false) }
                    startCountdown()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSending = false, error = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isSending = false, error = "网络连接失败")
                }
            }
        }
    }

    // ---------- 邮箱注册 / 登录切换 ----------

    /** 邮箱 Tab → 注册表单（Web 手机验证码走自动注册，邮箱必须显式注册） */
    fun switchToRegister() = _uiState.update {
        it.copy(isRegisterMode = true, error = null, notice = null)
    }

    fun switchToLogin() = _uiState.update {
        it.copy(isRegisterMode = false, error = null, notice = null)
    }

    /** 发送邮箱注册验证码（复用短信验证码的倒计时与防重入） */
    fun sendEmailCode(email: String) {
        if (!EMAIL_REGEX.matches(email)) {
            _uiState.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        val state = _uiState.value
        if (state.isSending || state.countdownSeconds > 0) return
        _uiState.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            when (val result = repository.sendEmailVerificationCode(email)) {
                is Result.Success -> {
                    _uiState.update { it.copy(isSending = false) }
                    startCountdown()
                }
                is Result.Error -> _uiState.update {
                    it.copy(isSending = false, error = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isSending = false, error = "网络连接失败")
                }
            }
        }
    }

    /**
     * 邮箱注册：verify-code + sign-up 成功后，用同一凭据自动登录
     * （mobile/token 邮箱分支不会自动建号，注册与登录必须分离）。
     */
    fun register(email: String, code: String, password: String, confirmPassword: String) {
        when {
            email.isBlank() || code.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                _uiState.update { it.copy(error = "请填写完整注册信息") }
            code.length != 6 ->
                _uiState.update { it.copy(error = "请输入6位邮箱验证码") }
            password.length < 6 ->
                _uiState.update { it.copy(error = "密码至少需要6位") }
            password != confirmPassword ->
                _uiState.update { it.copy(error = "两次输入的密码不一致") }
            else -> {
                _uiState.update { it.copy(isLoading = true, error = null, notice = null) }
                viewModelScope.launch {
                    when (val result = repository.signUp(email, code, password)) {
                        is Result.Success -> {
                            _uiState.update { it.copy(notice = "注册成功，正在自动登录") }
                            handleResult(repository.loginWithPassword(email, password))
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
        }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (remaining in 60 downTo 1) {
                _uiState.update { it.copy(countdownSeconds = remaining) }
                delay(1000)
            }
            _uiState.update { it.copy(countdownSeconds = 0) }
        }
    }

    private fun handleResult(result: Result<Unit>) {
        when (result) {
            is Result.Success -> {
                // token 写入后 AppViewModel.isLoggedIn 翻真，登录弹层自动收起
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
            is Result.Error -> {
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            Result.NetworkError -> {
                _uiState.update { it.copy(isLoading = false, error = "网络连接失败") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun reset() {
        countdownJob?.cancel()
        _uiState.value = LoginUiState()
    }

    override fun onCleared() {
        countdownJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** 宽松邮箱格式：局部@域名（对齐 Web 注册框的前端校验强度） */
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
