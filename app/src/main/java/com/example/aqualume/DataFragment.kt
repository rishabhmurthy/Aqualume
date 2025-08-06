package com.example.aqualume

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aqualume.data.model.WaterQualityResult
import com.example.aqualume.data.model.WaterQualityStation
import com.example.aqualume.data.repository.RepositoryProvider
import com.example.aqualume.data.util.ApiResult
import com.google.android.gms.location.*
import kotlinx.coroutines.launch
import java.util.Locale

class DataFragment : Fragment() {
    private val TAG = "DataFragment"

    companion object {
        // Cache for water quality data
        private var cachedStation: WaterQualityStation? = null
        private var cachedResults: List<WaterQualityResult>? = null
        private var lastFetchedLatitude: Double = 0.0
        private var lastFetchedLongitude: Double = 0.0
        private var hasCachedData: Boolean = false

        // Distance threshold for auto-refresh (15 miles in degrees, approx 15/69 = ~0.217)
        private const val DISTANCE_THRESHOLD_DEGREES = 0.217
    }

    // Repository for water quality data
    private val waterQualityRepository = RepositoryProvider.waterQualityRepository

    // Location client
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // UI components
    private lateinit var tvStationName: TextView
    private lateinit var tvStationId: TextView
    private lateinit var tvStationDistance: TextView
    private lateinit var tvLastUpdated: TextView
    private lateinit var cardStation: CardView
    private lateinit var rvWaterQuality: RecyclerView
    private lateinit var progressBar: View
    private lateinit var tvError: TextView
    private lateinit var tvQualityTitle: TextView
    private lateinit var btnRefresh: View

    // Adapter for water quality results
    private lateinit var waterQualityAdapter: WaterQualityAdapter

    // Location updates related fields
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var isReceivingLocationUpdates = false
    private var currentLocation: Location? = null

    // Location permission request
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
                // Permission granted, get current location
                getCurrentLocation()
            }
            else -> {
                // Permission denied, show error message
                showError("Location permission denied. Cannot fetch nearby water stations.")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize UI components
        tvStationName = view.findViewById(R.id.tvStationName)
        tvStationId = view.findViewById(R.id.tvStationId)
        tvStationDistance = view.findViewById(R.id.tvStationDistance)
        tvLastUpdated = view.findViewById(R.id.tvLastUpdated)
        cardStation = view.findViewById(R.id.cardStation)
        rvWaterQuality = view.findViewById(R.id.rvWaterQuality)
        progressBar = view.findViewById(R.id.progressBar)
        tvError = view.findViewById(R.id.tvError)
        tvQualityTitle = view.findViewById(R.id.tvQualityTitle)
        btnRefresh = view.findViewById(R.id.btnRefresh)

        // Initialize RecyclerView and adapter
        setupRecyclerView()

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())
        setupLocationUpdates()

        // Check location permissions and fetch data
        checkLocationPermissionAndFetchData()

        // Set up refresh button
        btnRefresh.setOnClickListener {
            refreshData(true) // Force refresh
        }
    }

    private fun setupRecyclerView() {
        waterQualityAdapter = WaterQualityAdapter()
        rvWaterQuality.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = waterQualityAdapter
        }
    }

    private fun checkLocationPermissionAndFetchData() {
        // First check if we already have cached data
        if (hasCachedData) {
            cachedStation?.let { station ->
                cachedResults?.let { results ->
                    displayStationInfo(station)
                    displayQualityResults(results)
                    hideLoading()
                    return
                }
            }
        }

        // No cached data, need to get location and fetch new data
        if (hasLocationPermission()) {
            getCurrentLocation()
        } else {
            requestLocationPermission()
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

    private fun getCurrentLocation() {
        showLoading()

        if (hasLocationPermission()) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            currentLocation = location
                            fetchNearbyStations(location.latitude, location.longitude)
                        } else {
                            showError("Could not get current location.")
                        }
                    }
                    .addOnFailureListener { e ->
                        showError("Error getting location: ${e.message}")
                    }
            } catch (e: SecurityException) {
                showError("Location permission error: ${e.message}")
            }
        } else {
            showError("Location permission not granted")
        }
    }

    private fun fetchNearbyStations(latitude: Double, longitude: Double) {
        // Check if we have cached data for the current location
        if (hasCachedData &&
            lastFetchedLatitude == latitude &&
            lastFetchedLongitude == longitude) {

            // Use cached data
            cachedStation?.let { station ->
                cachedResults?.let { results ->
                    displayStationInfo(station)
                    displayQualityResults(results)
                    hideLoading()
                    return
                }
            }
        }

        // Fetch new data
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = waterQualityRepository.getNearbyStations(latitude, longitude)) {
                is ApiResult.Success -> {
                    val stations = result.data
                    if (stations.isNotEmpty()) {
                        // Use the repository's method to find the first station with results
                        when (val stationResult = waterQualityRepository.getFirstStationWithResults(stations)) {
                            is ApiResult.Success -> {
                                val (station, qualityResults) = stationResult.data
                                displayStationInfo(station)
                                displayQualityResults(qualityResults)

                                // Cache the fetched data
                                cachedStation = station
                                cachedResults = qualityResults
                                lastFetchedLatitude = latitude
                                lastFetchedLongitude = longitude
                                hasCachedData = true

                                hideLoading()
                            }
                            is ApiResult.Error -> {
                                showError("No water quality results found for any nearby stations: ${stationResult.exception.message}")
                            }
                            is ApiResult.Loading -> {
                                // Show loading state (progress already visible)
                            }
                        }
                    } else {
                        showError("No water quality stations found nearby.")
                    }
                }
                is ApiResult.Error -> {
                    showError("Error fetching nearby stations: ${result.exception.message}")
                }
                is ApiResult.Loading -> {
                    // Show loading state (progress already visible)
                }
            }
        }
    }

    private fun displayStationInfo(station: WaterQualityStation) {
        tvStationName.text = station.siteName

        // Get city location instead of showing station ID
        viewLifecycleOwner.lifecycleScope.launch {
            val cityText = try {
                when (val result = RepositoryProvider.geocodingRepository.reverseGeocode(
                    station.latitude,
                    station.longitude
                )) {
                    is ApiResult.Success -> {
                        val response = result.data
                        if (response.results.isNotEmpty()) {
                            val components = response.results[0].components
                            if (components.city != null && components.state != null) {
                                "Location: ${components.city}, ${components.state}"
                            } else if (components.city != null) {
                                "Location: ${components.city}, ${components.country ?: ""}"
                            } else if (components.state != null) {
                                "Location: ${components.state}, ${components.country ?: ""}"
                            } else {
                                "Location: ${components.country ?: "Unknown"}"
                            }
                        } else {
                            "Location: Unknown"
                        }
                    }
                    else -> "Location: Unknown"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting city location: ${e.message}")
                "Location: Unknown"
            }
            tvStationId.text = cityText
        }

        // Format distance
        val distanceInMiles = station.distanceToUser * 69.0 // Approximate conversion from degrees to miles
        val formattedDistance = String.format(Locale.US, "%.1f", distanceInMiles)
        tvStationDistance.text = "Distance: $formattedDistance miles away"

        // Display last updated date
        tvLastUpdated.text = if (station.mostRecentActivityDate != null) {
            "Last Updated: ${formatDate(station.mostRecentActivityDate)}"
        } else {
            "Last Updated: Unknown"
        }

        cardStation.visibility = View.VISIBLE
        tvQualityTitle.visibility = View.VISIBLE
    }

    private fun displayQualityResults(results: List<WaterQualityResult>) {
        waterQualityAdapter.submitList(results)
        rvWaterQuality.visibility = View.VISIBLE
    }

    private fun formatDate(dateString: String): String {
        try {
            if (dateString.contains("T")) {
                val parts = dateString.split("T")
                return parts[0]
            }
            return dateString
        } catch (_: Exception) {
            return dateString
        }
    }

    private fun showLoading() {
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        cardStation.visibility = View.GONE
        rvWaterQuality.visibility = View.GONE
        tvQualityTitle.visibility = View.GONE
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
    }

    private fun showError(message: String) {
        progressBar.visibility = View.GONE
        tvError.visibility = View.VISIBLE
        tvError.text = message
        cardStation.visibility = View.GONE
        rvWaterQuality.visibility = View.GONE
        tvQualityTitle.visibility = View.GONE

        Log.e(TAG, message)
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 5 * 60 * 1000) // Update every 5 minutes
            .setMinUpdateIntervalMillis(2 * 60 * 1000) // Minimum 2 minutes between updates
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    Log.d(TAG, "Received location update: ${location.latitude}, ${location.longitude}")

                    // Check if we need to refresh based on distance
                    if (shouldRefreshForLocation(location)) {
                        currentLocation = location
                        refreshData(false) // Not forced, only if distance > threshold
                    } else {
                        currentLocation = location
                    }
                }
            }
        }
    }

    private fun shouldRefreshForLocation(newLocation: Location): Boolean {
        if (!hasCachedData) return true

        // Calculate distance from last fetched location to new location
        val distance = calculateDistance(
            lastFetchedLatitude,
            lastFetchedLongitude,
            newLocation.latitude,
            newLocation.longitude
        )

        Log.d(TAG, "Distance from last fetched location: $distance miles")

        // Return true if the distance is greater than 15 miles
        return distance >= 15.0
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isReceivingLocationUpdates = true
            Log.d(TAG, "Started location updates")
        } catch (e: SecurityException) {
            Log.e(TAG, "Error starting location updates", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isReceivingLocationUpdates = false
        Log.d(TAG, "Stopped location updates")
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission() && !isReceivingLocationUpdates) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isReceivingLocationUpdates) {
            stopLocationUpdates()
        }
    }

    private fun refreshData(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            // Forced refresh, always get current location and fetch data
            getCurrentLocation()
            return
        }

        // Check if we have a current location
        currentLocation?.let { location ->
            // Check if we need to refresh based on distance
            if (hasCachedData) {
                val distance = calculateDistance(
                    lastFetchedLatitude,
                    lastFetchedLongitude,
                    location.latitude,
                    location.longitude
                )

                if (distance >= 15.0) {
                    // User has moved more than 15 miles, refresh data
                    Log.d(TAG, "User moved ${distance.toInt()} miles, refreshing data")
                    fetchNearbyStations(location.latitude, location.longitude)
                } else {
                    // Use cached data
                    Log.d(TAG, "User only moved ${distance.toInt()} miles, using cached data")
                    cachedStation?.let { station ->
                        cachedResults?.let { results ->
                            displayStationInfo(station)
                            displayQualityResults(results)
                        }
                    }
                }
            } else {
                // No cached data, fetch new data
                fetchNearbyStations(location.latitude, location.longitude)
            }
        } ?: run {
            // No current location, get it
            getCurrentLocation()
        }
    }

    private fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val earthRadius = 3958.8 // Earth radius in miles

        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)

        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    /**
     * RecyclerView adapter for water quality results
     */
    inner class WaterQualityAdapter : RecyclerView.Adapter<WaterQualityAdapter.ViewHolder>() {
        private var items = listOf<WaterQualityResult>()

        fun submitList(newItems: List<WaterQualityResult>) {
            val oldItems = items
            items = newItems

            // Use more specific change notifications instead of notifyDataSetChanged
            if (oldItems.isEmpty()) {
                notifyItemRangeInserted(0, newItems.size)
            } else if (newItems.isEmpty()) {
                notifyItemRangeRemoved(0, oldItems.size)
            } else {
                notifyItemRangeChanged(0, minOf(oldItems.size, newItems.size))
                if (newItems.size > oldItems.size) {
                    notifyItemRangeInserted(oldItems.size, newItems.size - oldItems.size)
                } else if (oldItems.size > newItems.size) {
                    notifyItemRangeRemoved(newItems.size, oldItems.size - newItems.size)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_water_quality, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvParameterName: TextView = itemView.findViewById(R.id.tvParameterName)
            private val tvValue: TextView = itemView.findViewById(R.id.tvValue)
            private val tvDate: TextView = itemView.findViewById(R.id.tvDate)

            fun bind(result: WaterQualityResult) {
                tvParameterName.text = result.characteristicName
                tvValue.text = itemView.context.getString(R.string.quality_value_format,
                    result.resultValue.toString(), result.resultUnit)
                tvDate.text = itemView.context.getString(R.string.measured_on_format,
                    formatDate(result.activityDate))
            }
        }
    }
}
