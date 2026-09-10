package com.wxkzd.yuanlu.core.media

import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.model.AchievementItem
import com.wxkzd.yuanlu.domain.model.Episode
import com.wxkzd.yuanlu.domain.model.ProfileStats
import com.wxkzd.yuanlu.domain.model.UserProfile
import com.wxkzd.yuanlu.domain.model.WeeklyActivityItem
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.SmsSendStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ListeningTimeReporter 状态机测试：30s 批量上报 / 暂停止冲刷 / 关闭播放器冲刷 /
 * 游客跳过 / 登出丢弃余量 / 切歌连续计时（对齐 Web GlobalAudio 心跳行为）。
 * advanceTimeBy 带 500ms 余量，规避虚拟时间边界任务的不确定性。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListeningTimeReporterTest {

    private val token = MutableStateFlow<String?>("jwt-token")

    private fun episode(id: String) = Episode(episodeid = id, title = id, duration = 600)

    @Test
    fun `播放中每满30秒上报一批`() = runTest {
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(29_500)
        runCurrent()
        assertEquals(0, repo.calls.size)

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf(30), repo.calls)

        advanceTimeBy(30_500)
        runCurrent()
        assertEquals(listOf(30, 30), repo.calls)
    }

    @Test
    fun `暂停时冲刷未满批次余量`() = runTest {
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(12_500)
        state.value = state.value.copy(isPlaying = false)
        runCurrent()
        assertEquals(listOf(12), repo.calls)
    }

    @Test
    fun `关闭播放器冲刷余量`() = runTest {
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(8_500)
        state.value = PlayerState()
        runCurrent()
        assertEquals(listOf(8), repo.calls)
    }

    @Test
    fun `游客不计时不上报`() = runTest {
        token.value = null
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(40_500)
        state.value = state.value.copy(isPlaying = false)
        runCurrent()
        assertEquals(0, repo.calls.size)
    }

    @Test
    fun `登出时丢弃未上报余量`() = runTest {
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(10_500)
        token.value = null
        runCurrent()
        state.value = state.value.copy(isPlaying = false)
        runCurrent()
        assertEquals(0, repo.calls.size)
    }

    @Test
    fun `切歌不中断计时`() = runTest {
        val state = MutableStateFlow(PlayerState(currentEpisode = episode("e1"), isPlaying = true))
        val repo = RecordingAuthRepository()
        ListeningTimeReporter(state, token, repo, backgroundScope).start()
        runCurrent()

        advanceTimeBy(20_500)
        // 队列自动续播切到 e2：isPlaying 保持 true，计时连续（不在切歌点冲刷）
        state.value = state.value.copy(currentEpisode = episode("e2"))
        runCurrent()
        assertEquals(0, repo.calls.size)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(listOf(30), repo.calls)
    }
}

/** 只记录 reportListeningSeconds 调用；其余方法本测试不会触达 */
private class RecordingAuthRepository : AuthRepository {
    val calls = mutableListOf<Int>()

    override suspend fun reportListeningSeconds(seconds: Int): Result<Unit> {
        calls += seconds
        return Result.Success(Unit)
    }

    override suspend fun loginWithPassword(email: String, password: String): Result<Unit> = error("unused")
    override suspend fun loginWithSms(phone: String, code: String): Result<Unit> = error("unused")
    override suspend fun logout() = error("unused")
    override suspend fun sendSmsCode(phone: String): Result<SmsSendStatus> = error("unused")
    override suspend fun sendEmailVerificationCode(email: String): Result<Unit> = error("unused")
    override suspend fun signUp(email: String, code: String, password: String): Result<Unit> = error("unused")
    override suspend fun getProfile(): Result<UserProfile> = error("unused")
    override suspend fun updateProfile(
        nickname: String,
        bio: String,
        learnLevel: String,
        dailyStudyGoalMins: Int,
        weeklyListeningGoalHours: Int,
        weeklyWordsGoal: Int,
        avatarJpeg: ByteArray?
    ): Result<UserProfile> = error("unused")
    override suspend fun getStatsOverview(): Result<ProfileStats> = error("unused")
    override suspend fun getWeeklyActivity(weekOffset: Int): Result<List<WeeklyActivityItem>> = error("unused")
    override suspend fun getAchievements(): Result<List<AchievementItem>> = error("unused")
    override suspend fun sendBindPhoneCode(phone: String): Result<SmsSendStatus> = error("unused")
    override suspend fun bindPhone(phone: String, code: String): Result<Unit> = error("unused")
    override suspend fun sendBindEmailCode(email: String): Result<Unit> = error("unused")
    override suspend fun bindEmail(email: String, code: String, password: String): Result<Unit> = error("unused")
    override suspend fun deleteAccount(): Result<Unit> = error("unused")
}
