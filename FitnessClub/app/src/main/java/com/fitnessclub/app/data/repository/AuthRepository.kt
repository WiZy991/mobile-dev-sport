package com.fitnessclub.app.data.repository

import com.fitnessclub.app.data.api.ApiResult
import com.fitnessclub.app.data.api.FitnessApi
import com.fitnessclub.app.data.local.TokenManager
import com.fitnessclub.app.data.model.AuthResponse
import com.fitnessclub.app.data.model.LoginRequest
import com.fitnessclub.app.data.model.RegisterRequest
import com.fitnessclub.app.data.model.User
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: FitnessApi,
    private val tokenManager: TokenManager
) {
    
    fun login(email: String, password: String): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.login(LoginRequest(email, password))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                emit(ApiResult.Success(authResponse.user))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка входа", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    fun register(name: String, email: String, phone: String, password: String): Flow<ApiResult<User>> = flow {
        emit(ApiResult.Loading)
        try {
            val response = api.register(RegisterRequest(email, password, phone, name))
            if (response.isSuccessful && response.body() != null) {
                val authResponse = response.body()!!
                tokenManager.saveTokens(authResponse.token, authResponse.refreshToken)
                tokenManager.saveUser(authResponse.user)
                emit(ApiResult.Success(authResponse.user))
            } else {
                emit(ApiResult.Error(response.message() ?: "Ошибка регистрации", response.code()))
            }
        } catch (e: Exception) {
            emit(ApiResult.Error(e.message ?: "Неизвестная ошибка"))
        }
    }
    
    suspend fun logout() {
        try {
            api.logout()
        } catch (_: Exception) {
            // Ignore logout errors
        }
        tokenManager.clearAll()
    }
    
    fun isLoggedIn(): Flow<Boolean> = tokenManager.isLoggedIn()
    
    fun getCurrentUser(): Flow<User?> = tokenManager.getUser()
    
    suspend fun getAccessToken(): String? = tokenManager.getAccessToken()
}
