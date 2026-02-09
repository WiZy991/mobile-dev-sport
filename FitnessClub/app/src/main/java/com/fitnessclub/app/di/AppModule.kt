package com.fitnessclub.app.di

import android.content.Context
import com.fitnessclub.app.data.api.FitnessApi
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
    
    private const val BASE_URL = "http://127.0.0.1:8000/api/v1/"
    
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
    fun provideOkHttpClient(tokenManager: TokenManager): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                
                // Add auth token if available
                kotlinx.coroutines.runBlocking {
                    tokenManager.getAccessToken()?.let { token ->
                        requestBuilder.addHeader("Authorization", "Bearer $token")
                    }
                }
                
                chain.proceed(requestBuilder.build())
            }
            // Add mock interceptor for testing without real backend
            .addInterceptor(MockInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
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
