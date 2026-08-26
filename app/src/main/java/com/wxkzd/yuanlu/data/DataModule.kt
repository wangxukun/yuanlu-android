package com.wxkzd.yuanlu.data

import com.wxkzd.yuanlu.core.auth.TokenStore
import com.wxkzd.yuanlu.data.remote.AuthApi
import com.wxkzd.yuanlu.data.repository.AuthRepositoryImpl
import com.wxkzd.yuanlu.domain.repository.AuthRepository
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
    fun provideAuthRepository(
        api: AuthApi,
        tokenStore: TokenStore
    ): AuthRepository {
        return AuthRepositoryImpl(api, tokenStore)
    }
}
