package com.example.aqualume.data.model

import com.google.gson.annotations.SerializedName

data class GeocodingResponse(
    @SerializedName("results")
    val results: List<GeocodingResult>,
    @SerializedName("status")
    val status: Status
)

data class GeocodingResult(
    @SerializedName("components")
    val components: GeoComponents,
    @SerializedName("formatted")
    val formatted: String?,
    @SerializedName("geometry")
    val geometry: Geometry
)

data class GeoComponents(
    @SerializedName("city")
    val city: String?,
    @SerializedName("state")
    val state: String?,
    @SerializedName("country")
    val country: String?
)

data class Geometry(
    @SerializedName("lat")
    val lat: Double?,
    @SerializedName("lng")
    val lng: Double?
)

data class Status(
    @SerializedName("code")
    val code: Int?,
    @SerializedName("message")
    val message: String?
)
