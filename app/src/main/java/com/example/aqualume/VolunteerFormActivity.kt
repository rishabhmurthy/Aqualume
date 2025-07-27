package com.example.aqualume

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore

class VolunteerFormActivity : AppCompatActivity() {

    private lateinit var edtname: EditText
    private lateinit var edtage: EditText
    private lateinit var submitbtn: Button

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_volunteer_form)
        Log.d("Form Load", ">>>>>>>>>>>Volunteer Form has been loaded")

        edtname = findViewById(R.id.nameedt)
        edtage = findViewById(R.id.ageedt)
        submitbtn = findViewById(R.id.submitbtn)

        submitbtn.setOnClickListener {
            Log.d("Button Clicker", ">>>>>>>>>>>The Button has been clicked")
            val name = edtname.text.toString()
            val age = edtage.text.toString()

            val data = hashMapOf(
                "name" to name,
                "age" to age,
            )

            db.collection("users")
                .add(data)
                .addOnSuccessListener {
                    Log.d("Firestore", "Data added: $data")
                    Toast.makeText(this, "Successfully added data", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("Firestore", "Error adding data", e)
                    Toast.makeText(this, "Failed to add data: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }

    }
}