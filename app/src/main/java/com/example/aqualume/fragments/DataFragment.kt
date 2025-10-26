package com.example.aqualume.fragments

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aqualume.notifications.MovementNotificationService
import com.example.aqualume.R
import com.example.aqualume.data.model.WaterQualityResult
import com.example.aqualume.data.model.WaterQualityStation
import com.example.aqualume.data.repository.RepositoryProvider
import com.example.aqualume.data.util.ApiResult
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.util.Locale

class DataFragment : Fragment() {
    private val TAG = "DataFragment"

    companion object {
        private var cachedStation: WaterQualityStation? = null
        private var cachedResults: List<WaterQualityResult>? = null
        private var lastFetchedLatitude: Double = 0.0
        private var lastFetchedLongitude: Double = 0.0
        private var hasCachedData: Boolean = false
    }

    private val waterQualityRepository = RepositoryProvider.waterQualityRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
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
    private lateinit var tvLoadingStatus: TextView
    private lateinit var waterQualityAdapter: WaterQualityAdapter
    private var currentLocation: Location? = null
    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> {
            }
            else -> {
                showError("Location permission denied. Cannot fetch nearby water stations.")
            }
        }
    }
    private var locationReceiver: BroadcastReceiver? = null
    private var locationResponseReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLocationChangeReceiver()
        setupLocationResponseReceiver()
    }

    private fun setupLocationChangeReceiver() {
        locationReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MovementNotificationService.Companion.ACTION_LOCATION_CHANGED) {
                    val latitude = intent.getDoubleExtra(MovementNotificationService.Companion.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(MovementNotificationService.Companion.EXTRA_LONGITUDE, 0.0)
                    val distanceMiles = intent.getDoubleExtra(MovementNotificationService.Companion.EXTRA_DISTANCE_MILES, 0.0)

                    Log.d(TAG, "Received location change broadcast: lat=$latitude, lon=$longitude, distance=$distanceMiles")

                    activity?.runOnUiThread {
                        Log.d(TAG, "Forcing refresh from notification service broadcast")
                        showLoading()
                        fetchNearbyStations(latitude, longitude)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            requireContext(),
            locationReceiver,
            IntentFilter(MovementNotificationService.Companion.ACTION_LOCATION_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Registered location change broadcast receiver")
    }

    private fun setupLocationResponseReceiver() {
        locationResponseReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == MovementNotificationService.Companion.ACTION_LOCATION_RESPONSE) {
                    val latitude = intent.getDoubleExtra(MovementNotificationService.Companion.EXTRA_LATITUDE, 0.0)
                    val longitude = intent.getDoubleExtra(MovementNotificationService.Companion.EXTRA_LONGITUDE, 0.0)

                    Log.d(TAG, "Received location response from service: lat=$latitude, lon=$longitude")

                    activity?.runOnUiThread {
                        showLoading()
                        fetchNearbyStations(latitude, longitude)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            requireContext(),
            locationResponseReceiver,
            IntentFilter(MovementNotificationService.Companion.ACTION_LOCATION_RESPONSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        Log.d(TAG, "Registered location response receiver")
    }

    override fun onDestroy() {
        super.onDestroy()
        locationReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
                Log.d(TAG, "Unregistered location change broadcast receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }

        locationResponseReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
                Log.d(TAG, "Unregistered location response receiver")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering response receiver", e)
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
        tvLoadingStatus = view.findViewById(R.id.tvLoadingStatus)

        setupRecyclerView()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val stationId = arguments?.getString("stationId")
        if (!stationId.isNullOrEmpty()) {
            Log.d(TAG, "Received station ID from MapFragment: $stationId")

            showLoading()
            fetchStationData(stationId)
        } else {
            checkLocationPermissionAndFetchData()
        }

        btnRefresh.setOnClickListener {
            Log.d(TAG, "Refresh button clicked, requesting location from service")
            showLoading()

            val locationFromService = MovementNotificationService.Companion.getLastKnownLocation()
            if (locationFromService != null) {
                Log.d(TAG, "Using static location from service: ${locationFromService.first}, ${locationFromService.second}")
                fetchNearbyStations(locationFromService.first, locationFromService.second)
            } else {
                val intent = Intent(MovementNotificationService.Companion.ACTION_REQUEST_LOCATION)
                requireContext().sendBroadcast(intent)
                Log.d(TAG, "Requested location from service via broadcast")
            }
        }
    }

    private fun resetCachedData() {
        Log.d(TAG, "Resetting cached data")
        cachedStation = null
        cachedResults = null
        lastFetchedLatitude = 0.0
        lastFetchedLongitude = 0.0
        hasCachedData = false
    }

    private fun setupRecyclerView() {
        waterQualityAdapter = WaterQualityAdapter()
        rvWaterQuality.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = waterQualityAdapter
        }
    }

    private fun checkLocationPermissionAndFetchData() {
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

        showLoading()
        val locationFromService = MovementNotificationService.Companion.getLastKnownLocation()
        if (locationFromService != null) {
            Log.d(TAG, "Using static location from service: ${locationFromService.first}, ${locationFromService.second}")
            fetchNearbyStations(locationFromService.first, locationFromService.second)
        } else {
            val intent = Intent(MovementNotificationService.Companion.ACTION_REQUEST_LOCATION)
            requireContext().sendBroadcast(intent)
            Log.d(TAG, "Requested location from service via broadcast")
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

    private fun fetchNearbyStations(latitude: Double, longitude: Double) {
        Log.d(TAG, "fetchNearbyStations called with lat: $latitude, lon: $longitude")
        if (hasCachedData &&
            lastFetchedLatitude == latitude &&
            lastFetchedLongitude == longitude) {

            Log.d(TAG, "Using cached data")
            cachedStation?.let { station ->
                cachedResults?.let { results ->
                    Log.d(TAG, "Displaying cached station: ${station.siteName} with ${results.size} results")
                    displayStationInfo(station)
                    displayQualityResults(results)
                    hideLoading()
                    return
                }
            }
            Log.e(TAG, "Cache was marked as valid but data was null")
        }

        Log.d(TAG, "Fetching new water quality data")
        showLoading("Finding water quality stations near you...")
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Log.d(TAG, "Calling water quality repository")
                when (val result = waterQualityRepository.getNearbyStations(latitude, longitude)) {
                    is ApiResult.Success -> {
                        val stations = result.data
                        Log.d(TAG, "Found ${stations.size} nearby stations")
                        if (stations.isNotEmpty()) {
                            updateLoadingStatus("Found ${stations.size} stations in your area.\nSearching for water quality data...")

                            Log.d(TAG, "Looking for station with results")
                            when (val stationResult = waterQualityRepository.getFirstStationWithResults(stations)) {
                                is ApiResult.Success -> {
                                    val (station, qualityResults) = stationResult.data
                                    Log.d(TAG, "Found station ${station.siteName} with ${qualityResults.size} results")

                                    updateLoadingStatus("Processing ${qualityResults.size} water quality measurements...\nRemoving duplicates and filtering data...")

                                    if (isAdded && activity != null) {
                                        activity?.runOnUiThread {
                                            displayStationInfo(station)
                                            displayQualityResults(qualityResults)

                                            cachedStation = station
                                            cachedResults = qualityResults
                                            lastFetchedLatitude = latitude
                                            lastFetchedLongitude = longitude
                                            hasCachedData = true

                                            hideLoading()
                                        }
                                    }
                                }
                                is ApiResult.Error -> {
                                    Log.e(TAG, "Error finding station with results", stationResult.exception)
                                    if (isAdded && activity != null) {
                                        activity?.runOnUiThread {
                                            showError("No water quality results found for any nearby stations: ${stationResult.exception.message}")
                                        }
                                    }
                                }
                                is ApiResult.Loading -> {
                                    Log.d(TAG, "Still loading station results")
                                    updateLoadingStatus("Retrieving water quality data from stations...\nThis may take a moment.")
                                }
                            }
                        } else {
                            Log.e(TAG, "No stations found nearby")
                            if (isAdded && activity != null) {
                                activity?.runOnUiThread {
                                    showError("No water quality stations found nearby.")
                                }
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Error fetching nearby stations", result.exception)
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                showError("Error fetching nearby stations: ${result.exception.message}")
                            }
                        }
                    }
                    is ApiResult.Loading -> {
                        Log.d(TAG, "Loading nearby stations")
                        updateLoadingStatus("Searching for water monitoring stations near your location...")
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        Log.d(TAG, "Job cancelled - fragment probably navigated away: ${e.message}")
                    }
                    else -> {
                        Log.e(TAG, "Unexpected error fetching water quality data", e)
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                showError("Unexpected error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fetchStationData(stationId: String) {
        Log.d(TAG, "fetchStationData called with stationId: $stationId")

        showLoading("Retrieving water station information...")

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                updateLoadingStatus("Looking up station ID: $stationId...")
                when (val stationResult = waterQualityRepository.getStationById(stationId)) {
                    is ApiResult.Success -> {
                        val station = stationResult.data
                        Log.d(TAG, "Found station ${station.siteName}")

                        updateLoadingStatus("Found station: ${station.siteName}\nFetching water quality data...")

                        when (val resultsResult = waterQualityRepository.getResultsByStationId(stationId)) {
                            is ApiResult.Success -> {
                                val results = resultsResult.data
                                Log.d(TAG, "Found ${results.size} results for station ${station.siteName}")

                                updateLoadingStatus("Processing ${results.size} water quality measurements...\nRemoving duplicates and filtering data...")

                                if (isAdded && activity != null) {
                                    activity?.runOnUiThread {
                                        displayStationInfo(station)
                                        displayQualityResults(results)

                                        cachedStation = station
                                        cachedResults = results
                                        lastFetchedLatitude = station.latitude
                                        lastFetchedLongitude = station.longitude
                                        hasCachedData = true

                                        hideLoading()
                                    }
                                }
                            }
                            is ApiResult.Error -> {
                                Log.e(TAG, "Error fetching results for station $stationId", resultsResult.exception)
                                if (isAdded && activity != null) {
                                    activity?.runOnUiThread {
                                        showError("Error fetching water quality results: ${resultsResult.exception.message}")
                                    }
                                }
                            }
                            is ApiResult.Loading -> {
                                Log.d(TAG, "Loading results for station $stationId")
                                updateLoadingStatus("Retrieving water quality measurements...")
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        Log.e(TAG, "Error fetching station by ID $stationId", stationResult.exception)
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                showError("Error fetching station data: ${stationResult.exception.message}")
                            }
                        }
                    }
                    is ApiResult.Loading -> {
                        Log.d(TAG, "Loading station data for ID $stationId")
                        updateLoadingStatus("Connecting to water quality database...")
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        Log.d(TAG, "Job cancelled - fragment probably navigated away: ${e.message}")
                    }
                    else -> {
                        Log.e(TAG, "Unexpected error fetching station data", e)
                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                showError("Unexpected error: ${e.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun displayStationInfo(station: WaterQualityStation) {
        tvStationName.text = station.siteName

        viewLifecycleOwner.lifecycleScope.launch {
            try {
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

                if (isAdded && view != null) {
                    tvStationId.text = cityText
                }
            } catch (e: Exception) {
                when (e) {
                    is CancellationException -> {
                        Log.d(TAG, "Geocoding job cancelled - fragment probably navigated away: ${e.message}")
                    }
                    else -> {
                        Log.e(TAG, "Error in reverse geocoding", e)
                    }
                }
            }
        }

        val distanceInMiles = station.distanceToUser * 69.0
        val formattedDistance = String.format(Locale.US, "%.1f", distanceInMiles)
        tvStationDistance.text = "Distance: $formattedDistance miles away"

        tvLastUpdated.text = if (station.mostRecentActivityDate != null) {
            "Last Updated: ${formatDate(station.mostRecentActivityDate)}"
        } else {
            "Last Updated: Unknown"
        }

        cardStation.visibility = View.VISIBLE
        tvQualityTitle.visibility = View.VISIBLE
    }

    private fun filterMostRecentResults(results: List<WaterQualityResult>): List<WaterQualityResult> {
        return results
            .groupBy { it.characteristicName }
            .mapNotNull { (_, group) ->
                group.maxByOrNull { it.activityDate }
            }
    }

    private fun displayQualityResults(results: List<WaterQualityResult>) {
        val filteredResults = filterMostRecentResults(results)
        waterQualityAdapter.submitList(filteredResults)
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

    private fun showLoading(initialMessage: String = "Initializing...") {
        progressBar.visibility = View.VISIBLE
        tvError.visibility = View.GONE
        cardStation.visibility = View.GONE
        rvWaterQuality.visibility = View.GONE
        tvQualityTitle.visibility = View.GONE
        tvLoadingStatus.visibility = View.VISIBLE

        tvLoadingStatus.text = initialMessage

        waterQualityAdapter.submitList(emptyList())

        tvStationName.text = ""
        tvStationId.text = ""
        tvStationDistance.text = ""
        tvLastUpdated.text = ""
    }

    private fun updateLoadingStatus(message: String) {
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                tvLoadingStatus.text = message
                tvLoadingStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun hideLoading() {
        progressBar.visibility = View.GONE
        tvLoadingStatus.visibility = View.GONE
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

    override fun onResume() {
        super.onResume()
        if (hasCachedData) {
            cachedStation?.let { station ->
                cachedResults?.let { results ->
                    displayStationInfo(station)
                    displayQualityResults(results)
                }
            }
        }
    }

    inner class WaterQualityAdapter : RecyclerView.Adapter<WaterQualityAdapter.ViewHolder>() {
        private var items = listOf<WaterQualityResult>()

        fun submitList(newItems: List<WaterQualityResult>) {
            val oldItems = items
            items = newItems

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
            private val tvSearchMoreInfo: TextView = itemView.findViewById(R.id.tvSearchMoreInfo)
            private val cardView: CardView? = itemView.findViewById(R.id.cardView)

            fun bind(result: WaterQualityResult) {
                tvParameterName.text = result.characteristicName
                tvValue.text = itemView.context.getString(
                    R.string.quality_value_format,
                    result.resultValue.toString(), result.resultUnit)
                tvDate.text = itemView.context.getString(
                    R.string.measured_on_format,
                    formatDate(result.activityDate))
                tvSearchMoreInfo.setOnClickListener {
                    val query = "Is a ${result.characteristicName} level of ${result.resultValue} ${result.resultUnit} in water safe?"
                    val encodedQuery = URLEncoder.encode(query, "UTF-8")
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.data = Uri.parse("https://www.google.com/search?q=$encodedQuery")
                    itemView.context.startActivity(intent)
                }

                if (result.characteristicName.equals("Escherichia coli", ignoreCase = true)) {
                    val value = result.resultValue
                    if (value != null) {
                        val colorRes = if (value > 100.0) R.color.ecoli_unsafe else R.color.ecoli_safe
                        cardView?.setCardBackgroundColor(ContextCompat.getColor(itemView.context, colorRes))
                    } else {
                        cardView?.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                    }
                } else {
                    cardView?.setCardBackgroundColor(ContextCompat.getColor(itemView.context, android.R.color.white))
                }
            }
        }
    }
}
