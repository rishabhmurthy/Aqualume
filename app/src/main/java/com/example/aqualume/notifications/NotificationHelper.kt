package com.example.aqualume.notifications

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.aqualume.R

object NotificationHelper {
    private const val CHANNEL_ID = "water_quality_alerts"
    private const val CHANNEL_NAME = "Water Quality Alerts"
    private const val CHANNEL_DESC = "Notifications for water quality threshold violations."

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    fun showNotification(context: Context, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                if (context is Activity) {
                    ActivityCompat.requestPermissions(
                        context,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        1001
                    )
                }
                return
            }
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun showCityWelcomeNotificationIfMoved(context: Context, currentLat: Double, currentLon: Double) {
        val prefs = context.getSharedPreferences("city_notify_prefs", Context.MODE_PRIVATE)
        val lastLat = prefs.getFloat("last_city_lat", Float.MAX_VALUE)
        val lastLon = prefs.getFloat("last_city_lon", Float.MAX_VALUE)
        val MIN_DISTANCE_MILES = 15.0

        if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
            val results = FloatArray(1)
            Location.distanceBetween(
                lastLat.toDouble(), lastLon.toDouble(), currentLat, currentLon, results
            )
            val distanceMiles = results[0] * 0.000621371
            if (distanceMiles < MIN_DISTANCE_MILES) return
        }

        var cityName = "your new area"
        try {
            val geocoder = Geocoder(context)
            val addresses = geocoder.getFromLocation(currentLat, currentLon, 1)
            if (!addresses.isNullOrEmpty()) {
                cityName = addresses[0].locality ?: cityName
            }
        } catch (e: Exception) {
        }

        showNotification(
            context,
            "Welcome to $cityName!",
            "You've moved to a new city. Check the app for updated water quality data."
        )
        prefs.edit()
            .putFloat("last_city_lat", currentLat.toFloat())
            .putFloat("last_city_lon", currentLon.toFloat())
            .apply()
    }
}
