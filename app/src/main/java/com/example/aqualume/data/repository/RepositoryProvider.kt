package com.example.aqualume.data.repository

object RepositoryProvider {

    val geocodingRepository: GeocodingRepository by lazy {
        GeocodingRepositoryImpl()
    }

    val waterQualityRepository: WaterQualityRepository by lazy {
        WaterQualityRepositoryImpl()
    }
}