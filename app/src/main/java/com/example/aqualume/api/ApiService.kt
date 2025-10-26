package com.example.aqualume.api

import com.example.aqualume.data.model.GeocodingResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface GeocodingApiService {
    @GET("geocode/v1/json")
    suspend fun reverseGeocode(
        @Query("q") latLng: String,
        @Query("key") apiKey: String
    ): Response<GeocodingResponse>
}

interface WaterQualityApiService {
    @GET
    suspend fun getStations(@Url url: String): Response<String>

    @GET
    suspend fun getWaterQualityResults(@Url url: String): Response<String>
}
