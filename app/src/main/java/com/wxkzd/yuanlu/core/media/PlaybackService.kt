package com.wxkzd.yuanlu.core.media

import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var playerController: PlayerController
    
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSession.Builder(this, playerController.exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            // 注意：不能 release 共享的 ExoPlayer——它是 Hilt 单例，由 App 进程持有；
            // Service 销毁重建（任务划掉/系统回收）后若已释放，后续播放会崩溃或行为异常。
            // 仅释放 session 自身；播放器交由 PlayerController/App 生命周期管理。
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
