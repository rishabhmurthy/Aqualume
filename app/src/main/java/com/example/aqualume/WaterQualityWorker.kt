package com.example.aqualume

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aqualume.data.repository.RepositoryProvider
import com.example.aqualume.data.util.ApiResult

class WaterQualityWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        // Fetch last known location from SharedPreferences
        val prefs = applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val lat = prefs.getFloat("last_lat", 0f).toDouble()
        val lon = prefs.getFloat("last_lon", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return Result.success() // No location stored

        val waterQualityRepository = RepositoryProvider.waterQualityRepository
        val stationsResult = waterQualityRepository.getNearbyStations(lat, lon)
        if (stationsResult is ApiResult.Success) {
            val stationWithResultsResult = waterQualityRepository.getFirstStationWithRecentResults(stationsResult.data, 10.0)
            if (stationWithResultsResult is ApiResult.Success) {
                val (station, results) = stationWithResultsResult.data
                val notificationText = if (results.isNotEmpty()) {
                    val first = results.first()
                    "${first.characteristicName}: ${first.resultValue} ${first.resultUnit} at ${station.siteName}"
                } else {
                    "No recent water quality results near you."
                }
                sendNotification(notificationText)
            }
        }
        return Result.success()
    }

    private fun sendNotification(text: String) {
        val channelId = "water_quality_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Water Quality Alerts"
            val descriptionText = "Notifications about water quality near you."
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        val builder = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Water Quality Update")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        with(NotificationManagerCompat.from(applicationContext)) {
            notify(1001, builder.build())
        }
    }
}

