package com.fitnessclub.app.di

import android.util.Log
import android.content.Context
import com.fitnessclub.app.BuildConfig
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.auth.TokenRefreshAuthenticator
import com.fitnessclub.app.data.local.TokenManager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder()
        .setLenient()
        .create()
    
    @Provides
    @Singleton
    fun provideTokenManager(
        @ApplicationContext context: Context,
        gson: Gson
    ): TokenManager = TokenManager(context, gson)
    
    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenManager: TokenManager,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .authenticator(tokenRefreshAuthenticator)

        if (BuildConfig.DEBUG) {
            val loggingInterceptor = HttpLoggingInterceptor { message -> Log.d("FC_HTTP", message) }.apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(loggingInterceptor)
        }

        return builder
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()

                // Токен в памяти — без runBlocking на каждый HTTP-запрос.
                if (originalRequest.header("Authorization") == null) {
                    tokenManager.peekAccessToken()?.let { token ->
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                }

                // Франшиза: тот же API host, другая организация в CRM.
                val orgSlug = BuildConfig.ORGANIZATION_SLUG.trim()
                if (orgSlug.isNotEmpty()) {
                    requestBuilder.header("X-Organization-Slug", orgSlug)
                }
                // Чтобы HTTPS return/Sber callback вернул в это APK, а не в соседний flavor.
                requestBuilder.header("X-App-Deep-Link-Scheme", BuildConfig.DEEP_LINK_SCHEME)

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(MockInterceptor())
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideFitnessApi(retrofit: Retrofit): FitnessApi {
        return retrofit.create(FitnessApi::class.java)
    }
}
