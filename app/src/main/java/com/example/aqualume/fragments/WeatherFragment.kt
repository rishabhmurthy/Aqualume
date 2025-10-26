package com.example.aqualume.fragments

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
import com.example.aqualume.R
import com.example.aqualume.BuildConfig
import com.google.android.gms.location.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.collections.get

class WeatherFragment : Fragment(R.layout.fragment_weather) {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val weatherOutput = view.findViewById<TextView>(R.id.weatherOutput)
        val reverseButton = view.findViewById<Button>(R.id.reverseGeocodeButton)
        val temperatureText = view.findViewById<TextView>(R.id.temperatureText)
        val statusText = view.findViewById<TextView>(R.id.statusText)

        fetchWeatherAndLocation(weatherOutput, temperatureText, statusText)

        reverseButton.setOnClickListener {
            fetchWeatherAndLocation(weatherOutput, temperatureText, statusText)
        }
    }

    private fun fetchWeatherAndLocation(weatherOutput: TextView, temperatureText: TextView, statusText: TextView) {
        statusText.setText(R.string.fetching_location)
        weatherOutput.setText(R.string.weather_info_placeholder)
        temperatureText.text = "--°F"
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            getLocationAndReverseGeocode(weatherOutput, temperatureText, statusText)
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1001
            )
        }
    }

    private fun getLocationAndReverseGeocode(outputText: TextView, temperatureText: TextView, statusText: TextView) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
            .setWaitForAccurateLocation(false)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location: Location? = result.lastLocation
                    location?.let {
                        reverseGeocode(it.latitude, it.longitude, outputText, temperatureText, statusText)
                    }
                    fusedLocationClient.removeLocationUpdates(this)
                }
            }, null)
        } catch (e: SecurityException) {
            Log.e("WeatherFragment", "Location permission error: ${e.message}")
        }
    }

    private fun reverseGeocode(lat: Double, lon: Double, outputText: TextView, temperatureText: TextView, statusText: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url =
                    URL("https://api.opencagedata.com/geocode/v1/json?q=$lat,$lon&key=${BuildConfig.GEOCODING_API_KEY}")
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
                                statusText.text = getString(R.string.weather_location, city, country)
                                fetchWeather(lat, lon, outputText, temperatureText)
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

    private fun fetchWeather(lat: Double, lon: Double, outputText: TextView, temperatureText: TextView) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url =
                    URL("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=${BuildConfig.OPENWEATHER_API_KEY}")
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
                                if (temp is Number) {
                                    val fahrenheit = temp.toFloat() * 9 / 5 + 32
                                    val tempText = if (fahrenheit % 1 == 0f) "${fahrenheit.toInt()}°F" else "${"%.1f".format(fahrenheit)}°F"
                                    temperatureText.text = tempText
                                }
                                outputText.append("\n☁️ $description")
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
