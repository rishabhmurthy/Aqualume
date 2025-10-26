package com.example.aqualume.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.aqualume.R
import com.google.android.gms.location.*

class MovementNotificationService : Service() {
    private val CHANNEL_ID = "movement_notification_channel"
    private val NOTIFICATION_ID = 3001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private val MIN_DISTANCE_MILES = 15.0
    private val prefsName = "city_notify_prefs"
    private val latKey = "last_city_lat"
    private val lonKey = "last_city_lon"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        loadLastLocation()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    checkAndNotify(location)
                }
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REQUEST_LOCATION && hasLocation) {
            Log.d(CHANNEL_ID, "Received request for location, broadcasting current location")
            val responseIntent = Intent(ACTION_LOCATION_RESPONSE).apply {
                putExtra(EXTRA_LATITUDE, lastKnownLatitude)
                putExtra(EXTRA_LONGITUDE, lastKnownLongitude)
            }
            sendBroadcast(responseIntent)
        }

        startForeground(NOTIFICATION_ID, getNotification(""))
        startLocationUpdates()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(5000)
            .build()
        fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        const val ACTION_LOCATION_CHANGED = "com.example.aqualume.ACTION_LOCATION_CHANGED"
        const val EXTRA_LATITUDE = "extra_latitude"
        const val EXTRA_LONGITUDE = "extra_longitude"
        const val EXTRA_DISTANCE_MILES = "extra_distance_miles"
        const val ACTION_REQUEST_LOCATION = "com.example.aqualume.ACTION_REQUEST_LOCATION"
        const val ACTION_LOCATION_RESPONSE = "com.example.aqualume.ACTION_LOCATION_RESPONSE"
        private var lastKnownLatitude: Double = 0.0
        private var lastKnownLongitude: Double = 0.0
        private var hasLocation: Boolean = false
        fun getLastKnownLocation(): Pair<Double, Double>? {
            return if (hasLocation) {
                Pair(lastKnownLatitude, lastKnownLongitude)
            } else null
        }
    }

    private fun checkAndNotify(location: Location) {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val lastLat = prefs.getFloat(latKey, Float.MAX_VALUE)
        val lastLon = prefs.getFloat(lonKey, Float.MAX_VALUE)
        val currentLat = location.latitude
        val currentLon = location.longitude

        lastKnownLatitude = currentLat
        lastKnownLongitude = currentLon
        hasLocation = true

        var shouldNotify = false
        var distanceMiles = 0.0
        if (lastLat != Float.MAX_VALUE && lastLon != Float.MAX_VALUE) {
            val results = FloatArray(1)
            Location.distanceBetween(lastLat.toDouble(), lastLon.toDouble(), currentLat, currentLon, results)
            distanceMiles = results[0] * 0.000621371
            Log.d(CHANNEL_ID, "Calculated distance: $distanceMiles miles from ($lastLat, $lastLon) to ($currentLat, $currentLon)")
            if (distanceMiles >= MIN_DISTANCE_MILES) {
                shouldNotify = true
            }
        } else {
            Log.d(CHANNEL_ID, "First location save: ($currentLat, $currentLon)")
            prefs.edit().putFloat(latKey, currentLat.toFloat()).putFloat(lonKey, currentLon.toFloat()).apply()
        }
        if (shouldNotify) {
            Log.d(CHANNEL_ID, "Should notify: User moved $distanceMiles miles from last location.")
            var cityName = "your new area"
            try {
                val geocoder = Geocoder(this)
                val addresses = geocoder.getFromLocation(currentLat, currentLon, 1)
                Log.d(CHANNEL_ID, "Geocoder returned: $addresses")
                if (!addresses.isNullOrEmpty()) {
                    cityName = addresses[0].locality ?: cityName
                    Log.d(CHANNEL_ID, "Resolved city name: $cityName")
                }
            } catch (e: Exception) {
                Log.e(CHANNEL_ID, "Geocoder error: ${e.message}")
            }
            showNotification("Welcome to $cityName!", "You've moved to a new city. Check the app for updated water quality data.")
            Log.d(CHANNEL_ID, "Notification shown for city: $cityName at lat: $currentLat, lon: $currentLon")
            prefs.edit().putFloat(latKey, currentLat.toFloat()).putFloat(lonKey, currentLon.toFloat()).apply()

            broadcastLocationChange(currentLat, currentLon, distanceMiles)
        } else {
            Log.d(CHANNEL_ID, "No notification: User moved $distanceMiles miles, threshold is $MIN_DISTANCE_MILES miles.")
        }
    }

    private fun broadcastLocationChange(latitude: Double, longitude: Double, distanceMiles: Double) {
        val intent = Intent(ACTION_LOCATION_CHANGED).apply {
            putExtra(EXTRA_LATITUDE, latitude)
            putExtra(EXTRA_LONGITUDE, longitude)
            putExtra(EXTRA_DISTANCE_MILES, distanceMiles)
        }
        Log.d(CHANNEL_ID, "Broadcasting location change: lat=$latitude, lon=$longitude, distance=$distanceMiles")
        sendBroadcast(intent)
    }

    private fun loadLastLocation() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        lastLat = prefs.getFloat(latKey, Float.MAX_VALUE).takeIf { it != Float.MAX_VALUE }?.toDouble()
        lastLon = prefs.getFloat(lonKey, Float.MAX_VALUE).takeIf { it != Float.MAX_VALUE }?.toDouble()
    }

    private fun getNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("")
            .setContentText("")
            .setSmallIcon(R.mipmap.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun showNotification(title: String, message: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)

        val notificationId = System.currentTimeMillis().toInt()
        Log.d(CHANNEL_ID, "Showing notification with ID: $notificationId")

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movement Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.description = "Notifications when you move to a new location"
            channel.enableLights(true)
            channel.enableVibration(true)
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Log.d(CHANNEL_ID, "Created notification channel with HIGH importance")
        }
    }
}
