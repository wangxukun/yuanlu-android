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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 编辑资料弹窗的两个表单区块（对齐 Web EditProfileModal 的 tabs） */
enum class EditProfileTab { PROFILE, GOALS }

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
    val isSaving: Boolean = false
)

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

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
}
