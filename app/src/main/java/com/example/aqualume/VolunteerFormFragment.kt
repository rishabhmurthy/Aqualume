package com.example.aqualume

import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.firestore.FirebaseFirestore

class VolunteerFormFragment : Fragment() {

    private lateinit var edtName: EditText
    private lateinit var edtAge: EditText
    private lateinit var submitBtn: Button
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_volunteer_form, container, false)

        edtName = view.findViewById(R.id.nameedt)
        edtAge = view.findViewById(R.id.ageedt)
        submitBtn = view.findViewById(R.id.submitbtn)

        submitBtn.setOnClickListener {
            val name = edtName.text.toString().trim()
            val age = edtAge.text.toString().trim()

            if (name.isNotEmpty() && age.isNotEmpty()) {
                val data = hashMapOf("name" to name, "age" to age)

                db.collection("volunteers")
                    .add(data)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "Data submitted!", Toast.LENGTH_SHORT).show()
                        edtName.text.clear()
                        edtAge.text.clear()
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
}
