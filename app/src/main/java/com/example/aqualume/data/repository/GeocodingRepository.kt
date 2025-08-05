package com.example.aqualume.data.repository

import com.example.aqualume.data.model.GeocodingResponse
import com.example.aqualume.data.util.ApiResult

/**
 * Repository interface for geocoding operations
 */
interface GeocodingRepository {
    /**
     * Reverse geocode a latitude/longitude pair to get location information
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<GeocodingResponse>
}