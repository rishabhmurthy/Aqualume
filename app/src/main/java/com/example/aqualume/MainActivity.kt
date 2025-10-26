package com.example.aqualume

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.aqualume.fragments.DataFragment
import com.example.aqualume.fragments.MapFragment
import com.example.aqualume.fragments.VolunteerFormFragment
import com.example.aqualume.fragments.WeatherFragment
import com.example.aqualume.notifications.MovementNotificationService
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private var currentItemId: Int = R.id.nav_weather

    fun updateBottomNavigation(itemId: Int) {
        bottomNavigationView.selectedItemId = itemId
        currentItemId = itemId
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val intent = android.content.Intent(this, MovementNotificationService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        loadFragment(WeatherFragment())

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId != currentItemId) {
                val view: View = bottomNavigationView.findViewById(item.itemId)
                view.animate()
                    .scaleX(1.1f).scaleY(1.1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).duration = 150
                    }.start()

                val selectedFragment: Fragment = when (item.itemId) {
                    R.id.nav_weather -> WeatherFragment()
                    R.id.nav_form -> VolunteerFormFragment()
                    R.id.nav_map -> MapFragment()
                    R.id.nav_data -> DataFragment()
                    else -> WeatherFragment()
                }

                loadFragment(selectedFragment)
                currentItemId = item.itemId
            }

            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
