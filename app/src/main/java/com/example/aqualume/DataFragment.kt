package com.example.aqualume

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.util.Locale

class DataFragment : Fragment() {
    private val TAG = "DataFragment"

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

    // Adapter for water quality results
    private lateinit var waterQualityAdapter: WaterQualityAdapter

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

        // Initialize RecyclerView and adapter
        setupRecyclerView()

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // Check location permissions and fetch data
        checkLocationPermissionAndFetchData()
    }

    private fun setupRecyclerView() {
        waterQualityAdapter = WaterQualityAdapter()
        rvWaterQuality.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = waterQualityAdapter
        }
    }

    private fun checkLocationPermissionAndFetchData() {
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
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = waterQualityRepository.getNearbyStations(latitude, longitude)) {
                is ApiResult.Success -> {
                    val stations = result.data
                    if (stations.isNotEmpty()) {
                        // Sort stations by distance to user
                        val sortedStations = stations.sortedBy { it.distanceToUser }

                        // Get water quality results for the nearest station
                        fetchWaterQualityResults(sortedStations.first())
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

    private fun fetchWaterQualityResults(station: WaterQualityStation) {
        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = waterQualityRepository.getWaterQualityResults(station.siteId)) {
                is ApiResult.Success -> {
                    val qualityResults = result.data
                    if (qualityResults.isNotEmpty()) {
                        displayStationInfo(station)
                        displayQualityResults(qualityResults)
                        hideLoading()
                    } else {
                        // Try another station if the first one doesn't have results
                        showError("No water quality results found for this station.")
                    }
                }
                is ApiResult.Error -> {
                    showError("Error fetching water quality results: ${result.exception.message}")
                }
                is ApiResult.Loading -> {
                    // Show loading state (progress already visible)
                }
            }
        }
    }

    private fun displayStationInfo(station: WaterQualityStation) {
        tvStationName.text = station.siteName
        tvStationId.text = "Station ID: ${station.siteId}"

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
