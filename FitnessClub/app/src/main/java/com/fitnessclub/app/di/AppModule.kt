package com.fitnessclub.app.di

import android.util.Log
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // Прод: только HTTPS. http:// даёт 301→https, OkHttp при редиректе превращает POST в GET → 405 на /auth/login.
    // Локально с compose «ports: 8000:8000» — http://10.0.2.2:8000/api/v1/ (эмулятор).
    private const val BASE_URL = "https://worldcashfit.ru/api/v1/"
    
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
        // Явный тег FC_HTTP — в Logcat ищите по нему; уровень Verbose, иначе строки OkHttp легко «теряются».
        val loggingInterceptor = HttpLoggingInterceptor { message -> Log.d("FC_HTTP", message) }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestBuilder = originalRequest.newBuilder()
                
                runBlocking {
                    // Не подменяем Authorization, если Retrofit уже передал (например refresh в POST /auth/refresh).
                    if (originalRequest.header("Authorization") == null) {
                        tokenManager.getAccessToken()?.let { token ->
                            requestBuilder.header("Authorization", "Bearer $token")
                        }
                    }
                    // Add X-User-Id for API to identify current user (after login/register)
                    tokenManager.getUser().first()?.id?.let { userId ->
                        requestBuilder.header("X-User-Id", userId)
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
