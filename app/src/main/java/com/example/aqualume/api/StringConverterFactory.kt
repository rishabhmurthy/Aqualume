package com.example.aqualume.api

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * A Retrofit converter factory that handles String responses directly without any parsing.
 * This is useful for handling non-JSON responses like CSV data.
 */
class StringConverterFactory : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        return if (type == String::class.java) {
            Converter<ResponseBody, String> { value -> value.string() }
        } else {
            null
        }
    }

    override fun requestBodyConverter(
        type: Type,
        parameterAnnotations: Array<out Annotation>,
        methodAnnotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<*, RequestBody>? {
        return if (type == String::class.java) {
            Converter<String, RequestBody> { value ->
                value.toRequestBody("text/plain".toMediaTypeOrNull())
            }
        } else {
            null
        }
    }

    companion object {
        fun create(): StringConverterFactory = StringConverterFactory()
    }
}
