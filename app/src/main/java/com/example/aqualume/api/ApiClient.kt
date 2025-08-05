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

    // API key for OpenCage Geocoding API
    // TODO: Move this to BuildConfig or local.properties for security
    const val GEOCODING_API_KEY = "90f04c444fac4cb0aafc78a5fa13ba9a"

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

    /**
     * Create a Gson instance with custom configuration
     */
    private fun createGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    /**
     * Create a Retrofit instance for the Geocoding API
     */
    private val geocodingRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(GEOCODING_BASE_URL)
            .client(createOkHttpClient())
            .addConverterFactory(GsonConverterFactory.create(createGson()))
            .build()
    }

    /**
     * Create a Retrofit instance for the Water Quality API
     * Note: We don't set a base URL here because we'll provide full URLs in the API calls
     */
    private val waterQualityRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.waterqualitydata.us/") // Base URL is required but will be overridden
            .client(createOkHttpClient())
            .addConverterFactory(StringConverterFactory.create()) // Use StringConverterFactory for CSV responses
            .build()
    }

    /**
     * Provide an instance of the Geocoding API service
     */
    val geocodingApiService: GeocodingApiService by lazy {
        geocodingRetrofit.create(GeocodingApiService::class.java)
    }

    /**
     * Provide an instance of the Water Quality API service
     */
    val waterQualityApiService: WaterQualityApiService by lazy {
        waterQualityRetrofit.create(WaterQualityApiService::class.java)
    }
}
