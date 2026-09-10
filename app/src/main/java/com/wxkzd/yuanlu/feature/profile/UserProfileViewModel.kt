package com.wxkzd.yuanlu.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 编辑资料弹窗的两个表单区块（对齐 Web EditProfileModal 的 tabs） */
enum class EditProfileTab { PROFILE, GOALS }

/** 账号与安全弹层类型（绑定手机/绑定邮箱，同一时刻仅一个） */
enum class SecuritySheet { BIND_PHONE, BIND_EMAIL }

/**
 * 绑定表单状态（手机/邮箱弹层共用骨架，字段按弹层取用）。
 * 对齐 Web BindPhoneForm/BindEmailForm 的本地 state。
 */
data class SecurityFormState(
    val phone: String = "",
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSendingCode: Boolean = false,
    /** >0 时「获取验证码」按钮显示倒计时并禁用 */
    val countdownSeconds: Int = 0,
    val isSubmitting: Boolean = false,
    /** 弹层内侵扰提示（校验失败/接口报错，红色） */
    val error: String? = null,
    /** 弹层内非侵扰提示（验证码已发送，primary 色） */
    val notice: String? = null
)

/**
 * 个人中心状态：统管用户信息、旅程数据、里程碑与编辑资料表单。
 * Activity 作用域（Navigation 根创建），供个人中心页与「我的」Tab 共享——
 * 保存成功后 profileRevision 自增，外层 Tab 监听后刷新自己的用户卡。
 */
data class UserProfileUiState(
    // ---- 头部资料 ----
    val isLoading: Boolean = true,
    /** 下拉刷新中（静默重载，保留现有内容，仅顶部转圈） */
    val isRefreshing: Boolean = false,
    val profile: UserProfile? = null,
    val loadError: String? = null,
    /** 保存成功自增；「我的」Tab 观察它触发资料重载（对齐 Web updateSession 后 fetchProfile） */
    val profileRevision: Int = 0,
    // ---- 旅程数据 ----
    val stats: ProfileStats? = null,
    val statsLoading: Boolean = true,
    val weeklyActivity: List<WeeklyActivityItem> = emptyList(),
    val activityLoading: Boolean = true,
    val weekOffset: Int = 0,
    // ---- 里程碑 ----
    val achievements: List<AchievementItem> = emptyList(),
    val achievementsLoading: Boolean = true,
    // ---- 编辑资料弹窗 ----
    val isEditOpen: Boolean = false,
    val editTab: EditProfileTab = EditProfileTab.PROFILE,
    val formNickname: String = "",
    val formBio: String = "",
    val formLearnLevel: String = "Beginner",
    val formDailyGoalMins: Int = 20,
    val formWeeklyHours: Int = 2,
    val formWeeklyWords: Int = 50,
    /** 已裁剪待上传的头像 JPEG 字节（null = 沿用现有头像） */
    val formAvatar: ByteArray? = null,
    /** ByteArray 相等性为引用比较，用版本号驱动预览重组 */
    val formAvatarVersion: Int = 0,
    val nicknameError: String? = null,
    val bioError: String? = null,
    val isSaving: Boolean = false,
    // ---- 账号与安全（绑定手机/邮箱弹层 + 注销确认） ----
    val securitySheet: SecuritySheet? = null,
    val securityForm: SecurityFormState = SecurityFormState(),
    val isDeleteConfirmOpen: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /** 注销成功一次性标记：Route 观察后提示并退出（token 已清，全局降级游客态） */
    val isAccountDeleted: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    private var securityCountdownJob: Job? = null

    fun consumeToast() {
        _toast.value = null
    }

    /** 进入页面时全量加载（对齐 Web personal-center 各组件挂载即拉取） */
    fun load() {
        loadProfile()
        loadStats()
        loadActivity(_uiState.value.weekOffset)
        loadAchievements()
    }

    /**
     * 下拉刷新：静默重载四路数据（保留现有内容，仅顶部转圈），
     * 全部完成后收起指示器。
     */
    fun refresh() {
        if (_uiState.value.isRefreshing) return
        _uiState.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            joinAll(
                loadProfile(silent = true),
                loadStats(silent = true),
                loadActivity(_uiState.value.weekOffset, silent = true),
                loadAchievements(silent = true)
            )
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun retry() = load()

    private fun loadProfile(silent: Boolean = false): Job {
        if (!silent) _uiState.update { it.copy(isLoading = true, loadError = null) }
        return viewModelScope.launch {
            when (val result = authRepository.getProfile()) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, profile = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(isLoading = false, loadError = result.message)
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(isLoading = false, loadError = "网络连接失败")
                }
            }
        }
    }

    private fun loadStats(silent: Boolean = false): Job {
        if (!silent) _uiState.update { it.copy(statsLoading = true) }
        return viewModelScope.launch {
            when (val result = authRepository.getStatsOverview()) {
                is Result.Success -> _uiState.update {
                    it.copy(statsLoading = false, stats = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(statsLoading = false, stats = it.stats ?: ProfileStats())
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(statsLoading = false, stats = it.stats ?: ProfileStats())
                }
            }
        }
    }

    /** weekOffset 0=本周 1=上周（对齐 Web ActivityChart 的下拉切换） */
    fun changeWeek(offset: Int) {
        if (_uiState.value.weekOffset == offset) return
        _uiState.update { it.copy(weekOffset = offset) }
        loadActivity(offset)
    }

    private fun loadActivity(offset: Int, silent: Boolean = false): Job {
        if (!silent) _uiState.update { it.copy(activityLoading = true) }
        return viewModelScope.launch {
            when (val result = authRepository.getWeeklyActivity(offset)) {
                is Result.Success -> _uiState.update {
                    it.copy(activityLoading = false, weeklyActivity = result.data)
                }
                is Result.Error -> _uiState.update {
                    it.copy(activityLoading = false, weeklyActivity = emptyList())
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(activityLoading = false, weeklyActivity = emptyList())
                }
            }
        }
    }

    private fun loadAchievements(silent: Boolean = false): Job {
        if (!silent) _uiState.update { it.copy(achievementsLoading = true) }
        return viewModelScope.launch {
            when (val result = authRepository.getAchievements()) {
                is Result.Success -> _uiState.update {
                    it.copy(
                        achievementsLoading = false,
                        achievements = ProfileUtils.sortAchievements(result.data)
                    )
                }
                is Result.Error -> _uiState.update {
                    it.copy(achievementsLoading = false, achievements = emptyList())
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(achievementsLoading = false, achievements = emptyList())
                }
            }
        }
    }

    // ---------- 编辑资料弹窗 ----------

    /** 打开弹窗并以当前资料回填表单（对齐 Web isOpen 时 useEffect 重置各字段） */
    fun openEdit() {
        val profile = _uiState.value.profile
        _uiState.update {
            it.copy(
                isEditOpen = true,
                editTab = EditProfileTab.PROFILE,
                formNickname = profile?.nickname.orEmpty(),
                formBio = profile?.bio ?: ProfileUtils.DEFAULT_BIO,
                formLearnLevel = profile?.learnLevel ?: "Beginner",
                formDailyGoalMins = profile?.dailyStudyGoalMins ?: 20,
                formWeeklyHours = profile?.weeklyListeningGoalHours ?: 2,
                formWeeklyWords = profile?.weeklyWordsGoal ?: 50,
                formAvatar = null,
                nicknameError = null,
                bioError = null
            )
        }
    }

    fun closeEdit() {
        _uiState.update { it.copy(isEditOpen = false, isSaving = false) }
    }

    fun switchEditTab(tab: EditProfileTab) {
        _uiState.update { it.copy(editTab = tab) }
    }

    fun updateNickname(value: String) {
        _uiState.update {
            it.copy(
                formNickname = value,
                // 修正后即时清除错误；提交时再全量校验
                nicknameError = if (it.nicknameError != null) ProfileUtils.validateNickname(value) else null
            )
        }
    }

    fun updateBio(value: String) {
        _uiState.update {
            it.copy(
                formBio = value,
                bioError = if (it.bioError != null) ProfileUtils.validateBio(value) else null
            )
        }
    }

    fun updateLearnLevel(value: String) {
        _uiState.update { it.copy(formLearnLevel = value) }
    }

    fun updateDailyGoalMins(value: Int) {
        _uiState.update { it.copy(formDailyGoalMins = value.coerceIn(10, 120)) }
    }

    fun updateWeeklyHours(value: Int) {
        _uiState.update { it.copy(formWeeklyHours = value.coerceIn(1, 20)) }
    }

    fun updateWeeklyWords(value: Int) {
        _uiState.update { it.copy(formWeeklyWords = value.coerceIn(10, 200)) }
    }

    /** 头像选择完成（UI 层已做居中方形裁剪），bytes 为 JPEG 字节 */
    fun onAvatarPicked(bytes: ByteArray?) {
        if (bytes == null) return
        _uiState.update {
            it.copy(formAvatar = bytes, formAvatarVersion = it.formAvatarVersion + 1)
        }
    }

    /** 保存所有更改：先校验，通过后提交并回写资料（对齐 Web handleSubmit → onSave） */
    fun saveProfile() {
        val state = _uiState.value
        if (state.isSaving) return
        val nicknameError = ProfileUtils.validateNickname(state.formNickname)
        val bioError = ProfileUtils.validateBio(state.formBio)
        if (nicknameError != null || bioError != null) {
            _uiState.update { it.copy(nicknameError = nicknameError, bioError = bioError) }
            if (nicknameError != null) switchEditTab(EditProfileTab.PROFILE)
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val previous = _uiState.value.profile
            when (
                val result = authRepository.updateProfile(
                    nickname = state.formNickname.trim(),
                    bio = state.formBio.trim(),
                    learnLevel = state.formLearnLevel,
                    dailyStudyGoalMins = state.formDailyGoalMins,
                    weeklyListeningGoalHours = state.formWeeklyHours,
                    weeklyWordsGoal = state.formWeeklyWords,
                    avatarJpeg = state.formAvatar
                )
            ) {
                is Result.Success -> {
                    // PUT 响应不含 User 嵌套（邮箱/角色等）——沿用旧资料补齐（对齐 Web updateSession 只覆盖展示字段）
                    val merged = result.data.copy(
                        email = result.data.email ?: previous?.email,
                        phone = result.data.phone ?: previous?.phone,
                        role = result.data.role ?: previous?.role,
                        createAt = result.data.createAt ?: previous?.createAt
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isEditOpen = false,
                            profile = merged,
                            formAvatar = null,
                            profileRevision = it.profileRevision + 1
                        )
                    }
                    _toast.value = "设置已更新"
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _toast.value = "网络连接失败，请重试"
                }
            }
        }
    }

    // ---------- 账号与安全：绑定手机/邮箱 + 注销 ----------

    fun openBindPhoneSheet() {
        securityCountdownJob?.cancel()
        _uiState.update {
            it.copy(securitySheet = SecuritySheet.BIND_PHONE, securityForm = SecurityFormState())
        }
    }

    fun openBindEmailSheet() {
        securityCountdownJob?.cancel()
        _uiState.update {
            it.copy(securitySheet = SecuritySheet.BIND_EMAIL, securityForm = SecurityFormState())
        }
    }

    fun closeSecuritySheet() {
        securityCountdownJob?.cancel()
        _uiState.update { it.copy(securitySheet = null, securityForm = SecurityFormState()) }
    }

    // 输入回调：修正任意字段即清除错误（对齐 Web 表单 onChange 时 setError("")）

    fun updateSecurityPhone(value: String) {
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(
                phone = value.filter(Char::isDigit).take(11), error = null
            ))
        }
    }

    fun updateSecurityEmail(value: String) {
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(email = value.trim(), error = null))
        }
    }

    fun updateSecurityCode(value: String) {
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(
                code = value.filter(Char::isDigit).take(6), error = null
            ))
        }
    }

    fun updateSecurityPassword(value: String) {
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(password = value, error = null))
        }
    }

    fun updateSecurityConfirmPassword(value: String) {
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(confirmPassword = value, error = null))
        }
    }

    /** 发送绑定手机验证码（scene=BIND；风控/限频文案由仓库层转译） */
    fun sendSecurityPhoneCode() {
        val form = _uiState.value.securityForm
        ProfileUtils.validateBindPhone(form.phone)?.let { error ->
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = error)) }
            return
        }
        if (form.isSendingCode || form.countdownSeconds > 0) return
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(isSendingCode = true, error = null, notice = null))
        }
        viewModelScope.launch {
            when (val result = authRepository.sendBindPhoneCode(form.phone)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(securityForm = it.securityForm.copy(
                            isSendingCode = false, notice = "验证码发送成功"
                        ))
                    }
                    startSecurityCountdown()
                }
                is Result.Error -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSendingCode = false, error = result.message))
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSendingCode = false, error = "网络连接失败，请重试"))
                }
            }
        }
    }

    /** 发送绑定邮箱验证码（5 分钟有效） */
    fun sendSecurityEmailCode() {
        val form = _uiState.value.securityForm
        ProfileUtils.validateBindEmail(form.email)?.let { error ->
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = error)) }
            return
        }
        if (form.isSendingCode || form.countdownSeconds > 0) return
        _uiState.update {
            it.copy(securityForm = it.securityForm.copy(isSendingCode = true, error = null, notice = null))
        }
        viewModelScope.launch {
            when (val result = authRepository.sendBindEmailCode(form.email)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(securityForm = it.securityForm.copy(
                            isSendingCode = false, notice = "验证码已发送，请检查您的邮箱"
                        ))
                    }
                    startSecurityCountdown()
                }
                is Result.Error -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSendingCode = false, error = result.message))
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSendingCode = false, error = "网络连接失败，请重试"))
                }
            }
        }
    }

    /** 提交绑定手机号：成功后乐观回写本地资料（对齐 Web updateSession） */
    fun submitBindPhone() {
        val form = _uiState.value.securityForm
        if (form.isSubmitting) return
        ProfileUtils.validateBindPhone(form.phone)?.let { error ->
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = error)) }
            return
        }
        if (form.code.length != 6) {
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = "请输入6位验证码")) }
            return
        }
        _uiState.update { it.copy(securityForm = it.securityForm.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            when (val result = authRepository.bindPhone(form.phone, form.code)) {
                is Result.Success -> {
                    // 不重拉资料：新邮箱注册用户无 user_profile 行，GET 会走 404→JWT 兜底，
                    // 而 JWT 签发于绑定前、不含新手机号，重拉反而丢失；直接本地回写。
                    securityCountdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            securitySheet = null,
                            securityForm = SecurityFormState(),
                            profile = it.profile?.copy(phone = form.phone),
                            profileRevision = it.profileRevision + 1
                        )
                    }
                    _toast.value = "绑定成功"
                }
                is Result.Error -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSubmitting = false, error = result.message))
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSubmitting = false, error = "网络连接失败，请重试"))
                }
            }
        }
    }

    /** 提交绑定邮箱：同时设置登录密码（手机号注册用户的密码由此而来） */
    fun submitBindEmail() {
        val form = _uiState.value.securityForm
        if (form.isSubmitting) return
        ProfileUtils.validateBindEmail(form.email)?.let { error ->
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = error)) }
            return
        }
        if (form.code.length != 6) {
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = "请输入6位邮箱验证码")) }
            return
        }
        if (!ProfileUtils.passwordCriteria(form.password).allMet) {
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = "密码未达到强度要求")) }
            return
        }
        if (form.confirmPassword.isEmpty() || form.password != form.confirmPassword) {
            _uiState.update { it.copy(securityForm = it.securityForm.copy(error = "两次输入的密码不一致")) }
            return
        }
        _uiState.update { it.copy(securityForm = it.securityForm.copy(isSubmitting = true, error = null)) }
        viewModelScope.launch {
            when (val result = authRepository.bindEmail(form.email, form.code, form.password)) {
                is Result.Success -> {
                    // 同 submitBindPhone：乐观回写而非重拉（JWT 里的占位邮箱已过期）
                    securityCountdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            securitySheet = null,
                            securityForm = SecurityFormState(),
                            profile = it.profile?.copy(email = form.email),
                            profileRevision = it.profileRevision + 1
                        )
                    }
                    _toast.value = "邮箱绑定成功"
                }
                is Result.Error -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSubmitting = false, error = result.message))
                }
                Result.NetworkError -> _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(isSubmitting = false, error = "网络连接失败，请重试"))
                }
            }
        }
    }

    private fun startSecurityCountdown() {
        securityCountdownJob?.cancel()
        securityCountdownJob = viewModelScope.launch {
            for (remaining in 60 downTo 1) {
                _uiState.update {
                    it.copy(securityForm = it.securityForm.copy(countdownSeconds = remaining))
                }
                delay(1000)
            }
            _uiState.update { it.copy(securityForm = it.securityForm.copy(countdownSeconds = 0)) }
        }
    }

    // ---------- 注销账号 ----------

    fun openDeleteConfirm() {
        _uiState.update { it.copy(isDeleteConfirmOpen = true) }
    }

    fun closeDeleteConfirm() {
        // 注销请求进行中不允许关闭（防止重复提交/状态错乱）
        if (!_uiState.value.isDeletingAccount) {
            _uiState.update { it.copy(isDeleteConfirmOpen = false) }
        }
    }

    /**
     * 注销账号：服务端级联删除全部数据（OSS 文件尽力清理）；
     * 成功后清空本地会话（SessionCleared 全局广播，「我的」Tab 由 onAuthStateChanged 重置），
     * 并重置本 VM——它是 Activity 作用域共享实例，避免下一账号读到已注销用户的数据。
     */
    fun deleteAccount() {
        if (_uiState.value.isDeletingAccount) return
        _uiState.update { it.copy(isDeletingAccount = true) }
        viewModelScope.launch {
            when (val result = authRepository.deleteAccount()) {
                is Result.Success -> {
                    authRepository.logout()
                    securityCountdownJob?.cancel()
                    _uiState.value = UserProfileUiState(isAccountDeleted = true)
                    _toast.value = "账号已成功注销"
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isDeletingAccount = false, isDeleteConfirmOpen = false) }
                    _toast.value = result.message
                }
                Result.NetworkError -> {
                    _uiState.update { it.copy(isDeletingAccount = false, isDeleteConfirmOpen = false) }
                    _toast.value = "网络连接失败，请重试"
                }
            }
        }
    }

    override fun onCleared() {
        securityCountdownJob?.cancel()
        super.onCleared()
    }
}
