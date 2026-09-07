package com.wxkzd.yuanlu.core.media

import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.wxkzd.yuanlu.domain.model.Episode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    val exoPlayer: ExoPlayer
) {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private var progressJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /** playAll 入队的剧集快照（mediaId → Episode），自动续播时同步 currentEpisode 用 */
    private var queue: List<Episode> = emptyList()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startProgressUpdate() else stopProgressUpdate()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.update {
                    it.copy(
                        playbackState = playbackState,
                        duration = exoPlayer.duration.coerceAtLeast(0L)
                    )
                }
                // 定时关闭-按集数：无队列场景下 ENDED 即"播完一集"，递减剩余集数
                if (playbackState == Player.STATE_ENDED) {
                    handleSleepOnEpisodeEnded()
                }
            }

            /**
             * 倍速参数变化（含系统媒体控件/蓝牙/车机等外部下发）同步进状态流。
             * 不监听会导致 UI 显示 1x 而实际快放的静默 desync——正是"没设置却变快"的根因。
             */
            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playerState.update { it.copy(playbackRate = playbackParameters.speed) }
            }

            /**
             * 队列自动续播（播放全部）：mediaId 切换时同步 currentEpisode/进度/时长，
             * 迷你条与全屏播放器随之切换到新剧集；ProgressReporter 借 episodeid 变化
             * 冲刷上一集最终进度。仅自动切换（AUTO）结算「按集数」定时关闭。
             */
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.mediaId ?: return
                val episode = queue.firstOrNull { it.episodeid == mediaId } ?: return
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    handleSleepOnEpisodeEnded()
                }
                _playerState.update {
                    it.copy(
                        currentEpisode = episode,
                        currentPosition = 0L,
                        duration = if (episode.duration > 0) episode.duration * 1000L else 0L
                    )
                }
            }
        })
    }

    /**
     * 起播一首剧集。[intensive] = true 表示本次起播来自「精听」入口，
     * 会在 PlayerState 中带上精听标记，供迷你播放条/全屏播放器展示指示器。
     */
    fun play(episode: Episode, startPositionMs: Long = 0L, intensive: Boolean = false) {
        queue = emptyList()
        val mediaItem = MediaItem.Builder()
            .setUri(episode.audioUrl)
            .setMediaId(episode.episodeid)
            .build()

        // 起播重置为正常倍速：防止上一首/外部控件残留的快放静默延续到新播放。
        // 注意只在当前速度非 1x 时才调用——无条件调用会显式激活 Sonic 变速管线，
        // 模拟器/部分设备的音频后端上会出现"前 1~2 秒正常、随后加速+杂音"的欠载失真。
        resetSpeedIfChanged()
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (startPositionMs > 0) {
            exoPlayer.seekTo(startPositionMs)
        }
        exoPlayer.play()

        _playerState.update {
            it.copy(
                currentEpisode = episode,
                currentPosition = startPositionMs,
                duration = if (episode.duration > 0) episode.duration * 1000L else 0L,
                playbackRate = 1f,
                isIntensiveMode = intensive
            )
        }
    }

    /** 手动切换精听标记（例如从全屏播放器进入精听页时补标记），不改变播放行为 */
    fun setIntensiveMode(enabled: Boolean) {
        _playerState.update { it.copy(isIntensiveMode = enabled) }
    }

    /**
     * 队列播放（学习路径「播放全部/随机播放」）：一次 MediaItems 入队，
     * 播完自动续播下一集（onMediaItemTransition 同步 currentEpisode）。
     * [shuffle] = true 时打乱入队顺序（洗牌后从新的第一集起播）。
     */
    fun playAll(episodes: List<Episode>, startPositionMs: Long = 0L, shuffle: Boolean = false) {
        if (episodes.isEmpty()) return
        val ordered = if (shuffle) episodes.shuffled() else episodes
        val mediaItems = ordered.map { episode ->
            MediaItem.Builder()
                .setUri(episode.audioUrl)
                .setMediaId(episode.episodeid)
                .build()
        }
        queue = ordered

        // 起播重置为正常倍速（同 [play] 的防残留快放说明）
        resetSpeedIfChanged()
        exoPlayer.setMediaItems(mediaItems, 0, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.play()

        _playerState.update {
            it.copy(
                currentEpisode = ordered.first(),
                currentPosition = startPositionMs,
                duration = if (ordered.first().duration > 0) ordered.first().duration * 1000L else 0L,
                playbackRate = 1f,
                isIntensiveMode = false
            )
        }
    }

    /**
     * 关闭播放器（对齐 Web closePlayer）：停止并清空当前音轨，
     * PlayerState 复位 → 迷你播放条/全屏播放器随之隐藏。倍速与循环一并复位。
     */
    fun stop() {
        sleepJob?.cancel()
        sleepJob = null
        queue = emptyList()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        resetSpeedIfChanged()
        _playerState.value = PlayerState()
    }

    /** 设置播放倍速（Media3 setPlaybackSpeed，立即生效并同步进状态流） */
    fun setPlaybackRate(rate: Float) {
        // 与当前一致时跳过，避免无谓触发变速管线重配置
        if (exoPlayer.playbackParameters.speed != rate) {
            exoPlayer.setPlaybackSpeed(rate)
        }
        _playerState.update { it.copy(playbackRate = rate) }
    }

    /** 仅在实际速度 ≠ 1x 时才显式复位；速度已是 1x 时保持默认播放路径（不经过变速处理器） */
    private fun resetSpeedIfChanged() {
        if (exoPlayer.playbackParameters.speed != 1f) {
            exoPlayer.setPlaybackSpeed(1f)
        }
    }

    /** 倍速循环切换：1 → 1.25 → 1.5 → 2 → 0.75 → 1（对齐 Web cyclePlaybackRate） */
    fun cyclePlaybackRate() {
        val index = PLAYBACK_RATES.indexOfFirst { it == _playerState.value.playbackRate }
        val next = PLAYBACK_RATES[(index + 1).mod(PLAYBACK_RATES.size)]
        setPlaybackRate(next)
    }

    /** 循环模式切换：不循环 → 列表循环 → 单曲循环（映射 Media3 repeatMode） */
    fun toggleLoopMode() {
        val next = when (_playerState.value.loopMode) {
            LoopMode.NONE -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.ONE
            LoopMode.ONE -> LoopMode.NONE
        }
        setLoopMode(next)
    }

    /** 直接设定循环模式（精听页 FAB：单集循环 REPEAT_ONE ↔ 多集顺序播放 REPEAT_OFF） */
    fun setLoopMode(mode: LoopMode) {
        exoPlayer.repeatMode = when (mode) {
            LoopMode.NONE -> Player.REPEAT_MODE_OFF
            LoopMode.ALL -> Player.REPEAT_MODE_ALL
            LoopMode.ONE -> Player.REPEAT_MODE_ONE
        }
        _playerState.update { it.copy(loopMode = mode) }
    }

    companion object {
        private val PLAYBACK_RATES = floatArrayOf(1f, 1.25f, 1.5f, 2f, 0.75f)
    }

    // ---------- 定时关闭（全屏播放器「定时」弹层） ----------

    private var sleepJob: Job? = null

    /**
     * 应用定时关闭配置：按时间用协程倒计时到点暂停；按集数/播完本集在
     * STATE_ENDED 时结算。同时记忆为「上次定时」供弹层 Switch 快捷重开。
     */
    fun applySleepConfig(config: SleepConfig) {
        sleepJob?.cancel()
        sleepJob = null
        when (config) {
            is SleepConfig.Minutes -> {
                val endsAt = SystemClock.elapsedRealtime() + config.minutes * 60_000L
                _playerState.update {
                    it.copy(
                        sleepTimer = SleepTimer(config, config.describe(), endsAt),
                        lastSleepConfig = config
                    )
                }
                sleepJob = scope.launch {
                    delay(config.minutes * 60_000L)
                    exoPlayer.pause()
                    clearSleepTimer()
                }
            }
            is SleepConfig.Episodes -> _playerState.update {
                it.copy(
                    sleepTimer = SleepTimer(config, config.describe()),
                    lastSleepConfig = config
                )
            }
            SleepConfig.EpisodeEnd -> _playerState.update {
                it.copy(
                    sleepTimer = SleepTimer(config, config.describe()),
                    lastSleepConfig = config
                )
            }
        }
    }

    /** 取消定时关闭 */
    fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _playerState.update { it.copy(sleepTimer = null) }
    }

    private fun clearSleepTimer() {
        sleepJob?.cancel()
        sleepJob = null
        _playerState.update { it.copy(sleepTimer = null) }
    }

    /** 播完一集（STATE_ENDED）结算定时任务：递减剩余集数 / 播完本集即清除 */
    private fun handleSleepOnEpisodeEnded() {
        val timer = _playerState.value.sleepTimer ?: return
        when (val config = timer.config) {
            is SleepConfig.Episodes -> {
                val remaining = config.count - 1
                if (remaining <= 0) {
                    clearSleepTimer()
                } else {
                    val next = SleepConfig.Episodes(remaining)
                    _playerState.update { it.copy(sleepTimer = SleepTimer(next, next.describe())) }
                }
            }
            SleepConfig.EpisodeEnd -> clearSleepTimer()
            is SleepConfig.Minutes -> Unit // 时间到由倒计时协程处理
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    /** 仅暂停（点词查词等场景临时打断，对齐 Web wordClick pause 行为） */
    fun pause() {
        exoPlayer.pause()
    }

    /**
     * 恢复播放（听写成功后流转下一句等场景）：仅在已有媒体且非空闲态时生效，
     * 不触碰倍速、循环模式与队列——保持既有播放器控制状态不变。
     */
    fun resume() {
        if (exoPlayer.playbackState != Player.STATE_IDLE) {
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer.seekTo(positionMs.coerceIn(0L, exoPlayer.duration.takeIf { it > 0 } ?: Long.MAX_VALUE))
        _playerState.update { it.copy(currentPosition = positionMs) }
    }

    fun seekBy(deltaMs: Long) {
        seekTo(exoPlayer.currentPosition + deltaMs)
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                _playerState.update {
                    it.copy(
                        currentPosition = exoPlayer.currentPosition,
                        bufferedPosition = exoPlayer.bufferedPosition
                    )
                }
                delay(200)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }
}
