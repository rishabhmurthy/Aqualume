package com.example.aqualume.data.repository

import com.example.aqualume.api.ApiClient
import com.example.aqualume.data.model.GeocodingResponse
import com.example.aqualume.data.util.ApiResult
import com.example.aqualume.data.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of the GeocodingRepository interface
 */
class GeocodingRepositoryImpl : GeocodingRepository {
    private val apiService = ApiClient.geocodingApiService
    
    /**
     * Reverse geocode a latitude/longitude pair to get location information
     */
    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<GeocodingResponse> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val response = apiService.reverseGeocode(
                    latLng = "$latitude,$longitude",
                    apiKey = ApiClient.GEOCODING_API_KEY
                )
                
                if (response.isSuccessful) {
                    response.body() ?: throw Exception("Empty response body")
                } else {
                    throw Exception("Error: ${response.code()} - ${response.message()}")
                }
            }
        }
    }
}