package com.example.aqualume.fragments

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.core.content.edit
import com.example.aqualume.R
import com.google.firebase.firestore.FirebaseFirestore

class VolunteerFormFragment : Fragment() {

    private lateinit var edtName: EditText
    private lateinit var edtAge: EditText
    private lateinit var edtDate: EditText
    private lateinit var edtCity: EditText
    private lateinit var edtTemperature: EditText
    private lateinit var edtSalinity: EditText
    private lateinit var edtFauna: EditText
    private lateinit var edtWeather: EditText
    private lateinit var edtRain: EditText
    private lateinit var submitBtn: Button
    private val db = FirebaseFirestore.getInstance()

    private val PREF_NAME = "form_cache"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_volunteer_form, container, false)

        edtName = view.findViewById(R.id.nameedt)
        edtAge = view.findViewById(R.id.ageedt)
        edtDate = view.findViewById(R.id.Dateedt)
        edtCity = view.findViewById(R.id.Sitelocationedt)
        edtTemperature = view.findViewById(R.id.Temperatureedt)
        edtSalinity = view.findViewById(R.id.Salinityleveledt)
        edtFauna = view.findViewById(R.id.Faunaedt)
        edtWeather = view.findViewById(R.id.Weatheredt)
        edtRain = view.findViewById(R.id.Rainedt)
        submitBtn = view.findViewById(R.id.submitbtn)

        loadFormCache()
        setupAutoSave()

        submitBtn.setOnClickListener {
            val name = edtName.text.toString().trim()
            val age = edtAge.text.toString().trim()
            val date = edtDate.text.toString().trim()
            val city = edtCity.text.toString().trim()
            val temp = edtTemperature.text.toString().trim()
            val salinity = edtSalinity.text.toString().trim()
            val fauna = edtFauna.text.toString().trim()
            val weather = edtWeather.text.toString().trim()
            val rain = edtRain.text.toString().trim()

            if (name.isNotEmpty() && age.isNotEmpty()) {
                val data = hashMapOf(
                    "Name" to name,
                    "Age" to age,
                    "Date" to date,
                    "City" to city,
                    "Temperature" to temp,
                    "Salinity Level" to salinity,
                    "Fauna" to fauna,
                    "Weather condition" to weather,
                    "Recently Rained" to rain
                )

                db.collection("volunteers")
                    .add(data)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Data submitted!", Toast.LENGTH_SHORT).show()
                        clearForm()
                        clearFormCache()
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Failed to add data", e)
                        Toast.makeText(requireContext(), "Submission failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
            }
        }

        return view
    }

    private fun saveFormCache() {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            putString("Name", edtName.text.toString())
            putString("Age", edtAge.text.toString())
            putString("Date", edtDate.text.toString())
            putString("City", edtCity.text.toString())
            putString("Temperature", edtTemperature.text.toString())
            putString("Salinity", edtSalinity.text.toString())
            putString("Fauna", edtFauna.text.toString())
            putString("Weather", edtWeather.text.toString())
            putString("Rain", edtRain.text.toString())
        }
    }

    private fun loadFormCache() {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        edtName.setText(sharedPref.getString("Name", ""))
        edtAge.setText(sharedPref.getString("Age", ""))
        edtDate.setText(sharedPref.getString("Date", ""))
        edtCity.setText(sharedPref.getString("City", ""))
        edtTemperature.setText(sharedPref.getString("Temperature", ""))
        edtSalinity.setText(sharedPref.getString("Salinity", ""))
        edtFauna.setText(sharedPref.getString("Fauna", ""))
        edtWeather.setText(sharedPref.getString("Weather", ""))
        edtRain.setText(sharedPref.getString("Rain", ""))
    }

    private fun clearFormCache() {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit {
            clear()
        }
    }

    private fun setupAutoSave() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveFormCache()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        edtName.addTextChangedListener(watcher)
        edtAge.addTextChangedListener(watcher)
        edtDate.addTextChangedListener(watcher)
        edtCity.addTextChangedListener(watcher)
        edtTemperature.addTextChangedListener(watcher)
        edtSalinity.addTextChangedListener(watcher)
        edtFauna.addTextChangedListener(watcher)
        edtWeather.addTextChangedListener(watcher)
        edtRain.addTextChangedListener(watcher)
    }

    private fun clearForm() {
        edtName.text.clear()
        edtAge.text.clear()
        edtDate.text.clear()
        edtCity.text.clear()
        edtTemperature.text.clear()
        edtSalinity.text.clear()
        edtFauna.text.clear()
        edtWeather.text.clear()
        edtRain.text.clear()
    }
}
