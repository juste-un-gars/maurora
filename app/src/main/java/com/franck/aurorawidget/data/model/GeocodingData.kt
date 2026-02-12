/**
 * @file GeocodingData.kt
 * @description Data models for Open-Meteo Geocoding API response.
 */
package com.franck.aurorawidget.data.model

import kotlinx.serialization.Serializable

/** Raw JSON response from Open-Meteo geocoding endpoint. */
@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult>? = null
)

/** A single geocoding result (city match). */
@Serializable
data class GeocodingResult(
    val name: String,
    val country: String = "",
    val admin1: String = "",
    val latitude: Double,
    val longitude: Double
)
