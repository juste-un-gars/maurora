/**
 * @file WeatherRepository.kt
 * @description Repository for fetching weather data from Open-Meteo API.
 */
package com.franck.aurorawidget.data.remote

import com.franck.aurorawidget.data.model.WeatherData
import com.franck.aurorawidget.data.model.WeatherResponse
import com.franck.aurorawidget.data.model.toWeatherData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import timber.log.Timber

/**
 * Fetches current weather conditions from the Open-Meteo API.
 * Used to get cloud cover and sunrise/sunset for aurora visibility calculation.
 *
 * @param client Ktor HttpClient (use [HttpClientFactory.create])
 */
class WeatherRepository(private val client: HttpClient) {

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
    }

    /**
     * Fetches weather data for the given coordinates.
     * @param latitude User latitude (-90 to 90)
     * @param longitude User longitude (-180 to 180)
     * @return [Result] containing [WeatherData] on success, or the exception on failure.
     */
    suspend fun fetchWeather(latitude: Double, longitude: Double): Result<WeatherData> =
        runCatching {
            Timber.d("Fetching weather from Open-Meteo for (%.2f, %.2f)...", latitude, longitude)
            val startTime = System.currentTimeMillis()

            val response: WeatherResponse = client.get(BASE_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("current", "cloud_cover")
                parameter("daily", "sunrise,sunset")
                parameter("timezone", "auto")
                parameter("forecast_days", 1)
            }.body()

            val data = response.toWeatherData()
            val duration = System.currentTimeMillis() - startTime
            Timber.d(
                "Weather fetched in %dms: cloud=%d%%, sunrise=%s, sunset=%s",
                duration, data.cloudCover, data.sunrise, data.sunset
            )
            data
        }.onFailure { e ->
            Timber.e(e, "Failed to fetch weather data")
        }
}
