package com.example.aqualume

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aqualume.data.repository.RepositoryProvider
import com.example.aqualume.data.util.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WaterQualityAlertWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val waterQualityRepository = RepositoryProvider.waterQualityRepository

    // Default threshold for demonstration (e.g., Nitrate > 10 mg/L)
    private val thresholds = mapOf(
        "Nitrate" to 10.0,
        "Lead" to 0.015,
        "Arsenic" to 0.01
    )

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // For demo: use last known location from shared prefs or skip
        val prefs = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return@withContext Result.success()

        val stationsResult = waterQualityRepository.getNearbyStations(lat, lon)
        if (stationsResult is ApiResult.Success) {
            val stations = stationsResult.data
            val stationWithResultsResult = waterQualityRepository.getFirstStationWithResults(stations)
            if (stationWithResultsResult is ApiResult.Success) {
                val (_, results) = stationWithResultsResult.data
                for (result in results) {
                    val threshold = thresholds[result.characteristicName]
                    val value = result.resultValue?.toString()?.toDoubleOrNull()
                    if (threshold != null && value != null && value > threshold) {
                        NotificationHelper.showNotification(
                            applicationContext,
                            "Water Quality Alert",
                            "${result.characteristicName} is ${result.resultValue} ${result.resultUnit} (limit: $threshold)"
                        )
                    }
                }
            }
        }
        Result.success()
    }
}