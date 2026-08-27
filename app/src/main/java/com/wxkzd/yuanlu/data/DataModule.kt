package com.wxkzd.yuanlu.data

import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.remote.ContentApi
import com.wxkzd.yuanlu.data.repository.AuthRepositoryImpl
import com.wxkzd.yuanlu.data.repository.ContentRepositoryImpl
import com.wxkzd.yuanlu.domain.repository.AuthRepository
import com.wxkzd.yuanlu.domain.repository.ContentRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
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

    @Provides
    @Singleton
    fun provideContentRepository(api: ContentApi): ContentRepository {
        return ContentRepositoryImpl(api)
    }
}
