package com.example.aqualume.data.util

/**
 * A generic class that holds a value or an error status
 */
sealed class ApiResult<out T> {
    data class Success<out T>(val data: T) : ApiResult<T>()
    data class Error(val exception: Throwable) : ApiResult<Nothing>()
    object Loading : ApiResult<Nothing>()
    
    /**
     * Returns true if this is a Success
     */
    val isSuccess: Boolean get() = this is Success
    
    /**
     * Returns the data if this is a Success, otherwise null
     */
    fun getOrNull(): T? = if (this is Success) data else null
    
    /**
     * Executes the given block if this is a Success
     */
    inline fun onSuccess(block: (T) -> Unit): ApiResult<T> {
        if (this is Success) block(data)
        return this
    }
    
    /**
     * Executes the given block if this is an Error
     */
    inline fun onError(block: (Throwable) -> Unit): ApiResult<T> {
        if (this is Error) block(exception)
        return this
    }
    
    /**
     * Executes the given block if this is Loading
     */
    inline fun onLoading(block: () -> Unit): ApiResult<T> {
        if (this is Loading) block()
        return this
    }
    
    /**
     * Maps the success value to a new value
     */
    inline fun <R> map(transform: (T) -> R): ApiResult<R> {
        return when (this) {
            is Success -> Success(transform(data))
            is Error -> Error(exception)
            is Loading -> Loading
        }
    }
}

/**
 * Utility function to wrap API calls and handle exceptions
 */
suspend fun <T> safeApiCall(apiCall: suspend () -> T): ApiResult<T> {
    return try {
        ApiResult.Success(apiCall())
    } catch (e: Exception) {
        ApiResult.Error(e)
    }
}