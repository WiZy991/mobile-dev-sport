package com.fitnessclub.app.data.api

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int? = null) : ApiResult<Nothing>()
    data object Loading : ApiResult<Nothing>()
    
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading
    
    fun getOrNull(): T? = (this as? Success)?.data
    
    fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> Loading
    }
    
    suspend fun <R> flatMap(transform: suspend (T) -> ApiResult<R>): ApiResult<R> = when (this) {
        is Success -> transform(data)
        is Error -> this
        is Loading -> Loading
    }
    
    companion object {
        fun <T> success(data: T): ApiResult<T> = Success(data)
        fun error(message: String, code: Int? = null): ApiResult<Nothing> = Error(message, code)
        fun loading(): ApiResult<Nothing> = Loading
    }
}

inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(data)
    return this
}

inline fun <T> ApiResult<T>.onError(action: (String, Int?) -> Unit): ApiResult<T> {
    if (this is ApiResult.Error) action(message, code)
    return this
}

inline fun <T> ApiResult<T>.onLoading(action: () -> Unit): ApiResult<T> {
    if (this is ApiResult.Loading) action()
    return this
}
