package com.example.aqualume.data.repository

import com.example.aqualume.data.model.GeocodingResponse
import com.example.aqualume.data.util.ApiResult

interface GeocodingRepository {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<GeocodingResponse>
}