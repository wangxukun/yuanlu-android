package com.wxkzd.yuanlu.feature.profile

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.SmsSendStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile() = UserProfile(
        userid = "u1",
        nickname = "远路客",
        bio = null,
        learnLevel = "Intermediate",
        email = "walker@yuanlu.com",
        phone = "13812348000",
        role = "USER",
        createAt = "2026-01-15T08:00:00.000Z",
        dailyStudyGoalMins = 30,
        weeklyListeningGoalHours = 5,
        weeklyWordsGoal = 80
    )

    // ---------- 加载 ----------

    @Test
    fun `load populates profile stats and achievements`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile(),
            stats = ProfileStats(totalHours = 12.3, streakDays = 4, wordsLearned = 66),
            achievements = listOf(
                AchievementItem("k1", "起步", "desc", "🚩", unlocked = true),
                AchievementItem("k2", "小径", "desc", "🌲")
            )
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals("远路客", state.profile?.nickname)
        assertEquals(12.3, state.stats?.totalHours ?: 0.0, 0.001)
        assertEquals(4, state.stats?.streakDays)
        assertEquals(66, state.stats?.wordsLearned)
        assertEquals(2, state.achievements.size)
        assertFalse(state.isLoading)
        assertFalse(state.statsLoading)
        assertFalse(state.achievementsLoading)
    }

    @Test
    fun `load error surfaces message`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profileError = "请先登录")
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        assertEquals("请先登录", viewModel.uiState.value.loadError)
        assertNull(viewModel.uiState.value.profile)
    }

    @Test
    fun `change week reloads activity`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository()
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.changeWeek(1)
        runCurrent()

        assertEquals(1, viewModel.uiState.value.weekOffset)
        assertEquals(1, repository.requestedWeekOffsets.count { it == 1 })
    }

    // ---------- 编辑表单 ----------

    @Test
    fun `open edit seeds form from profile with defaults`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile().copy(
                nickname = null,
                bio = null,
                learnLevel = null,
                dailyStudyGoalMins = null,
                weeklyListeningGoalHours = null,
                weeklyWordsGoal = null
            )
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()

        val state = viewModel.uiState.value
        assertTrue(state.isEditOpen)
        assertEquals(EditProfileTab.PROFILE, state.editTab)
        assertEquals("", state.formNickname)
        // 空简介回填默认座右铭、空水平回填 Beginner（对齐 Web 弹窗初始化）
        assertEquals(ProfileUtils.DEFAULT_BIO, state.formBio)
        assertEquals("Beginner", state.formLearnLevel)
        assertEquals(20, state.formDailyGoalMins)
        assertEquals(2, state.formWeeklyHours)
        assertEquals(50, state.formWeeklyWords)
    }

    @Test
    fun `update nickname clears error after fix`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile())
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        viewModel.updateNickname("")
        viewModel.saveProfile()
        assertNotNull(viewModel.uiState.value.nicknameError)

        viewModel.updateNickname("新昵称")
        assertNull(viewModel.uiState.value.nicknameError)
    }

    @Test
    fun `save blocks invalid nickname and skips repository`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile())
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        viewModel.updateNickname("   ")
        viewModel.saveProfile()

        assertEquals("请输入昵称", viewModel.uiState.value.nicknameError)
        assertEquals(0, repository.updateCalls)
        assertTrue(viewModel.uiState.value.isEditOpen)
    }

    @Test
    fun `save blocks overlong nickname and bio`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile())
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        viewModel.updateNickname("昵".repeat(21))
        viewModel.updateBio("长".repeat(101))
        viewModel.saveProfile()

        assertEquals("昵称不能超过 20 个字", viewModel.uiState.value.nicknameError)
        assertEquals("简介不能超过 100 个字", viewModel.uiState.value.bioError)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun `save success merges profile bumps revision closes sheet and toasts`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile(),
            updatedProfile = profile().copy(
                nickname = "新远路客",
                bio = "新签名",
                dailyStudyGoalMins = 45,
                weeklyListeningGoalHours = 8,
                weeklyWordsGoal = 120
            )
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        viewModel.updateNickname("新远路客")
        viewModel.updateBio("新签名")
        viewModel.updateDailyGoalMins(45)
        viewModel.updateWeeklyHours(8)
        viewModel.updateWeeklyWords(120)
        val avatar = byteArrayOf(1, 2, 3)
        viewModel.onAvatarPicked(avatar)
        viewModel.saveProfile()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isEditOpen)
        assertEquals("新远路客", state.profile?.nickname)
        assertEquals(45, state.profile?.dailyStudyGoalMins)
        // PUT 响应不含 User 嵌套：邮箱/角色沿用旧资料补齐
        assertEquals("walker@yuanlu.com", state.profile?.email)
        assertEquals("USER", state.profile?.role)
        assertEquals(1, state.profileRevision)
        assertNull(state.formAvatar)
        assertEquals("设置已更新", viewModel.toast.value)
        assertEquals(1, repository.updateCalls)
        assertEquals(avatar, repository.lastAvatar)
        assertEquals("Intermediate", repository.lastLearnLevel)
    }

    @Test
    fun `save failure keeps sheet open and toasts error`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile(),
            updateError = Result.Error(500, "服务器开小差了")
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        viewModel.saveProfile()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isEditOpen)
        assertFalse(state.isSaving)
        assertEquals("服务器开小差了", viewModel.toast.value)
        assertEquals(0, state.profileRevision)
    }

    @Test
    fun `avatar pick bumps version`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile())
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openEdit()
        val before = viewModel.uiState.value.formAvatarVersion
        viewModel.onAvatarPicked(byteArrayOf(9))
        assertEquals(before + 1, viewModel.uiState.value.formAvatarVersion)
    }

    // ---------- 账号与安全：绑定手机 ----------

    @Test
    fun `send bind phone code shows notice and starts countdown`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile().copy(phone = null))
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindPhoneSheet()
        viewModel.updateSecurityPhone("13812348000")
        viewModel.sendSecurityPhoneCode()
        runCurrent()

        val form = viewModel.uiState.value.securityForm
        assertEquals("验证码发送成功", form.notice)
        assertEquals(60, form.countdownSeconds)
        assertFalse(form.isSendingCode)
    }

    @Test
    fun `bind phone success optimistically writes phone closes sheet and toasts`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile().copy(phone = null))
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindPhoneSheet()
        viewModel.updateSecurityPhone("13812348000")
        viewModel.updateSecurityCode("123456")
        viewModel.submitBindPhone()
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.securitySheet)
        assertEquals("13812348000", state.profile?.phone)
        assertEquals(1, state.profileRevision)
        assertEquals("绑定成功", viewModel.toast.value)
        assertEquals(1, repository.bindPhoneCalls)
    }

    @Test
    fun `bind phone invalid phone blocks repository with error`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile().copy(phone = null))
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindPhoneSheet()
        viewModel.updateSecurityPhone("12345")
        viewModel.updateSecurityCode("12345")
        viewModel.submitBindPhone()

        assertEquals("请输入有效的11位手机号码", viewModel.uiState.value.securityForm.error)
        assertEquals(0, repository.bindPhoneCalls)
        assertEquals(SecuritySheet.BIND_PHONE, viewModel.uiState.value.securitySheet)
    }

    @Test
    fun `bind phone failure keeps sheet open and surfaces backend message`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile().copy(phone = null),
            bindPhoneError = Result.Error(400, "该手机号已被其他账号绑定")
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindPhoneSheet()
        viewModel.updateSecurityPhone("13812348000")
        viewModel.updateSecurityCode("123456")
        viewModel.submitBindPhone()
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(SecuritySheet.BIND_PHONE, state.securitySheet)
        assertFalse(state.securityForm.isSubmitting)
        assertEquals("该手机号已被其他账号绑定", state.securityForm.error)
    }

    // ---------- 账号与安全：绑定邮箱 ----------

    @Test
    fun `bind email success writes real email and replaces placeholder`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile().copy(
                phone = "13812348000",
                email = "13812348000@placeholder.yuanlu.com"
            )
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindEmailSheet()
        viewModel.updateSecurityEmail("walker@yuanlu.com")
        viewModel.updateSecurityCode("654321")
        viewModel.updateSecurityPassword("abcd1234")
        viewModel.updateSecurityConfirmPassword("abcd1234")
        viewModel.submitBindEmail()
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.securitySheet)
        assertEquals("walker@yuanlu.com", state.profile?.email)
        assertEquals(1, state.profileRevision)
        assertEquals("邮箱绑定成功", viewModel.toast.value)
        assertEquals(1, repository.bindEmailCalls)
    }

    @Test
    fun `bind email weak password blocks submit`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile().copy(email = "13812348000@placeholder.yuanlu.com")
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindEmailSheet()
        viewModel.updateSecurityEmail("walker@yuanlu.com")
        viewModel.updateSecurityCode("654321")
        viewModel.updateSecurityPassword("abc123")
        viewModel.updateSecurityConfirmPassword("abc123")
        viewModel.submitBindEmail()

        assertEquals("密码未达到强度要求", viewModel.uiState.value.securityForm.error)
        assertEquals(0, repository.bindEmailCalls)
    }

    @Test
    fun `bind email mismatched confirmation blocks submit`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile().copy(email = "13812348000@placeholder.yuanlu.com")
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openBindEmailSheet()
        viewModel.updateSecurityEmail("walker@yuanlu.com")
        viewModel.updateSecurityCode("654321")
        viewModel.updateSecurityPassword("abcd1234")
        viewModel.updateSecurityConfirmPassword("abcd12345")
        viewModel.submitBindEmail()

        assertEquals("两次输入的密码不一致", viewModel.uiState.value.securityForm.error)
        assertEquals(0, repository.bindEmailCalls)
    }

    // ---------- 注销账号 ----------

    @Test
    fun `delete account success clears session resets state and flags exit`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(profile = profile())
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openDeleteConfirm()
        viewModel.deleteAccount()
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state.isAccountDeleted)
        assertFalse(state.isDeleteConfirmOpen)
        assertFalse(state.isDeletingAccount)
        assertNull(state.profile)
        assertEquals("账号已成功注销", viewModel.toast.value)
        assertEquals(1, repository.deleteAccountCalls)
        // 服务端删除成功后必须清本地 Token（SessionCleared 全局广播）
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `delete account failure closes dialog and toasts error`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile(),
            deleteAccountError = Result.Error(400, "注销失败，请重试")
        )
        val viewModel = UserProfileViewModel(repository)
        viewModel.load()
        runCurrent()

        viewModel.openDeleteConfirm()
        viewModel.deleteAccount()
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isAccountDeleted)
        assertFalse(state.isDeleteConfirmOpen)
        assertFalse(state.isDeletingAccount)
        assertNotNull(state.profile) // 资料保留，仅提示失败
        assertEquals("注销失败，请重试", viewModel.toast.value)
        assertEquals(0, repository.logoutCalls)
    }
}

/** 个人中心用 Fake：覆盖 profile/stats/activity/achievements、updateProfile 与账号安全操作 */
private class FakeUserAuthRepository(
    private val profile: UserProfile = UserProfile(userid = "u1", nickname = "远路客"),
    private val updatedProfile: UserProfile? = null,
    private val stats: ProfileStats = ProfileStats(),
    private val achievements: List<AchievementItem> = emptyList(),
    private val activity: List<WeeklyActivityItem> = List(7) { WeeklyActivityItem("周${it + 1}", it * 10) },
    var profileError: String? = null,
    var updateError: Result.Error? = null,
    var bindPhoneError: Result.Error? = null,
    var bindEmailError: Result.Error? = null,
    var deleteAccountError: Result.Error? = null
) : AuthRepository {
    var updateCalls = 0
    var lastAvatar: ByteArray? = null
    var lastLearnLevel: String? = null
    val requestedWeekOffsets = mutableListOf<Int>()
    var bindPhoneCalls = 0
    var bindEmailCalls = 0
    var deleteAccountCalls = 0
    var logoutCalls = 0

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun logout() {
        logoutCalls++
    }
    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> =
        Result.Success(SmsSendStatus())
    override suspend fun sendEmailVerificationCode(email: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun signUp(email: String, code: String, password: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun sendBindPhoneCode(phone: String): Result<SmsSendStatus> =
        Result.Success(SmsSendStatus())
    override suspend fun bindPhone(phone: String, code: String): Result<Unit> {
        bindPhoneCalls++
        bindPhoneError?.let { return it }
        return Result.Success(Unit)
    }
    override suspend fun sendBindEmailCode(email: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun bindEmail(email: String, code: String, password: String): Result<Unit> {
        bindEmailCalls++
        bindEmailError?.let { return it }
        return Result.Success(Unit)
    }
    override suspend fun deleteAccount(): Result<Unit> {
        deleteAccountCalls++
        deleteAccountError?.let { return it }
        return Result.Success(Unit)
    }
    override suspend fun getProfile(): Result<UserProfile> =
        profileError?.let { Result.Error(401, it) } ?: Result.Success(profile)
    override suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile> {
        updateCalls++
        lastAvatar = avatarJpeg
        lastLearnLevel = learnLevel
        updateError?.let { return it }
        return Result.Success(
            (updatedProfile ?: profile).copy(
                nickname = nickname,
                bio = bio,
                learnLevel = learnLevel,
                dailyStudyGoalMins = dailyStudyGoalMins,
                weeklyListeningGoalHours = weeklyListeningGoalHours,
                weeklyWordsGoal = weeklyWordsGoal
            )
        )
    }
    override suspend fun getStatsOverview(): Result<ProfileStats> = Result.Success(stats)
    override suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>> {
        requestedWeekOffsets += weekOffset
        return Result.Success(activity)
    }
    override suspend fun getAchievements(): Result<List<AchievementItem>> = Result.Success(achievements)
}
