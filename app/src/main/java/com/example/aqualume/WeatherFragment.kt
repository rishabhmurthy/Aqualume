package com.example.aqualume

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class WeatherFragment : Fragment(R.layout.fragment_weather) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val weatherOutput = view.findViewById<TextView>(R.id.weatherOutput)
        val reverseButton = view.findViewById<Button>(R.id.reverseGeocodeButton)

        reverseButton.setOnClickListener {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                getLocationAndReverseGeocode(weatherOutput)
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    1001
                )
            }
        }
    }

    private fun getLocationAndReverseGeocode(outputText: TextView) {
        val request = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 1000
            numUpdates = 1
        }

        fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location: Location? = result.lastLocation
                location?.let {
                    reverseGeocode(it.latitude, it.longitude, outputText)
                }
                // Remove location updates to prevent repeated callbacks
                fusedLocationClient.removeLocationUpdates(this)
            }
        }, null)
    }

    private fun reverseGeocode(lat: Double, lon: Double, outputText: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url =
                    URL("https://api.opencagedata.com/geocode/v1/json?q=$lat,$lon&key=90f04c444fac4cb0aafc78a5fa13ba9a")
                val connection = url.openConnection() as? HttpsURLConnection
                connection?.run {
                    if (responseCode == 200) {
                        inputStream.bufferedReader().use {
                            val json = Gson().fromJson(it, Map::class.java)
                            val results = (json["results"] as? List<*>) ?: emptyList<Any>()
                            val firstResult = results.firstOrNull() as? Map<*, *>
                            val components = firstResult?.get("components") as? Map<*, *>
                            val city = components?.get("city") ?: components?.get("town") ?: "Unknown"
                            val country = components?.get("country") ?: "Unknown"

                            withContext(Dispatchers.Main) {
                                outputText.text = "📍 $city, $country"
                                fetchWeather(lat, lon, outputText)
                            }
                        }
                    }
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e("Geocode", "Error in reverse geocode", e)
            }
        }
    }

    private fun fetchWeather(lat: Double, lon: Double, outputText: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url =
                    URL("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=66ef97e13d2afe8faf457114d04605a0")
                val connection = url.openConnection() as? HttpsURLConnection
                connection?.run {
                    if (responseCode == 200) {
                        inputStream.bufferedReader().use {
                            val json = Gson().fromJson(it, Map::class.java)
                            val main = json["main"] as? Map<*, *>
                            val temp = main?.get("temp")
                            val weatherList = json["weather"] as? List<*>
                            val weather = weatherList?.firstOrNull() as? Map<*, *>
                            val description = weather?.get("description")

                            withContext(Dispatchers.Main) {
                                outputText.append("\n🌡️ $temp°C\n☁️ $description")
                            }
                        }
                    }
                    disconnect()
                }
            } catch (e: Exception) {
                Log.e("Weather", "Error fetching weather", e)
            }
        }
    }
}
