package com.wxkzd.yuanlu.feature.profile

import com.wxkzd.yuanlu.FakeContentRepository
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.feature.favorites.FavoriteCenter
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `auth state login loads profile`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            profile = UserProfile(
                userid = "u1",
                nickname = "远路客",
                email = "a@b.com",
                role = "PREMIUM"
            )
        )
        val viewModel = ProfileViewModel(repository, FavoriteCenter(FakeContentRepository()))
        viewModel.onAuthStateChanged(loggedIn = true)
        runCurrent()

        assertEquals("远路客", viewModel.uiState.value.profile?.nickname)
        assertEquals("PREMIUM", viewModel.uiState.value.profile?.role)
    }

    @Test
    fun `auth state guest clears profile`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileViewModel(repository, FavoriteCenter(FakeContentRepository()))
        viewModel.onAuthStateChanged(true)
        runCurrent()
        assertEquals("远路客", viewModel.uiState.value.profile?.nickname)

        viewModel.onAuthStateChanged(false)
        assertNull(viewModel.uiState.value.profile)
    }

    @Test
    fun `logout clears profile`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = ProfileViewModel(repository, FavoriteCenter(FakeContentRepository()))
        viewModel.onAuthStateChanged(true)
        runCurrent()
        viewModel.logout()
        runCurrent()

        assertNull(viewModel.uiState.value.profile)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `load error surfaces message and retry works`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(profileError = "请先登录")
        val viewModel = ProfileViewModel(repository, FavoriteCenter(FakeContentRepository()))
        viewModel.onAuthStateChanged(true)
        runCurrent()
        assertEquals("请先登录", viewModel.uiState.value.error)

        repository.profileError = null
        viewModel.load()
        runCurrent()
        assertEquals("远路客", viewModel.uiState.value.profile?.nickname)
    }
}

private class FakeAuthRepository(
    var profileError: String? = null,
    profile: UserProfile = UserProfile(userid = "u1", nickname = "远路客")
) : AuthRepository {
    private val profileResult = profile
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
    override suspend fun getProfile(): Result<UserProfile> =
        profileError?.let { Result.Error(401, it) } ?: Result.Success(profileResult)
    override suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile> = Result.Error(600, "not implemented")
    override suspend fun getStatsOverview(): Result<ProfileStats> =
        Result.Error(600, "not implemented")
    override suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>> =
        Result.Error(600, "not implemented")
    override suspend fun getAchievements(): Result<List<AchievementItem>> =
        Result.Error(600, "not implemented")
}
