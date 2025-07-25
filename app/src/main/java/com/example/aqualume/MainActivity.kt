package com.example.aqualume

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.GravityCompat
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.*
import com.google.gson.Gson
import java.io.*
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var expandableListView: ExpandableListView
    private lateinit var contentFrame: FrameLayout
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    private lateinit var listDataHeader: List<String>
    private lateinit var listDataChild: HashMap<String, List<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        drawerLayout = findViewById(R.id.drawerLayout)
        toolbar = findViewById(R.id.toolbar)
        expandableListView = findViewById(R.id.expandableListView)
        contentFrame = findViewById(R.id.content_frame)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setSupportActionBar(toolbar)

        val drawerToggle = androidx.appcompat.app.ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        prepareListData()
        setupExpandableListView()

        // Default page on launch
        loadLayout(R.layout.activity_about)
    }

    private fun prepareListData() {
        listDataHeader = listOf("About", "Our Work", "Get Involved", "Contact", "Weather")

        listDataChild = HashMap()
        listDataChild["About"] = listOf("About Us", "Staff and Board", "Mission and History", "Work With Us", "Supporters", "News")
        listDataChild["Our Work"] = listOf("Water Protection", "Land Protection", "Climate Resilience", "Education")
        listDataChild["Get Involved"] = listOf("Membership", "Business Support", "Volunteering", "Upcoming Programs")
        listDataChild["Contact"] = listOf("Contact Us")
        listDataChild["Weather"] = listOf("Weather Info")
    }

    private fun setupExpandableListView() {
        val groupData = ArrayList<Map<String, String>>()
        val childData = ArrayList<List<Map<String, String>>>()

        for (group in listDataHeader) {
            val curGroupMap = HashMap<String, String>()
            curGroupMap["GROUP_NAME"] = group
            groupData.add(curGroupMap)

            val children = listDataChild[group] ?: emptyList()
            val childList = ArrayList<Map<String, String>>()
            for (child in children) {
                val curChildMap = HashMap<String, String>()
                curChildMap["CHILD_NAME"] = child
                childList.add(curChildMap)
            }
            childData.add(childList)
        }

        val adapter = SimpleExpandableListAdapter(
            this,
            groupData,
            R.layout.drawer_list_group,
            arrayOf("GROUP_NAME"),
            intArrayOf(R.id.lblListHeader),
            childData,
            R.layout.drawer_list_item,
            arrayOf("CHILD_NAME"),
            intArrayOf(R.id.lblListItem)
        )

        expandableListView.setAdapter(adapter)

        expandableListView.setOnChildClickListener { _, _, groupPos, childPos, _ ->
            val group = listDataHeader[groupPos]
            val child = listDataChild[group]?.get(childPos)

            when (group) {

                "About" -> when (child) {
                    "About Us" -> loadLayout(R.layout.activity_about)
                    "Staff and Board" -> loadLayout(R.layout.activity_staff)
                    "Mission and History" -> loadLayout(R.layout.activity_mission)
                    "Work With Us" -> loadLayout(R.layout.activity_work_with_us)
                    "Supporters" -> loadLayout(R.layout.activity_supporters)
                    "News" -> loadLayout(R.layout.activity_news)
                }
                "Our Work" -> when (child) {
                    "Water Protection" -> loadLayout(R.layout.activity_water_protection)
                    "Land Protection" -> loadLayout(R.layout.activity_land_protection)
                    "Climate Resilience" -> loadLayout(R.layout.activity_climate_resilience)
                    "Education" -> loadLayout(R.layout.activity_education)

                }
                "Get Involved" -> when (child) {
                    "Membership" -> loadLayout(R.layout.activity_membership)
                    "Business Support" -> loadLayout(R.layout.activity_business_support)
                    "Volunteering" -> loadLayout(R.layout.activity_volunteering)
                    "Upcoming Programs" -> loadLayout(R.layout.activity_programs)
                }
                "Contact" -> when (child) {
                    "Contact Us" -> loadLayout(R.layout.activity_contact)
                }

                "Weather" -> loadLayout(R.layout.activity_weather)
            }

            drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun loadLayout(layoutResId: Int) {
        contentFrame.removeAllViews()
        val view = layoutInflater.inflate(layoutResId, contentFrame, false)
        contentFrame.addView(view)

        if (layoutResId == R.layout.activity_weather) {
            setupWeather(view)
        }
    }

    private fun setupWeather(view: View) {
        val outputText = view.findViewById<TextView>(R.id.weatherOutput)

        val reverseButton = view.findViewById<Button>(R.id.reverseGeocodeButton)



        reverseButton.setOnClickListener {
            if (hasLocationPermission()) {
                getLocationAndReverseGeocode(outputText)
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getLocationAndReverseGeocode(outputText: TextView) {
        if (!hasLocationPermission()) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        val request = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 1000
            numUpdates = 1
        }

        fusedLocationClient.requestLocationUpdates(request, object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation
                if (location != null) {
                    reverseGeocode(location.latitude, location.longitude, outputText)
                } else {
                    Toast.makeText(this@MainActivity, "Using default location: Boston", Toast.LENGTH_SHORT).show()
                    reverseGeocode(42.3601, -71.0589, outputText)
                }
            }
        }, null)
    }

    private fun reverseGeocode(lat: Double, lon: Double, outputText: TextView) {
        Thread {
            try {
                val url = URL("https://api.opencagedata.com/geocode/v1/json?q=$lat,$lon&key=90f04c444fac4cb0aafc78a5fa13ba9a")
                (url.openConnection() as? HttpsURLConnection)?.run {
                    if (responseCode == 200) {
                        inputStream.bufferedReader().use {
                            val json = Gson().fromJson(it, Map::class.java)
                            val results = json["results"] as? List<*> ?: emptyList<Any>()
                            val firstResult = results.firstOrNull() as? Map<*, *>

                            val components = firstResult?.get("components") as? Map<*, *>
                            val city = components?.get("city") ?: components?.get("town") ?: "Unknown"
                            val country = components?.get("country") ?: "Unknown"

                            runOnUiThread {
                                outputText.text = "📍 $city, $country"
                                fetchWeather(lat, lon, outputText)
                            }
                        }
                    } else {
                        Log.e("Geocode", "API error $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("Geocode", "Error", e)
            }
        }.start()
    }

    private fun fetchWeather(lat: Double, lon: Double, outputText: TextView) {
        Thread {
            try {
                val apiKey = "66ef97e13d2afe8faf457114d04605a0"
                val url = URL("https://api.openweathermap.org/data/2.5/weather?lat=$lat&lon=$lon&units=metric&appid=$apiKey")
                (url.openConnection() as? HttpsURLConnection)?.run {
                    if (responseCode == 200) {
                        inputStream.bufferedReader().use {
                            val json = Gson().fromJson(it, Map::class.java)
                            val main = json["main"] as? Map<*, *>
                            val temp = main?.get("temp")
                            val weatherList = json["weather"] as? List<*>
                            val weather = weatherList?.firstOrNull() as? Map<*, *>
                            val description = weather?.get("description")

                            runOnUiThread {
                                outputText.append("\n🌡️ $temp°C\n☁️ $description")
                            }
                        }
                    } else {
                        Log.e("Weather", "Error: $responseCode")
                    }
                }
            } catch (e: Exception) {
                Log.e("Weather", "Exception", e)
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            val outputText = findViewById<TextView>(R.id.weatherOutput)
            getLocationAndReverseGeocode(outputText)
        }
    }
}
