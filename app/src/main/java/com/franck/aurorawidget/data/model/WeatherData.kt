/**
 * @file WeatherData.kt
 * @description Data models for Open-Meteo weather API response.
 */
package com.franck.aurorawidget.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw JSON response from Open-Meteo forecast endpoint.
 * Only the fields we need are mapped; unknown keys are ignored.
 */
@Serializable
data class WeatherResponse(
    val current: CurrentWeather,
    val daily: DailyWeather
)

@Serializable
data class CurrentWeather(
    @SerialName("cloud_cover") val cloudCover: Int,
    val time: String
)

@Serializable
data class DailyWeather(
    val sunrise: List<String>,
    val sunset: List<String>
)

/**
 * Processed weather data ready for use in score calculation.
 * @property cloudCover Cloud cover percentage (0-100)
 * @property sunrise Today's sunrise time (ISO 8601 local time, e.g. "2026-02-11T08:15")
 * @property sunset Today's sunset time (ISO 8601 local time, e.g. "2026-02-11T17:30")
 * @property fetchTime Time of the current weather observation (ISO 8601)
 */
data class WeatherData(
    val cloudCover: Int,
    val sunrise: String,
    val sunset: String,
    val fetchTime: String
)

/** Converts API response to domain model, extracting today's sunrise/sunset. */
fun WeatherResponse.toWeatherData(): WeatherData = WeatherData(
    cloudCover = current.cloudCover,
    sunrise = daily.sunrise.firstOrNull().orEmpty(),
    sunset = daily.sunset.firstOrNull().orEmpty(),
    fetchTime = current.time
)
