package com.example.aqualume.data.repository

import com.example.aqualume.data.model.WaterQualityStation
import com.example.aqualume.data.model.WaterQualityResult
import com.example.aqualume.data.util.ApiResult

interface WaterQualityRepository {
    suspend fun getNearbyStations(
        latitude: Double, 
        longitude: Double, 
        radiusMiles: Int = 25
    ): ApiResult<List<WaterQualityStation>>
    
    suspend fun getWaterQualityResults(stationId: String): ApiResult<List<WaterQualityResult>>
    
    suspend fun getFirstStationWithResults(
        stations: List<WaterQualityStation>
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>>

    suspend fun getFirstStationWithRecentResults(
        stations: List<WaterQualityStation>,
        maxRadiusMiles: Double = 10.0
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>>

    suspend fun getStationById(stationId: String): ApiResult<WaterQualityStation>

    suspend fun getResultsByStationId(stationId: String): ApiResult<List<WaterQualityResult>>
}