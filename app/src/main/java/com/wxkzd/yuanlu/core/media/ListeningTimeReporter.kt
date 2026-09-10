package com.wxkzd.yuanlu.core.media

import android.util.Log
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * 每日学习时长上报器（对齐 Web components/player/GlobalAudio.tsx 的心跳逻辑）：
 * - 播放中：每秒累计墙钟收听时长，满 [batchSeconds]（默认 30s）上报一次增量
 *   POST api/auth/update-activity { seconds }，服务端累加 user_daily_activity.listeningSeconds
 * - 暂停/停止/关闭播放器（isPlaying=false）：立即冲刷未满批次的余量
 * - 按墙钟计时而非播放器进度：倍速/拖动进度条均不影响（Web setInterval 同口径）
 * - 游客（无 Token）完全跳过且不累计：避免把游客播放误记到之后登录的账号上
 *
 * 挂在应用级作用域（迷你条后台播放时也要计时）；上报失败静默（尽力而为，
 * 与 Web 一致：失败批次不重发，由后续批次继续累计）。
 */
class ListeningTimeReporter(
    private val playerState: StateFlow<PlayerState>,
    private val tokenFlow: Flow<String?>,
    private val repository: AuthRepository,
    private val scope: CoroutineScope,
    private val batchSeconds: Int = DEFAULT_BATCH_SECONDS
) {
    private var loggedIn = false
    private var started = false

    // 未上报的累计秒数（Web unsentSecondsRef 对应物）。
    // 心跳任务与状态收集跑在同 scope 的不同协程上，用原子量避免读改写竞争。
    private val unsentSeconds = AtomicInteger(0)

    // 播放中的 1s 心跳计时任务（Web setInterval(…, 1000) 对应物）
    private var tickJob: Job? = null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            tokenFlow.collect { loggedIn = !it.isNullOrEmpty() }
        }
        scope.launch {
            playerState.collect { state -> onState(state) }
        }
    }

    private fun onState(state: PlayerState) {
        if (state.isPlaying && loggedIn) {
            if (tickJob == null) startTicker()
        } else {
            // 暂停/停止：冲刷余量；登出：丢弃（余量无法归属到账号）
            stopTicker()
            if (loggedIn) flush() else unsentSeconds.set(0)
        }
    }

    private fun startTicker() {
        tickJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val seconds = unsentSeconds.incrementAndGet()
                // CAS 归零：与冲刷并发时只有一方拿到这批秒数，避免双报
                if (seconds >= batchSeconds && unsentSeconds.compareAndSet(seconds, 0)) {
                    upload(seconds)
                }
            }
        }
    }

    private fun stopTicker() {
        tickJob?.cancel()
        tickJob = null
    }

    private fun flush() {
        val seconds = unsentSeconds.getAndSet(0)
        if (seconds > 0) upload(seconds)
    }

    private fun upload(seconds: Int) {
        scope.launch {
            try {
                repository.reportListeningSeconds(seconds)
            } catch (e: Exception) {
                // 时长上报属尽力而为：失败静默，不打断播放体验
                Log.d(TAG, "activity upload failed: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ListeningTimeReporter"
        private const val DEFAULT_BATCH_SECONDS = 30
    }
}
