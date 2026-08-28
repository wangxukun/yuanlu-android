package com.wxkzd.yuanlu.feature.auth

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.UserProfile
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
    private val smsResult: Result<SmsSendStatus> = Result.Success(SmsSendStatus())
) : AuthRepository {
    var smsSendCalls = 0
    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> =
        Result.Success(Unit)
    override suspend fun logout() {}
    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> {
        smsSendCalls++
        return smsResult
    }
    override suspend fun getProfile(): Result<UserProfile> =
        Result.Success(UserProfile(userid = "u1", nickname = "Tester"))
}
