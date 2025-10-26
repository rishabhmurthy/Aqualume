package com.example.aqualume

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mapbox.search.result.SearchSuggestion

class SearchSuggestionsAdapter(
    private val suggestions: MutableList<SearchSuggestion> = mutableListOf(),
    private val onSuggestionClickListener: (SearchSuggestion) -> Unit
) : RecyclerView.Adapter<SearchSuggestionsAdapter.SuggestionViewHolder>() {

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
