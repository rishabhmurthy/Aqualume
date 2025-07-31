package com.example.aqualume

import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private var currentItemId: Int = R.id.nav_weather // Keep track of current tab

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Load default fragment
        loadFragment(WeatherFragment())

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId != currentItemId) {
                // Apply bounce effect
                val view: View = bottomNavigationView.findViewById(item.itemId)
                view.animate()
                    .scaleX(1.1f).scaleY(1.1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator())
                    .withEndAction {
                        view.animate().scaleX(1f).scaleY(1f).duration = 150
                    }.start()

                // Load corresponding fragment
                val selectedFragment: Fragment = when (item.itemId) {
                    R.id.nav_weather -> WeatherFragment()
                    R.id.nav_form -> VolunteerFormFragment()
                    R.id.nav_map -> MapFragment()
                    R.id.nav_data -> DataFragment()
                    else -> WeatherFragment()
                }

                loadFragment(selectedFragment)
                currentItemId = item.itemId // Update current tab
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
