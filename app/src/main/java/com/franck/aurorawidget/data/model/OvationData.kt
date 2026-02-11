/**
 * @file OvationData.kt
 * @description Data models for NOAA OVATION aurora probability data.
 */
package com.franck.aurorawidget.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw JSON response from NOAA OVATION endpoint.
 * Each coordinate is [longitude (0-359), latitude (-90 to 90), aurora (0-100)].
 */
@Serializable
data class OvationResponse(
    @SerialName("Observation Time") val observationTime: String,
    @SerialName("Forecast Time") val forecastTime: String,
    @SerialName("Data Format") val dataFormat: String,
    val coordinates: List<List<Int>>
)

/**
 * Parsed aurora data point.
 * @property longitude 0-359 degrees
 * @property latitude -90 to 90 degrees
 * @property aurora Aurora probability value (0-100)
 */
data class OvationPoint(
    val longitude: Int,
    val latitude: Int,
    val aurora: Int
)

/**
 * Processed OVATION data ready for use.
 * @property observationTime UTC timestamp of the observation
 * @property forecastTime UTC timestamp of the forecast
 * @property points All aurora data points from the grid
 */
data class OvationData(
    val observationTime: String,
    val forecastTime: String,
    val points: List<OvationPoint>
)

/** Converts raw coordinate arrays to [OvationPoint] list. */
fun OvationResponse.toOvationData(): OvationData = OvationData(
    observationTime = observationTime,
    forecastTime = forecastTime,
    points = coordinates.mapNotNull { coord ->
        if (coord.size >= 3) OvationPoint(coord[0], coord[1], coord[2]) else null
    }
)
