/**
 * @file GeocodingRepository.kt
 * @description Repository for city name search using Open-Meteo Geocoding API.
 */
package com.franck.aurorawidget.data.remote

import com.franck.aurorawidget.data.model.GeocodingResponse
import com.franck.aurorawidget.data.model.GeocodingResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import timber.log.Timber

/**
 * Searches cities by name via Open-Meteo Geocoding API (free, no key needed).
 * @param client Ktor HttpClient (use [HttpClientFactory.create])
 */
class GeocodingRepository(private val client: HttpClient) {

    companion object {
        private const val BASE_URL = "https://geocoding-api.open-meteo.com/v1/search"
    }

    /**
     * Searches for cities matching the given query.
     * @param query City name to search for (e.g. "Paris", "Tromso")
     * @param lang Language for results ("en", "fr", etc.)
     * @return [Result] containing a list of [GeocodingResult], or the exception on failure.
     */
    suspend fun searchCities(query: String, lang: String = "en"): Result<List<GeocodingResult>> =
        runCatching {
            Timber.d("Geocoding search: '%s' (lang=%s)", query, lang)
            val response: GeocodingResponse = client.get(BASE_URL) {
                parameter("name", query)
                parameter("count", 5)
                parameter("language", lang)
                parameter("format", "json")
            }.body()

            val results = response.results.orEmpty()
            Timber.d("Geocoding returned %d results", results.size)
            results
        }.onFailure { e ->
            Timber.e(e, "Geocoding search failed for '%s'", query)
        }
}
