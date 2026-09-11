package com.wxkzd.yuanlu.core.media

import android.util.Log
import com.wxkzd.yuanlu.core.network.Result
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
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
 * 挂在应用级作用域（迷你条后台播放时也要计时）；失败批次不重发、由后续批次
 * 继续累计（尽力而为，与 Web 一致），但失败原因必须落日志。
 *
 * 链路埋点：Logcat 过滤 TAG "ListeningTimeReporter" 可见完整链路——
 * 计时开始/停止 → 每批上报请求 → 服务端响应（成功 / HTTP 码）。
 * 历史教训：服务端该接口曾只认 Web Cookie，Android Bearer 心跳恒 401，
 * 而旧版 upload() 丢弃 Result 返回值、catch 分支对 HTTP 错误永远走不到，
 * 故障完全不可见——任何一层静默都会让这类问题重新变成"玄学"。
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
        Log.i(TAG, "启动：播放收听计时心跳（每满 ${batchSeconds}s 上报一批）")
        scope.launch {
            tokenFlow.collect {
                val next = !it.isNullOrEmpty()
                if (next != loggedIn) Log.i(TAG, "登录态变更：loggedIn=$next")
                loggedIn = next
            }
        }
        scope.launch {
            playerState.collect { state -> onState(state) }
        }
    }

    private fun onState(state: PlayerState) {
        if (state.isPlaying && loggedIn) {
            // 播放中 progress 每 200ms 触发一次本回调，只在"未计时→计时"边界打日志
            if (tickJob == null) {
                Log.i(TAG, "▶ 开始累计收听秒数：episode=${state.currentEpisode?.episodeid}")
                startTicker()
            }
        } else {
            val wasTicking = tickJob != null
            stopTicker()
            val pending = unsentSeconds.get()
            when {
                // 暂停/停止/关闭播放器：冲刷余量（余量为 0 时 flush 是 no-op）
                loggedIn && (wasTicking || pending > 0) -> flush()
                // 登出/游客态：余量无法归属到账号，丢弃
                pending > 0 -> Log.w(TAG, "非登录态丢弃未上报秒数：${unsentSeconds.getAndSet(0)}s")
            }
            if (wasTicking) {
                Log.i(TAG, "⏸ 停止累计（isPlaying=${state.isPlaying}, loggedIn=$loggedIn）")
            }
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
        if (seconds > 0) {
            Log.i(TAG, "冲刷未满批次余量：${seconds}s")
            upload(seconds)
        }
    }

    private fun upload(seconds: Int) {
        Log.i(TAG, "→ POST api/auth/update-activity {\"seconds\":$seconds}")
        scope.launch {
            try {
                // 仓库层把 HTTP 错误折叠进 Result 而不抛异常——必须检查返回值，
                // 否则 401/500 会被静默吞掉（旧版正是栽在这里）
                when (val result = repository.reportListeningSeconds(seconds)) {
                    is Result.Success ->
                        Log.i(TAG, "✓ 上报成功：+$seconds s 已计入今日学习时长")
                    is Result.Error ->
                        Log.w(TAG, "✗ 上报被拒：HTTP ${result.code} ${result.message}" +
                            "（401=登录态失效或服务端未认 Bearer），本批 $seconds s 丢弃")
                    Result.NetworkError ->
                        Log.w(TAG, "✗ 上报网络失败（离线/超时），本批 $seconds s 丢弃")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "✗ 上报异常：${e.javaClass.simpleName}: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ListeningTimeReporter"
        private const val DEFAULT_BATCH_SECONDS = 30
    }
}
