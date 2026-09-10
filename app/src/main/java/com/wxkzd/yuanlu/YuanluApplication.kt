package com.wxkzd.yuanlu

import android.app.Application
import com.wxkzd.yuanlu.core.media.ListeningTimeReporter
import com.wxkzd.yuanlu.core.media.ProgressReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class YuanluApplication : Application() {

    @Inject
    lateinit var progressReporter: ProgressReporter

    @Inject
    lateinit var listeningTimeReporter: ListeningTimeReporter

    override fun onCreate() {
        super.onCreate()
        // 播放进度上报需与全局播放器同生命周期（后台播放/跨页面），在应用启动时开启
        progressReporter.start()
        // 每日学习时长心跳（打卡数据源）与进度上报同生命周期开启
        listeningTimeReporter.start()
    }
}
