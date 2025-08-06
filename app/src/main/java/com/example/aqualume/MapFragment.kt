package com.example.aqualume

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.*
import com.mapbox.geojson.Point
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.circleLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min

class MapFragment : Fragment(), CoroutineScope by MainScope() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var mapView: MapView? = null
    private val SOURCE_ID = "api-source"
    private val LAYER_ID = "circle-layer"
    // Add limit parameter to reduce data size (e.g., limit to 100 locations)
    private val API_URL = "https://www.waterqualitydata.us/data/summary/monitoringLocation/search?countryCode=US&dataProfile=summaryMonitoringLocation&mimeType=geojson&limit=100"

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                startLocationUpdates()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupLocation()
        setupMap(view)
    }

    private fun setupLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = location
                }
            }
        }

        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }

    private fun setupMap(view: View) {
        mapView = view.findViewById(R.id.mapView)

        // Use the new style loading API
        mapView?.mapboxMap?.loadStyle(
            Style.MAPBOX_STREETS
        ) {
            // Configure location component after style is loaded
            configureLocationComponent()

            // Fetch data and add circle layer
            fetchDataAndAddCircleLayer()
        }
    }

    private fun configureLocationComponent() {
        mapView?.location?.apply {
            enabled = true
            pulsingEnabled = true
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ActivityCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        if (hasLocationPermission()) {
            try {
                fusedLocationClient.requestLocationUpdates(
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                        .setWaitForAccurateLocation(false)
                        .setMinUpdateIntervalMillis(5000)
                        .build(),
                    locationCallback,
                    requireActivity().mainLooper
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    private fun fetchDataAndAddCircleLayer() {
        launch {
            try {
                val points = fetchDataFromApi()
                addCircleLayer(points)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun fetchDataFromApi(): List<Point> = withContext(Dispatchers.IO) {
        val points = mutableListOf<Point>()
        var connection: HttpURLConnection? = null
        var inputStream: InputStream? = null

        try {
            val url = URL(API_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Process the data in a more memory-efficient way
                inputStream = connection.inputStream

                // Create a JsonReader for streaming
                val reader = InputStreamReader(inputStream, "UTF-8")

                // We'll use a limited buffer to read the JSON structure
                // Then process features one at a time
                val jsonObject = JSONTokener(reader.readText()).nextValue() as JSONObject

                // Only process if we have features
                if (jsonObject.has("features")) {
                    val features = jsonObject.getJSONArray("features")

                    // Limit the number of points we process to avoid memory issues
                    val maxPoints = min(features.length(), 200)
                    Log.d("MapFragment", "Processing $maxPoints points from API")

                    for (i in 0 until maxPoints) {
                        try {
                            val feature = features.getJSONObject(i)
                            val geometry = feature.getJSONObject("geometry")
                            val coordinates = geometry.getJSONArray("coordinates")

                            // Assuming coordinates are in [longitude, latitude] format
                            val longitude = coordinates.getDouble(0)
                            val latitude = coordinates.getDouble(1)

                            points.add(Point.fromLngLat(longitude, latitude))
                        } catch (e: Exception) {
                            // Skip this point if there's an issue
                            Log.e("MapFragment", "Error processing point at index $i", e)
                        }
                    }
                }
            } else {
                Log.e("MapFragment", "HTTP error code: $responseCode")
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error fetching data from API", e)
        } finally {
            // Always close connections and streams
            try {
                inputStream?.close()
                connection?.disconnect()
            } catch (e: Exception) {
                Log.e("MapFragment", "Error closing resources", e)
            }
        }

        Log.d("MapFragment", "Fetched ${points.size} points successfully")
        points
    }

    private fun addCircleLayer(points: List<Point>) {
        if (points.isEmpty()) return

        // Create a GeoJSON source with the points
        val geoJsonSource = geoJsonSource(SOURCE_ID) {
            featureCollection(FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(it) }))
        }

        // Create a circle layer that uses the source
        val circleLayer = circleLayer(LAYER_ID, SOURCE_ID) {
            circleColor("#3bb2d0") // Circle color
            circleRadius(8.0) // Circle size
            circleOpacity(0.8) // Circle opacity
        }

        // Add the source and layer to the map style using the non-deprecated style property
        mapView?.mapboxMap?.style?.let { style ->
            style.addSource(geoJsonSource)
            style.addLayer(circleLayer)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView = null
        cancel()
    }
}
