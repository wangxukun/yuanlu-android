package com.wxkzd.yuanlu.feature.auth

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun `sendSmsCode rejects invalid phone`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.sendSmsCode("12345")
        runCurrent()
        assertEquals("请输入11位手机号码", viewModel.uiState.value.error)
        assertEquals(0, repository.smsSendCalls)
    }

    @Test
    fun `sendSmsCode success starts countdown`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.sendSmsCode("13800138000")
        runCurrent()
        assertTrue(viewModel.uiState.value.countdownSeconds in 1..60)
        // 倒计时期间重复发送被拦截
        viewModel.sendSmsCode("13800138000")
        runCurrent()
        assertEquals(1, repository.smsSendCalls)
    }

    @Test
    fun `sendSmsCode captcha risk surfaces degraded message`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            smsResult = Result.Error(429, "触发安全验证，请改用邮箱登录")
        )
        val viewModel = LoginViewModel(repository)
        viewModel.sendSmsCode("13800138000")
        runCurrent()
        assertEquals("触发安全验证，请改用邮箱登录", viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.countdownSeconds)
    }

    @Test
    fun `password login blank input rejected`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(FakeAuthRepository())
        viewModel.loginWithPassword("", "")
        runCurrent()
        assertEquals("请输入邮箱和密码", viewModel.uiState.value.error)
    }

    // ---------- 邮箱注册 ----------

    @Test
    fun `sendEmailCode rejects malformed email`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.sendEmailCode("not-an-email")
        runCurrent()
        assertEquals("请输入正确的邮箱地址", viewModel.uiState.value.error)
        assertEquals(0, repository.emailCodeCalls)
    }

    @Test
    fun `sendEmailCode success starts countdown`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.sendEmailCode("user@example.com")
        runCurrent()
        assertEquals(1, repository.emailCodeCalls)
        assertTrue(viewModel.uiState.value.countdownSeconds in 1..60)
    }

    @Test
    fun `register validates code length password and confirmation`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.register("user@example.com", "123", "123456", "123456")
        assertEquals("请输入6位邮箱验证码", viewModel.uiState.value.error)
        viewModel.register("user@example.com", "123456", "123", "123")
        assertEquals("密码至少需要6位", viewModel.uiState.value.error)
        viewModel.register("user@example.com", "123456", "123456", "654321")
        assertEquals("两次输入的密码不一致", viewModel.uiState.value.error)
        assertEquals(0, repository.signUpCalls)
    }

    @Test
    fun `register success auto logs in with same credentials`() = runTest(dispatcher) {
        val repository = FakeAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.register("user@example.com", "123456", "123456", "123456")
        runCurrent()
        assertEquals(1, repository.signUpCalls)
        assertEquals(1, repository.passwordLoginCalls)
        assertTrue(viewModel.uiState.value.isSuccess)
        assertEquals("注册成功，正在自动登录", viewModel.uiState.value.notice)
    }

    @Test
    fun `register duplicate email surfaces backend message`() = runTest(dispatcher) {
        val repository = FakeAuthRepository(
            signUpResult = Result.Error(400, "电子邮箱已被注册")
        )
        val viewModel = LoginViewModel(repository)
        viewModel.register("user@example.com", "123456", "123456", "123456")
        runCurrent()
        assertEquals("电子邮箱已被注册", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
        assertEquals(0, repository.passwordLoginCalls)
    }

    @Test
    fun `reset clears state`() = runTest(dispatcher) {
        val viewModel = LoginViewModel(FakeAuthRepository())
        viewModel.sendSmsCode("13800138000")
        runCurrent()
        assertTrue(viewModel.uiState.value.countdownSeconds > 0)
        viewModel.reset()
        assertEquals(0, viewModel.uiState.value.countdownSeconds)
        assertNull(viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isSuccess)
    }
}

private class FakeAuthRepository(
    private val smsResult: Result<SmsSendStatus> = Result.Success(SmsSendStatus()),
    private val emailCodeResult: Result<Unit> = Result.Success(Unit),
    private val signUpResult: Result<Unit> = Result.Success(Unit)
) : AuthRepository {
    override suspend fun reportListeningSeconds(seconds: Int): Result<Unit> =
        Result.Success(Unit)
    var smsSendCalls = 0
    var emailCodeCalls = 0
    var signUpCalls = 0
    var passwordLoginCalls = 0
    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> {
        passwordLoginCalls++
        return Result.Success(Unit)
    }
    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun logout() {}
    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> {
        smsSendCalls++
        return smsResult
    }
    override suspend fun sendEmailVerificationCode(email: String): Result<Unit> {
        emailCodeCalls++
        return emailCodeResult
    }
    override suspend fun signUp(email: String, code: String, password: String): Result<Unit> {
        signUpCalls++
        return signUpResult
    }
    override suspend fun sendBindPhoneCode(phone: String): Result<SmsSendStatus> =
        Result.Error(600, "not implemented in fake")
    override suspend fun bindPhone(phone: String, code: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun sendBindEmailCode(email: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun bindEmail(email: String, code: String, password: String): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun deleteAccount(): Result<Unit> =
        Result.Error(600, "not implemented in fake")
    override suspend fun getProfile(): Result<UserProfile> =
        Result.Success(UserProfile(userid = "u1", nickname = "Tester"))
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
