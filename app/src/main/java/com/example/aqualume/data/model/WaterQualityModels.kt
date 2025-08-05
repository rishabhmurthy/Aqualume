package com.example.aqualume.data.model

import android.os.Build
import androidx.annotation.RequiresApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Data class representing a water quality monitoring station
 */
data class WaterQualityStation(
    val siteId: String,
    val siteName: String,
    val latitude: Double,
    val longitude: Double,
    val distanceToUser: Double = 0.0,
    val mostRecentActivityDate: String? = null
) {
    /**
     * Calculate the distance from this station to the given coordinates
     */
    fun distanceTo(lat: Double, lon: Double): Double {
        return calculateDistance(latitude, longitude, lat, lon)
    }

    /**
     * Check if this station's data is considered recent (less than 1 year old)
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun hasRecentData(): Boolean {
        if (mostRecentActivityDate == null) return false

        try {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            val activityDate = LocalDate.parse(mostRecentActivityDate.split("T")[0], formatter)
            val today = LocalDate.now()

            // Data is considered recent if less than 1 year old
            return ChronoUnit.YEARS.between(activityDate, today) < 1
        } catch (e: Exception) {
            return false
        }
    }

    companion object {
        /**
         * Calculate the distance between two geographic coordinates
         * Returns the approximate distance in degrees, which can be used for relative comparisons
         */
        fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = lat1 - lat2
            val dLon = (lon1 - lon2) * Math.cos(Math.toRadians(lat2))
            return Math.sqrt(dLat * dLat + dLon * dLon)
        }
    }
}

/**
 * Data class representing a water quality result
 */
data class WaterQualityResult(
    val characteristicName: String,
    val resultValue: Double,
    val resultUnit: String,
    val activityDate: String,
    val additionalData: Map<String, String> = emptyMap()
)

/**
 * Data class representing a parsed CSV row
 */
data class CsvRow(
    val data: Map<String, String>
) {
    operator fun get(key: String): String? = data[key]
    
    fun toWaterQualityResult(): WaterQualityResult? {
        val characteristicName = data["CharacteristicName"] ?: return null
        val resultValue = data["ResultMeasureValue"]?.toDoubleOrNull() ?: return null
        val resultUnit = data["ResultMeasure/MeasureUnitCode"] ?: "-"
        val activityEndDate = data["ActivityEndDate"]
        val activityStartDate = data["ActivityStartDate"]
        val date = activityEndDate?.takeIf { it.isNotBlank() } ?: activityStartDate?.takeIf { it.isNotBlank() } ?: return null
        
        return WaterQualityResult(
            characteristicName = characteristicName,
            resultValue = resultValue,
            resultUnit = resultUnit,
            activityDate = date,
            additionalData = data
        )
    }
}