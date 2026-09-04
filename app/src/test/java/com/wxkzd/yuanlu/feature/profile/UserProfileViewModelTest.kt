package com.wxkzd.yuanlu.feature.profile

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.RecentHistoryItem
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
    fun `load populates profile stats achievements and history`() = runTest(dispatcher) {
        val repository = FakeUserAuthRepository(
            profile = profile(),
            stats = ProfileStats(totalHours = 12.3, streakDays = 4, wordsLearned = 66),
            achievements = listOf(
                AchievementItem("k1", "起步", "desc", "🚩", unlocked = true),
                AchievementItem("k2", "小径", "desc", "🌲")
            ),
            history = listOf(
                RecentHistoryItem(historyId = 1, episodeId = "e1", title = "第一集")
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
        assertEquals(1, state.recentHistory.size)
        assertFalse(state.isLoading)
        assertFalse(state.statsLoading)
        assertFalse(state.achievementsLoading)
        assertFalse(state.historyLoading)
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
}

/** 个人中心用 Fake：覆盖 profile/stats/activity/achievements/history 与 updateProfile */
private class FakeUserAuthRepository(
    private val profile: UserProfile = UserProfile(userid = "u1", nickname = "远路客"),
    private val updatedProfile: UserProfile? = null,
    private val stats: ProfileStats = ProfileStats(),
    private val achievements: List<AchievementItem> = emptyList(),
    private val history: List<RecentHistoryItem> = emptyList(),
    private val activity: List<WeeklyActivityItem> = List(7) { WeeklyActivityItem("周${it + 1}", it * 10) },
    var profileError: String? = null,
    var updateError: Result.Error? = null
) : AuthRepository {
    var updateCalls = 0
    var lastAvatar: ByteArray? = null
    var lastLearnLevel: String? = null
    val requestedWeekOffsets = mutableListOf<Int>()

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun logout() = Unit
    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> =
        Result.Success(SmsSendStatus())
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
    override suspend fun getRecentHistory(): Result<List<RecentHistoryItem>> = Result.Success(history)
}
