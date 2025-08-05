package com.example.aqualume.api

import com.example.aqualume.data.model.GeocodingResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Interface for the OpenCage Geocoding API
 */
interface GeocodingApiService {
    @GET("geocode/v1/json")
    suspend fun reverseGeocode(
        @Query("q") latLng: String,
        @Query("key") apiKey: String
    ): Response<GeocodingResponse>
}

/**
 * Interface for the Water Quality Data API
 * Note: Since the API returns CSV data, we use String as the response type
 * and will parse it manually in the repository layer
 */
interface WaterQualityApiService {
    @GET
    suspend fun getStations(@Url url: String): Response<String>

    @GET
    suspend fun getWaterQualityResults(@Url url: String): Response<String>
}
