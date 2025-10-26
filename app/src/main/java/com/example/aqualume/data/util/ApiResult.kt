package com.example.aqualume.data.util

sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Throwable) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success

    fun getOrNull(): T? = if (this is Success) data else null

    inline fun onSuccess(block: (T) -> Unit): ApiResult<T> {
        if (this is Success) block(data)
        return this
    }

    inline fun onError(block: (Throwable) -> Unit): ApiResult<T> {
        if (this is Error) block(exception)
        return this
    }

    inline fun onLoading(block: () -> Unit): ApiResult<T> {
        if (this is Loading) block()
        return this
    }

    inline fun <R> map(transform: (T) -> R): ApiResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(exception)
            is Loading -> Loading
        }
    }
}

suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: Exception) {
        ApiResult.Error(e)
    }
}