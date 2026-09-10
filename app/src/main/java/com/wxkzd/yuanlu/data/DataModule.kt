package com.wxkzd.yuanlu.data

import com.wxkzd.yuanlu.core.auth.TokenSource
import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.core.media.ListeningTimeReporter
import com.wxkzd.yuanlu.core.media.PlayerController
import com.wxkzd.yuanlu.core.media.ProgressReporter
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.remote.ContentApi
import com.wxkzd.yuanlu.data.remote.SpeechApi
import com.wxkzd.yuanlu.data.repository.AuthRepositoryImpl
import com.wxkzd.yuanlu.data.repository.ContentRepositoryImpl
import com.wxkzd.yuanlu.data.repository.SpeechRepositoryImpl
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import com.wxkzd.yuanlu.domain.repository.SpeechRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideContentApi(retrofit: Retrofit): ContentApi {
        return retrofit.create(ContentApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        api: AuthApi,
        tokenStore: TokenStore
    ): AuthRepository {
        return AuthRepositoryImpl(api, tokenStore)
    }

    /** 页面 ViewModel 通过 TokenSource 接口订阅全局登录态（生产实现即 TokenStore） */
    @Provides
    @Singleton
    fun provideTokenSource(tokenStore: TokenStore): TokenSource {
        return tokenStore
    }

    @Provides
    @Singleton
    fun provideContentRepository(api: ContentApi, tokenStore: TokenStore): ContentRepository {
        return ContentRepositoryImpl(api, tokenStore)
    }

    @Provides
    @Singleton
    fun provideSpeechApi(retrofit: Retrofit): SpeechApi {
        return retrofit.create(SpeechApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSpeechRepository(api: SpeechApi): SpeechRepository {
        return SpeechRepositoryImpl(api)
    }

    /** 应用级进度上报器：观察全局播放状态流，防抖上报 listening_history */
    @Provides
    @Singleton
    fun provideProgressReporter(
        playerController: PlayerController,
        tokenStore: TokenStore,
        contentRepository: ContentRepository
    ): ProgressReporter {
        return ProgressReporter(
            playerState = playerController.playerState,
            tokenFlow = tokenStore.tokenFlow,
            repository = contentRepository,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }

    /** 应用级学习时长上报器：播放心跳累计 30s 批量上报每日收听秒数（打卡数据源） */
    @Provides
    @Singleton
    fun provideListeningTimeReporter(
        playerController: PlayerController,
        tokenStore: TokenStore,
        authRepository: AuthRepository
    ): ListeningTimeReporter {
        return ListeningTimeReporter(
            playerState = playerController.playerState,
            tokenFlow = tokenStore.tokenFlow,
            repository = authRepository,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        )
    }
}
