package com.example.aqualume.data.repository

import com.example.aqualume.api.ApiClient
import com.example.aqualume.data.model.CsvRow
import com.example.aqualume.data.model.WaterQualityStation
import com.example.aqualume.data.model.WaterQualityResult
import com.example.aqualume.data.util.ApiResult
import com.example.aqualume.data.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos


class WaterQualityRepositoryImpl : WaterQualityRepository {
    private val apiService = ApiClient.waterQualityApiService

    override suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        radiusMiles: Int
    ): ApiResult<List<WaterQualityStation>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val latDelta = radiusMiles / 69.0
                val lonDelta = radiusMiles / (69.0 * cos(Math.toRadians(latitude)))
                val url = "https://www.waterqualitydata.us/wqx3/Station/search?bBox=${longitude - lonDelta},${latitude - latDelta},${longitude + lonDelta},${latitude + latDelta}&mimeType=text/csv"

                val response = apiService.getStations(url)

                if (response.isSuccessful) {
                    val csvContent = response.body() ?: throw Exception("Empty response body")
                    val linesSeq = csvContent.lineSequence().iterator()
                    if (!linesSeq.hasNext()) throw Exception("Empty CSV")
                    val headers = parseCsvLine(linesSeq.next())
                    val idIndex = headers.indexOf("Location_Identifier")
                    val latIndex = headers.indexOf("Location_Latitude")
                    val lonIndex = headers.indexOf("Location_Longitude")
                    val nameIndex = headers.indexOf("Location_Name")
                    if (idIndex == -1 || latIndex == -1 || lonIndex == -1) {
                        throw Exception("Missing columns in CSV")
                    }
                    val stations = mutableListOf<WaterQualityStation>()
                    while (linesSeq.hasNext()) {
                        val line = linesSeq.next()
                        val cols = parseCsvLine(line)
                        if (cols.size > maxOf(idIndex, latIndex, lonIndex)) {
                            runCatching {
                                val stationLat = cols[latIndex].toDouble()
                                val stationLon = cols[lonIndex].toDouble()
                                val distance = WaterQualityStation.calculateDistance(
                                    stationLat, stationLon, latitude, longitude
                                )
                                WaterQualityStation(
                                    siteId = cols[idIndex],
                                    latitude = stationLat,
                                    longitude = stationLon,
                                    distanceToUser = distance,
                                    siteName = if (nameIndex != -1 && nameIndex < cols.size) cols[nameIndex] else "Unknown Station"
                                )
                            }.getOrNull()?.let { stations.add(it) }
                        }
                    }
                    stations.sortedBy { it.distanceToUser }
                } else {
                    throw Exception("Error: ${response.code()} - ${response.message()}")
                }
            }
        }
    }

    override suspend fun getWaterQualityResults(stationId: String): ApiResult<List<WaterQualityResult>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val url = "https://www.waterqualitydata.us/data/Result/search?siteid=$stationId&sampleMedia=Water&mimeType=csv&dataProfile=resultPhysChem&providers=NWIS&providers=STORET"
                val response = apiService.getWaterQualityResults(url)

                if (response.isSuccessful) {
                    val csvContent = response.body() ?: throw Exception("Empty response body")
                    android.util.Log.d("WaterQualityRepo", "CSV content length: ${csvContent.length}")
                    val linesSeq = csvContent.lineSequence().iterator()
                    if (!linesSeq.hasNext()) {
                        android.util.Log.d("WaterQualityRepo", "No lines in CSV for station $stationId")
                        throw Exception("Empty results for station $stationId")
                    }
                    val headers = parseCsvLine(linesSeq.next())
                    android.util.Log.d("WaterQualityRepo", "Headers: $headers")
                    val results = mutableListOf<WaterQualityResult>()
                    var lineCount = 0
                    while (linesSeq.hasNext()) {
                        val line = linesSeq.next()
                        val row = parseCsvLine(line)
                        if (row.size == headers.size) {
                            val csvRow = CsvRow(headers.zip(row).toMap())
                            csvRow.toWaterQualityResult()?.let { results.add(it) }
                        }
                        lineCount++
                    }
                    android.util.Log.d("WaterQualityRepo", "Parsed $lineCount lines, results size: ${results.size}")
                    if (results.isEmpty()) {
                        android.util.Log.d("WaterQualityRepo", "No valid results parsed for station $stationId")
                    }
                    results
                } else {
                    throw Exception("Error: ${response.code()} - ${response.message()}")
                }
            }
        }
    }

    override suspend fun getFirstStationWithResults(
        stations: List<WaterQualityStation>
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                var lastError: Exception? = null

                // Sort stations by distance first
                val sortedStations = stations.sortedBy { it.distanceToUser }

                // Check each station in order of distance until we find one with results
                for (station in sortedStations) {
                    try {
                        val resultsResult = getWaterQualityResults(station.siteId)
                        if (resultsResult is ApiResult.Success && resultsResult.data.isNotEmpty()) {
                            // Return immediately when we find a valid station with results
                            return@safeApiCall station to resultsResult.data
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }

                // If we get here, no stations had results
                throw lastError ?: Exception("No stations with results found")
            }
        }
    }

    override suspend fun getFirstStationWithRecentResults(
        stations: List<WaterQualityStation>,
        maxRadiusMiles: Double
    ): ApiResult<Pair<WaterQualityStation, List<WaterQualityResult>>> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                var lastError: Exception? = null

                val stationsWithinRadius = stations
                    .filter { it.distanceToUser <= maxRadiusMiles }
                    .sortedBy { it.distanceToUser }

                for (station in stationsWithinRadius) {
                    try {
                        val resultsResult = getWaterQualityResults(station.siteId)
                        if (resultsResult is ApiResult.Success && resultsResult.data.isNotEmpty()) {
                            return@safeApiCall station to resultsResult.data
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }

                val remainingStations = stations
                    .filter { it.distanceToUser > maxRadiusMiles }
                    .sortedBy { it.distanceToUser }

                for (station in remainingStations) {
                    try {
                        val resultsResult = getWaterQualityResults(station.siteId)
                        if (resultsResult is ApiResult.Success && resultsResult.data.isNotEmpty()) {
                            return@safeApiCall station to resultsResult.data
                        }
                    } catch (e: Exception) {
                        lastError = e
                    }
                }

                throw lastError ?: Exception("No stations with results found")
            }
        }
    }

    override suspend fun getStationById(stationId: String): ApiResult<WaterQualityStation> {
        return withContext(Dispatchers.IO) {
            safeApiCall {
                val url = "https://www.waterqualitydata.us/wqx3/Station/search?siteid=$stationId&mimeType=text/csv"

                val response = apiService.getStations(url)

                if (response.isSuccessful) {
                    val csvContent = response.body() ?: throw Exception("Empty response body")
                    val linesSeq = csvContent.lineSequence().iterator()
                    if (!linesSeq.hasNext()) throw Exception("Empty CSV")

                    val headers = parseCsvLine(linesSeq.next())
                    val idIndex = headers.indexOf("Location_Identifier")
                    val latIndex = headers.indexOf("Location_Latitude")
                    val lonIndex = headers.indexOf("Location_Longitude")
                    val nameIndex = headers.indexOf("Location_Name")

                    if (idIndex == -1 || latIndex == -1 || lonIndex == -1) {
                        throw Exception("Missing columns in CSV")
                    }

                    if (!linesSeq.hasNext()) throw Exception("No station found with ID: $stationId")

                    val line = linesSeq.next()
                    val cols = parseCsvLine(line)
                    if (cols.size > maxOf(idIndex, latIndex, lonIndex)) {
                        try {
                            val stationLat = cols[latIndex].toDouble()
                            val stationLon = cols[lonIndex].toDouble()

                            return@safeApiCall WaterQualityStation(
                                siteId = cols[idIndex],
                                latitude = stationLat,
                                longitude = stationLon,
                                distanceToUser = 0.0, // We don't know the user location here
                                siteName = if (nameIndex != -1 && nameIndex < cols.size) cols[nameIndex] else "Unknown Station"
                            )
                        } catch (e: Exception) {
                            throw Exception("Error parsing station data: ${e.message}")
                        }
                    } else {
                        throw Exception("Invalid data format for station")
                    }
                } else {
                    throw Exception("Error: ${response.code()} - ${response.message()}")
                }
            }
        }
    }

    override suspend fun getResultsByStationId(stationId: String): ApiResult<List<WaterQualityResult>> {
        return getWaterQualityResults(stationId)
    }

    private fun parseCsvLine(line: String): List<String> {
        if (line.isBlank()) return emptyList()

        val result = ArrayList<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val char = line[i]
            when {
                // Handle escaped quotes (two double quotes in a row)
                char == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i += 2 // Skip both quotes
                }
                // Handle quote that starts or ends a quoted section
                char == '"' -> {
                    inQuotes = !inQuotes
                    i++
                }
                // Handle comma outside quotes (field separator)
                char == ',' && !inQuotes -> {
                    result.add(current.toString().trim())
                    current.setLength(0)
                    i++
                }
                // Handle all other characters
                else -> {
                    current.append(char)
                    i++
                }
            }
        }

        // Add the last field
        result.add(current.toString().trim())

        return result
    }
}
