package com.example.aqualume.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.aqualume.MainActivity
import com.example.aqualume.notifications.NotificationHelper
import com.example.aqualume.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueriedRenderedFeature
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.style.expressions.dsl.generated.literal
import com.mapbox.maps.extension.style.image.image
import com.mapbox.maps.extension.style.layers.generated.symbolLayer
import com.mapbox.maps.extension.style.layers.properties.generated.IconAnchor
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.extension.style.style
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.CircleAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createCircleAnnotationManager
import com.mapbox.maps.plugin.gestures.addOnMapLongClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.plugin.locationcomponent.OnIndicatorPositionChangedListener
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.search.autocomplete.PlaceAutocomplete
import com.mapbox.search.autocomplete.PlaceAutocompleteOptions
import com.mapbox.search.autocomplete.PlaceAutocompleteSuggestion
import com.mapbox.search.autocomplete.PlaceAutocompleteType
import com.mapbox.search.result.SearchAddress
import com.mapbox.search.result.SearchSuggestion
import com.mapbox.search.ui.adapter.autocomplete.PlaceAutocompleteUiAdapter
import com.mapbox.search.ui.view.CommonSearchViewConfiguration
import com.mapbox.search.ui.view.SearchResultsView
import com.mapbox.search.ui.view.place.SearchPlace
import com.mapbox.search.ui.view.place.SearchPlaceBottomSheetView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MapFragment : Fragment(), CoroutineScope by MainScope() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private var mapView: MapView? = null
    private lateinit var mapboxMap: MapboxMap
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private val SOURCE_ID = "api-source"
    private val WATER_DROPLET_ICON_ID = "water-droplet"
    private val SYMBOL_LAYER_ID = "water-symbol-layer"
    private var currentInfoBoxView: View? = null
    private val MAPBOX_SOURCE_URL = "mapbox://addyveerendra.cp7m28xl"
    private val MAPBOX_SOURCE_LAYER = "filtered_stations-6qy89v"

    private lateinit var placeAutocomplete: PlaceAutocomplete
    private lateinit var searchResultsView: SearchResultsView
    private lateinit var placeAutocompleteUiAdapter: PlaceAutocompleteUiAdapter
    private lateinit var searchEditText: EditText
    private lateinit var searchPlaceView: SearchPlaceBottomSheetView
    private lateinit var mapMarkersManager: MapMarkersManager
    private var ignoreNextQueryUpdate = false

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

    private var hasCenteredOnUser = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        NotificationHelper.createNotificationChannel(requireContext())

        placeAutocomplete = PlaceAutocomplete.create()

        mapView = view.findViewById(R.id.mapView)
        mapView?.let { mv ->
            viewAnnotationManager = mv.viewAnnotationManager
            mapboxMap = mv.mapboxMap
        }

        initializeSearchComponents(view)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        setupLocationCallback()

        if (!hasLocationPermission()) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            startLocationUpdates()
        }

        setupMap()
    }

    private fun initializeSearchComponents(view: View) {
        searchEditText = view.findViewById(R.id.searchEditText)
        searchResultsView = view.findViewById(R.id.searchResultsView)
        searchPlaceView = view.findViewById(R.id.searchPlaceView)

        searchResultsView.initialize(
            SearchResultsView.Configuration(
                commonConfiguration = CommonSearchViewConfiguration()
            )
        )

        placeAutocompleteUiAdapter = PlaceAutocompleteUiAdapter(
            view = searchResultsView,
            placeAutocomplete = placeAutocomplete
        )

        searchPlaceView.apply {
            initialize(CommonSearchViewConfiguration())
            isFavoriteButtonVisible = false

            addOnCloseClickListener {
                hide()
                closePlaceCard()
            }

            addOnNavigateClickListener { searchPlace ->
                startActivity(geoIntent(searchPlace.coordinate))
            }

            addOnShareClickListener { searchPlace ->
                startActivity(shareIntent(searchPlace))
            }
        }

        placeAutocompleteUiAdapter.addSearchListener(object : PlaceAutocompleteUiAdapter.SearchListener {
            override fun onSuggestionsShown(suggestions: List<PlaceAutocompleteSuggestion>) {
            }

            override fun onSuggestionSelected(suggestion: PlaceAutocompleteSuggestion) {
                openPlaceCard(suggestion)
            }

            override fun onPopulateQueryClick(suggestion: PlaceAutocompleteSuggestion) {
                searchEditText.setText(suggestion.name)
            }

            override fun onError(e: Exception) {
                Log.e("MapFragment", "Search error", e)
            }
        })

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                if (ignoreNextQueryUpdate) {
                    ignoreNextQueryUpdate = false
                } else {
                    closePlaceCard()
                }

                lifecycleScope.launch {
                    placeAutocompleteUiAdapter.search(text.toString())
                    searchResultsView.isVisible = text.isNotEmpty()
                }
            }

            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            }

            override fun afterTextChanged(s: Editable) {
            }
        })

        mapView?.let { mv ->
            mapMarkersManager = MapMarkersManager(mv)
        }

        mapView?.let { mv ->
            mv.mapboxMap.addOnMapLongClickListener { point ->
                reverseGeocoding(point)
                true
            }
        }
    }

    private fun reverseGeocoding(point: Point) {
        val types: List<PlaceAutocompleteType> = when (mapboxMap.cameraState.zoom) {
            in 0.0..4.0 -> REGION_LEVEL_TYPES
            in 4.0..6.0 -> DISTRICT_LEVEL_TYPES
            in 6.0..12.0 -> LOCALITY_LEVEL_TYPES
            else -> ALL_TYPES
        }

        val context = requireContext()

        lifecycleScope.launch {
            try {
                val response = placeAutocomplete.reverse(point, PlaceAutocompleteOptions(types = types))
                response.onValue { suggestions ->
                    if (suggestions.isEmpty()) {
                        context.showToast(R.string.place_autocomplete_reverse_geocoding_error_message)
                    } else {
                        openPlaceCard(suggestions.first())
                    }
                }.onError { error ->
                    Log.d("MapFragment", "Reverse geocoding error", error)
                    context.showToast(R.string.place_autocomplete_reverse_geocoding_error_message)
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Exception during reverse geocoding", e)
                context.showToast(R.string.place_autocomplete_reverse_geocoding_error_message)
            }
        }
    }

    private fun openPlaceCard(suggestion: PlaceAutocompleteSuggestion) {
        ignoreNextQueryUpdate = true
        searchEditText.setText("")

        val context = requireContext()

        lifecycleScope.launch {
            try {
                placeAutocomplete.select(suggestion).onValue { result ->
                    try {
                        mapMarkersManager.showMarker(result.coordinate)
                        searchPlaceView.open(SearchPlace.createFromPlaceAutocompleteResult(result))
                        searchEditText.hideKeyboard()
                        searchResultsView.isVisible = false
                    } catch (e: Exception) {
                        Log.e("MapFragment", "Error opening place card", e)
                        context.showToast(R.string.place_autocomplete_selection_error)
                    }
                }.onError { error ->
                    Log.d("MapFragment", "Suggestion selection error", error)
                    context.showToast(R.string.place_autocomplete_selection_error)
                }
            } catch (e: Exception) {
                Log.e("MapFragment", "Exception during suggestion selection", e)
                context.showToast(R.string.place_autocomplete_selection_error)
            }
        }
    }

    private fun closePlaceCard() {
        searchPlaceView.hide()
        mapMarkersManager.clearMarkers()
    }

    private fun View.hideKeyboard() =
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)

    private fun Context.showToast(@StringRes resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
    }

    private fun geoIntent(point: Point): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${point.latitude()}, ${point.longitude()}"))

    private fun shareIntent(searchPlace: SearchPlace): Intent {
        val text = "${searchPlace.name}. " +
                "Address: ${searchPlace.address?.formattedAddress(SearchAddress.FormatStyle.Short) ?: "unknown"}. " +
                "Geo coordinate: (lat=${searchPlace.coordinate.latitude()}, lon=${searchPlace.coordinate.longitude()})"

        return Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    }

    @SuppressLint("UseKtx")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun setupMap() {
        try {
            val waterDropletDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_water_droplet)

            if (waterDropletDrawable == null) {
                Log.e("MapFragment", "Water droplet drawable is null")
                return
            }

            val waterDropletBitmap = Bitmap.createBitmap(
                waterDropletDrawable.intrinsicWidth,
                waterDropletDrawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            val waterDropletCanvas = Canvas(waterDropletBitmap)
            waterDropletDrawable.setBounds(0, 0, waterDropletCanvas.width, waterDropletCanvas.height)
            waterDropletDrawable.draw(waterDropletCanvas)

            Log.d("MapFragment", "Loading map style with water stations")
            mapView?.mapboxMap?.loadStyle(
                style(Style.MAPBOX_STREETS) {
                    +image(WATER_DROPLET_ICON_ID, waterDropletBitmap)
                    +vectorSource(SOURCE_ID) {
                        url(MAPBOX_SOURCE_URL)
                    }
                    +symbolLayer(SYMBOL_LAYER_ID, SOURCE_ID) {
                        sourceLayer(MAPBOX_SOURCE_LAYER)
                        iconImage(WATER_DROPLET_ICON_ID)
                        iconSize(literal(1.0))
                        iconAllowOverlap(true)
                        iconAnchor(IconAnchor.BOTTOM)
                    }
                }
            ) { style ->
                Log.d("MapFragment", "Style loaded successfully")
                configureLocationComponent()
                setupMapClickListener()
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "Error setting up map", e)
        }
    }

    private fun setupMapClickListener() {
        mapView?.let { mv ->
            mv.gestures.addOnMapClickListener { point ->
                removeCurrentInfoBox()
                onMapClick(point)
            }
        }
    }

    private fun configureLocationComponent() {
        mapView?.location?.apply {
            enabled = true
            pulsingEnabled = true
        }

        mapView?.location?.addOnIndicatorPositionChangedListener(object : OnIndicatorPositionChangedListener {
            override fun onIndicatorPositionChanged(point: Point) {
                if (!hasCenteredOnUser) {
                    mapView?.mapboxMap?.setCamera(
                        CameraOptions.Builder()
                            .center(point)
                            .zoom(14.0)
                            .build()
                    )
                    hasCenteredOnUser = true
                    mapView?.location?.removeOnIndicatorPositionChangedListener(this)
                }
            }
        })
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    currentLocation = location
                }
            }
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
                Log.e("MapFragment", "Error requesting location updates: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }

    fun onMapClick(point: Point): Boolean {
        mapboxMap.queryRenderedFeatures(
            RenderedQueryGeometry(mapboxMap.pixelForCoordinate(point)),
            RenderedQueryOptions(listOf(SYMBOL_LAYER_ID), null)
        ) {
            onFeatureClicked(it, point) { feature ->
                val monitoringLocationId = feature.getStringProperty("MonitoringLocationIdentifier")

                Log.d("MapFragment", "Click successful on feature with MonitoringLocationIdentifier: $monitoringLocationId")

                showInfoBoxForFeature(feature, point)
            }
        }
        return true
    }

    private fun onFeatureClicked(
        expected: Expected<String, List<QueriedRenderedFeature>>,
        point: Point,
        onFeatureClicked: (Feature) -> Unit
    ) {
        if (expected.isValue && expected.value?.size!! > 0) {
            expected.value?.get(0)?.queriedFeature?.feature?.let { feature ->
                Log.d("MapFragment", "Feature clicked: ${feature.id()}")
                onFeatureClicked.invoke(feature)
            }
        } else {
            Log.d("MapFragment", "No feature found at clicked point")
        }
    }

    private fun showInfoBoxForFeature(feature: Feature, point: Point) {
        removeCurrentInfoBox()

        val infoBoxView = LayoutInflater.from(requireContext()).inflate(R.layout.map_info_box, mapView, false)

        infoBoxView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val titleTextView = infoBoxView.findViewById<TextView>(R.id.infoBoxTitle)
        val contentTextView = infoBoxView.findViewById<TextView>(R.id.infoBoxContent)
        val closeButton = infoBoxView.findViewById<ImageButton>(R.id.closeButton)
        val getWaterQualityButton = infoBoxView.findViewById<Button>(R.id.getWaterQualityButton)

        val locationName = feature.getStringProperty("MonitoringLocationName") ?: "Unknown Location"
        val countyName = feature.getStringProperty("CountyName") ?: "Unknown County"
        val stateName = feature.getStringProperty("StateName") ?: "Unknown State"
        val locationInfo = "$countyName, $stateName"

        titleTextView.text = locationName
        contentTextView.text = locationInfo

        closeButton.setOnClickListener {
            removeCurrentInfoBox()
        }

        getWaterQualityButton.setOnClickListener {
            Log.d("MapFragment", "Get water quality button clicked for location: $locationName")
            val stationId = feature.getStringProperty("MonitoringLocationIdentifier")

            val dataFragment = DataFragment().apply {
                arguments = Bundle().apply {
                    putString("stationId", stationId)
                }
            }

            val mainActivity = activity as? MainActivity
            mainActivity?.updateBottomNavigation(R.id.nav_data)

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, dataFragment)
                .addToBackStack(null)
                .commit()
        }

        viewAnnotationManager.addViewAnnotation(
            view = infoBoxView,
            options = viewAnnotationOptions {
                geometry(point)
                allowOverlap(true)
                annotationAnchor {
                    anchor(ViewAnnotationAnchor.BOTTOM)
                    offsetY(-(waterDropletDrawable?.intrinsicHeight ?: 40).toDouble())
                }
            }
        )

        currentInfoBoxView = infoBoxView
    }

    private fun removeCurrentInfoBox() {
        currentInfoBoxView?.let { infoBoxView ->
            viewAnnotationManager.removeViewAnnotation(infoBoxView)
            currentInfoBoxView = null
        }
    }

    private val waterDropletDrawable by lazy {
        ContextCompat.getDrawable(requireContext(), R.drawable.ic_water_droplet)
    }

    private class MapMarkersManager(mapView: MapView) {
        private val mapboxMap = mapView.mapboxMap
        private val circleAnnotationManager = mapView.annotations.createCircleAnnotationManager(null)
        private val markers = mutableMapOf<String, Point>()

        fun clearMarkers() {
            markers.clear()
            circleAnnotationManager.deleteAll()
        }

        fun showMarker(coordinate: Point) {
            clearMarkers()

            val circleAnnotationOptions: CircleAnnotationOptions = CircleAnnotationOptions()
                .withPoint(coordinate)
                .withCircleRadius(8.0)
                .withCircleColor("#ee4e8b")
                .withCircleStrokeWidth(2.0)
                .withCircleStrokeColor("#ffffff")

            val annotation = circleAnnotationManager.create(circleAnnotationOptions)
            markers[annotation.id] = coordinate

            CameraOptions.Builder()
                .center(coordinate)
                .padding(MARKERS_INSETS_OPEN_CARD)
                .zoom(14.0)
                .build().also {
                    mapboxMap.setCamera(it)
                }
        }
    }

    inner class SearchSuggestionsAdapter(
        private val suggestions: MutableList<SearchSuggestion> = mutableListOf(),
        private val onSuggestionClickListener: (SearchSuggestion) -> Unit
    ) : RecyclerView.Adapter<SearchSuggestionsAdapter.SuggestionViewHolder>() {

        inner class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nameTextView: TextView = view.findViewById(R.id.suggestionName)
            val addressTextView: TextView = view.findViewById(R.id.suggestionAddress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_suggestion, parent, false)
            return SuggestionViewHolder(view)
        }

        override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
            val suggestion = suggestions[position]

            holder.nameTextView.text = suggestion.name

            val address = suggestion.address?.formattedAddress(com.mapbox.search.result.SearchAddress.FormatStyle.Short)
            holder.addressTextView.text = address ?: suggestion.descriptionText ?: ""

            holder.itemView.setOnClickListener {
                onSuggestionClickListener(suggestion)
            }
        }

        override fun getItemCount(): Int = suggestions.size

        fun updateSuggestions(newSuggestions: List<SearchSuggestion>) {
            suggestions.clear()
            suggestions.addAll(newSuggestions)
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val MARKERS_EDGE_OFFSET = 64.0
        private const val PLACE_CARD_HEIGHT = 300.0
        private const val MARKERS_TOP_OFFSET = 88.0

        private val MARKERS_INSETS_OPEN_CARD = EdgeInsets(
            MARKERS_TOP_OFFSET, MARKERS_EDGE_OFFSET, PLACE_CARD_HEIGHT, MARKERS_EDGE_OFFSET
        )

        private val REGION_LEVEL_TYPES = listOf(
            PlaceAutocompleteType.AdministrativeUnit.Country,
            PlaceAutocompleteType.AdministrativeUnit.Region
        )

        private val DISTRICT_LEVEL_TYPES = REGION_LEVEL_TYPES + listOf(
            PlaceAutocompleteType.AdministrativeUnit.Postcode,
            PlaceAutocompleteType.AdministrativeUnit.District
        )

        private val LOCALITY_LEVEL_TYPES = DISTRICT_LEVEL_TYPES + listOf(
            PlaceAutocompleteType.AdministrativeUnit.Place,
            PlaceAutocompleteType.AdministrativeUnit.Locality
        )

        private val ALL_TYPES = listOf(
            PlaceAutocompleteType.Poi,
            PlaceAutocompleteType.AdministrativeUnit.Country,
            PlaceAutocompleteType.AdministrativeUnit.Region,
            PlaceAutocompleteType.AdministrativeUnit.Postcode,
            PlaceAutocompleteType.AdministrativeUnit.District,
            PlaceAutocompleteType.AdministrativeUnit.Place,
            PlaceAutocompleteType.AdministrativeUnit.Locality,
            PlaceAutocompleteType.AdministrativeUnit.Neighborhood,
            PlaceAutocompleteType.AdministrativeUnit.Street,
            PlaceAutocompleteType.AdministrativeUnit.Address,
        )
    }
}
