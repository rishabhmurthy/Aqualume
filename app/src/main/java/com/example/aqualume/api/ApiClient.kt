package com.example.aqualume.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val GEOCODING_BASE_URL = "https://api.opencagedata.com/"
    private const val TIMEOUT_SECONDS = 30L

    private fun createOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private fun createGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    private val geocodingRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GEOCODING_BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(createGson()))
            .build()
    }

    private val waterQualityRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.waterqualitydata.us/")
            .client(createOkHttpClient())
            .addConverterFactory(StringConverterFactory.create())
            .build()
    }

    val geocodingApiService: GeocodingApiService by lazy {
        geocodingRetrofit.create(GeocodingApiService::class.java)
    }

    val waterQualityApiService: WaterQualityApiService by lazy {
        waterQualityRetrofit.create(WaterQualityApiService::class.java)
    }
}
