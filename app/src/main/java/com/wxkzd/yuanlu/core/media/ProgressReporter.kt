package com.wxkzd.yuanlu.core.media

import android.util.Log
import androidx.media3.common.Player
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 收听进度上报器（对齐 Web lib/hooks/useSaveProgress.ts 的策略）：
 * - 播放中：与上次保存位置相差超过 [intervalMs] 时上报（默认 15s）
 * - 暂停/停止：立即上报（位置有效且不同于上次保存时）
 * - 播完（STATE_ENDED 或进入结尾 5s）：上报 isFinished=true，且不重复覆盖
 * - 切集/关闭播放器：冲刷上一集的最终进度（isFinished=false，避免覆盖已完成标记）
 * - 游客（无 Token）完全跳过：progress 接口需要登录，避免无效请求
 *
 * 挂在应用级作用域（播放可能长于任何 ViewModel/页面生命周期，迷你条后台播放时也要上报）。
 * 上报失败静默（进度属尽力而为，不打扰用户）。
 */
class ProgressReporter(
    private val playerState: StateFlow<PlayerState>,
    private val tokenFlow: Flow<String?>,
    private val repository: ContentRepository,
    private val scope: CoroutineScope,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS
) {
    private var loggedIn = false
    private var started = false

    // 当前追踪的剧集与位置（切集冲刷用）
    private var trackedEpisodeId: String? = null
    private var trackedPositionMs = 0L

    // 去重：上次成功发起上报的位置与完成态
    private var lastSavedPositionMs = 0L
    private var lastSaveWasFinished = false

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
        val episodeId = state.currentEpisode?.episodeid

        // 1. 切集 / 清空音轨：先冲刷上一集最终进度
        if (episodeId != trackedEpisodeId) {
            val previous = trackedEpisodeId
            if (previous != null &&
                !lastSaveWasFinished &&
                trackedPositionMs > 0L &&
                trackedPositionMs != lastSavedPositionMs
            ) {
                upload(previous, trackedPositionMs / 1000f, isFinished = false)
            }
            trackedEpisodeId = episodeId
            trackedPositionMs = 0L
            lastSavedPositionMs = 0L
            lastSaveWasFinished = false
        }

        if (episodeId == null) return
        trackedPositionMs = state.currentPosition
        if (!loggedIn) return

        val position = state.currentPosition

        // 2. 播完：标记 finished，只报一次
        if (state.playbackState == Player.STATE_ENDED) {
            if (!lastSaveWasFinished) {
                upload(episodeId, position / 1000f, isFinished = true)
                lastSaveWasFinished = true
                lastSavedPositionMs = position
            }
            return
        }

        // 已标记完成：后续（如单曲循环重播、再次暂停）不再覆盖进度，直到切集
        if (lastSaveWasFinished) return

        // 3. 暂停/停止：位置有效且未报过 → 立即上报（结尾 5s 内视为听完）
        if (!state.isPlaying) {
            if (position > 0L && position != lastSavedPositionMs) {
                val finished = state.duration > 0 && position >= state.duration - NEAR_END_MS
                upload(episodeId, position / 1000f, isFinished = finished)
                lastSaveWasFinished = finished
                lastSavedPositionMs = position
            }
            return
        }

        // 4. 播放中：与上次保存位置相差超过间隔 → 周期上报
        if (position > 0L && abs(position - lastSavedPositionMs) >= intervalMs) {
            upload(episodeId, position / 1000f, isFinished = false)
            lastSavedPositionMs = position
        }
    }

    private fun upload(episodeId: String, progressSeconds: Float, isFinished: Boolean) {
        scope.launch {
            try {
                repository.updateEpisodeProgress(episodeId, progressSeconds, isFinished)
            } catch (e: Exception) {
                // 进度上报属尽力而为：失败静默，不打断播放体验
                Log.d(TAG, "progress upload failed for $episodeId: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "ProgressReporter"
        private const val DEFAULT_INTERVAL_MS = 15_000L
        private const val NEAR_END_MS = 5_000L
    }
}
