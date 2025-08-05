package com.example.aqualume.data.repository

import com.example.aqualume.data.model.WaterQualityStation
import com.example.aqualume.data.model.WaterQualityResult
import com.example.aqualume.data.util.ApiResult

/**
 * Repository interface for water quality data operations
 */
interface WaterQualityRepository {
    /**
     * Get water quality stations near the specified location
     */
    suspend fun getNearbyStations(
        latitude: Double, 
        longitude: Double, 
        radiusMiles: Int = 25
    ): ApiResult<List<WaterQualityStation>>
    
    /**
     * Get water quality results for a specific station
     */
    suspend fun getWaterQualityResults(stationId: String): ApiResult<List<WaterQualityResult>>
    
    /**
     * Get the first station with valid results from a list of stations
     */
    suspend fun getFirstStationWithResults(
        stations: List<WaterQualityStation>
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>>

    /**
     * Get the first station with results within the specified radius
     * If no results are found within the specified radius, returns the closest station with data
     */
    suspend fun getFirstStationWithRecentResults(
        stations: List<WaterQualityStation>,
        maxRadiusMiles: Double = 10.0
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>>
}