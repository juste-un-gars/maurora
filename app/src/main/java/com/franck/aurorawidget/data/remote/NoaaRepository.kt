/**
 * @file NoaaRepository.kt
 * @description Repository for fetching aurora data from NOAA SWPC.
 */
package com.franck.aurorawidget.data.remote

import com.franck.aurorawidget.data.model.KpData
import com.franck.aurorawidget.data.model.KpForecast
import com.franck.aurorawidget.data.model.KpForecastPoint
import com.franck.aurorawidget.data.model.OvationData
import com.franck.aurorawidget.data.model.OvationResponse
import com.franck.aurorawidget.data.model.toOvationData
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import timber.log.Timber

/**
 * Fetches and parses aurora probability data from NOAA SWPC.
 * @param client Ktor HttpClient (use [HttpClientFactory.create])
 */
class NoaaRepository(private val client: HttpClient) {

    companion object {
        private const val OVATION_URL =
            "https://services.swpc.noaa.gov/json/ovation_aurora_latest.json"
        private const val KP_URL =
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json"
        private const val KP_FORECAST_URL =
            "https://services.swpc.noaa.gov/products/noaa-planetary-k-index-forecast.json"
    }

    /**
     * Fetches the latest OVATION aurora data.
     * @return [Result] containing [OvationData] on success, or the exception on failure.
     */
    suspend fun fetchOvationData(): Result<OvationData> = runCatching {
        Timber.d("Fetching OVATION data from NOAA...")
        val startTime = System.currentTimeMillis()

        val response: OvationResponse = client.get(OVATION_URL).body()
        val data = response.toOvationData()

        val duration = System.currentTimeMillis() - startTime
        Timber.d(
            "OVATION data fetched in %dms: %d points, observation=%s",
            duration, data.points.size, data.observationTime
        )
        data
    }.onFailure { e ->
        Timber.e(e, "Failed to fetch OVATION data")
    }

    /**
     * Fetches the current planetary Kp index.
     * Response is a JSON array of string arrays: [["time_tag","kp","observed","noaa_scale"], ...]
     * @return [Result] containing [KpData] with the latest Kp value.
     */
    suspend fun fetchKpIndex(): Result<KpData> = runCatching {
        Timber.d("Fetching Kp index from NOAA...")
        val response: List<List<String>> = client.get(KP_URL).body()

        // First row is headers, last row is the most recent value
        val latest = response.last()
        val kp = latest[1].toDouble()
        val timeTag = latest[0]

        Timber.d("Kp index fetched: %.2f at %s", kp, timeTag)
        KpData(value = kp, timeTag = timeTag)
    }.onFailure { e ->
        Timber.e(e, "Failed to fetch Kp index")
    }

    /**
     * Fetches the 3-day Kp forecast.
     * Returns points from now onward (estimated + predicted), up to 72h.
     * @return [Result] containing [KpForecast] with forecast points.
     */
    suspend fun fetchKpForecast(): Result<KpForecast> = runCatching {
        Timber.d("Fetching Kp forecast from NOAA...")
        val response: List<List<String?>> = client.get(KP_FORECAST_URL).body()

        // First row is headers, skip it
        val points = response.drop(1).mapNotNull { row ->
            val timeTag = row.getOrNull(0) ?: return@mapNotNull null
            val kp = row.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
            val type = row.getOrNull(2) ?: "unknown"
            KpForecastPoint(timeTag = timeTag, kp = kp, type = type)
        }

        // Keep only estimated + predicted (future data)
        val futurePoints = points.filter { it.type != "observed" }
        Timber.d("Kp forecast fetched: %d total, %d future points", points.size, futurePoints.size)
        KpForecast(points = futurePoints)
    }.onFailure { e ->
        Timber.e(e, "Failed to fetch Kp forecast")
    }
}
