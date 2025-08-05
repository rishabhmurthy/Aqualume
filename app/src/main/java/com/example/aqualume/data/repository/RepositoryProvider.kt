package com.example.aqualume.data.repository

/**
 * Singleton class that provides repository instances
 */
object RepositoryProvider {
    /**
     * Provide an instance of the GeocodingRepository
     */
    val geocodingRepository: GeocodingRepository by lazy {
        GeocodingRepositoryImpl()
    }
    
    /**
     * Provide an instance of the WaterQualityRepository
     */
    val waterQualityRepository: WaterQualityRepository by lazy {
        WaterQualityRepositoryImpl()
    }
}