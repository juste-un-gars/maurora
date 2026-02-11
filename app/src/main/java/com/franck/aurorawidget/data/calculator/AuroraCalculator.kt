/**
 * @file AuroraCalculator.kt
 * @description Computes aurora visibility score from OVATION data, weather, and time of day.
 */
package com.franck.aurorawidget.data.calculator

import com.franck.aurorawidget.data.model.OvationData
import com.franck.aurorawidget.data.model.OvationPoint
import com.franck.aurorawidget.data.model.WeatherData
import timber.log.Timber
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Computes aurora visibility score from OVATION grid data, weather, and time of day.
 *
 * Visibility formula: `score = aurora_probability * (1 - cloud_cover/100) * night_factor`
 */
object AuroraCalculator {

    /** Duration of twilight transition in minutes (golden hour). */
    private const val TWILIGHT_MINUTES = 30L

    /**
     * Gets aurora probability (0-100) for the given coordinates.
     * Uses bilinear interpolation on the OVATION 1-degree grid.
     *
     * @param data OVATION grid data
     * @param latitude User latitude (-90 to 90)
     * @param longitude User longitude (-180 to 180 or 0 to 360)
     * @return Aurora probability (0.0-100.0), or 0.0 if data is insufficient
     */
    fun getAuroraProbability(data: OvationData, latitude: Double, longitude: Double): Double {
        if (data.points.isEmpty()) return 0.0

        // Build lookup grid: key = (lon, lat) -> aurora value
        val grid = buildGrid(data.points)

        // Normalize longitude to 0-359 range (OVATION uses 0-359)
        val normLon = normalizeLongitude(longitude)
        val normLat = latitude.coerceIn(-90.0, 90.0)

        val result = interpolate(grid, normLat, normLon)

        Timber.d(
            "Aurora probability at (%.2f, %.2f): %.1f%%",
            latitude, longitude, result
        )
        return result
    }

    /**
     * Computes combined visibility score (0-100) from aurora probability, weather, and time.
     *
     * @param auroraProbability Raw aurora probability (0-100) from OVATION
     * @param weather Current weather data (cloud cover + sunrise/sunset)
     * @param now Current local time (default: now)
     * @return Visibility score (0.0-100.0)
     */
    fun computeVisibilityScore(
        auroraProbability: Double,
        weather: WeatherData,
        now: LocalDateTime = LocalDateTime.now()
    ): Double {
        val cloudFactor = 1.0 - weather.cloudCover / 100.0
        val nightFactor = computeNightFactor(weather.sunrise, weather.sunset, now)
        val score = auroraProbability * cloudFactor * nightFactor

        Timber.d(
            "Visibility: aurora=%.1f%% * cloud=%.2f * night=%.2f = %.1f%%",
            auroraProbability, cloudFactor, nightFactor, score
        )
        return score.coerceIn(0.0, 100.0)
    }

    /**
     * Computes how "dark" it is on a 0.0-1.0 scale.
     * - 1.0 = fully dark (between sunset+twilight and sunrise-twilight)
     * - 0.0 = fully light (daytime)
     * - Progressive transition during twilight (30 min after sunset, 30 min before sunrise)
     *
     * @param sunrise ISO 8601 local time string (e.g. "2026-02-11T08:15")
     * @param sunset ISO 8601 local time string (e.g. "2026-02-11T17:45")
     * @param now Current local time
     * @return Night factor (0.0-1.0)
     */
    internal fun computeNightFactor(
        sunrise: String,
        sunset: String,
        now: LocalDateTime = LocalDateTime.now()
    ): Double {
        if (sunrise.isBlank() || sunset.isBlank()) return 0.5 // Unknown → neutral

        val sunriseTime = parseTime(sunrise) ?: return 0.5
        val sunsetTime = parseTime(sunset) ?: return 0.5

        val minutesOfDay = now.hour * 60L + now.minute

        val sunriseMin = sunriseTime.hour * 60L + sunriseTime.minute
        val sunsetMin = sunsetTime.hour * 60L + sunsetTime.minute

        return when {
            // After sunset: transition 0→1 over TWILIGHT_MINUTES
            minutesOfDay >= sunsetMin -> {
                val elapsed = minutesOfDay - sunsetMin
                (elapsed.toDouble() / TWILIGHT_MINUTES).coerceIn(0.0, 1.0)
            }
            // Before sunrise: transition 1→0 over TWILIGHT_MINUTES
            minutesOfDay <= sunriseMin -> {
                val remaining = sunriseMin - minutesOfDay
                (remaining.toDouble() / TWILIGHT_MINUTES).coerceIn(0.0, 1.0)
            }
            // Daytime
            else -> 0.0
        }
    }

    private fun parseTime(iso: String): LocalDateTime? = try {
        LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    } catch (_: Exception) {
        Timber.w("Failed to parse time: %s", iso)
        null
    }

    /** Builds a lookup map from (longitude, latitude) to aurora value. */
    private fun buildGrid(points: List<OvationPoint>): Map<Pair<Int, Int>, Int> {
        return points.associate { (it.longitude to it.latitude) to it.aurora }
    }

    /** Normalizes longitude from [-180, 180] to [0, 359]. */
    internal fun normalizeLongitude(lon: Double): Double {
        val normalized = lon % 360.0
        return if (normalized < 0) normalized + 360.0 else normalized
    }

    /**
     * Bilinear interpolation on the OVATION grid.
     * Finds 4 surrounding integer grid points and interpolates.
     */
    internal fun interpolate(
        grid: Map<Pair<Int, Int>, Int>,
        lat: Double,
        lon: Double
    ): Double {
        val lonFloor = lon.toInt() % 360
        val lonCeil = (lonFloor + 1) % 360
        val latFloor = lat.toInt().coerceIn(-90, 89)
        val latCeil = (latFloor + 1).coerceIn(-90, 90)

        // Get the 4 corner values (default 0 if missing)
        val q00 = grid[lonFloor to latFloor] ?: 0
        val q10 = grid[lonCeil to latFloor] ?: 0
        val q01 = grid[lonFloor to latCeil] ?: 0
        val q11 = grid[lonCeil to latCeil] ?: 0

        // Fractional parts
        val lonFrac = lon - lon.toInt()
        val latFrac = lat - lat.toInt()

        // Bilinear interpolation
        val result = q00 * (1 - lonFrac) * (1 - latFrac) +
                q10 * lonFrac * (1 - latFrac) +
                q01 * (1 - lonFrac) * latFrac +
                q11 * lonFrac * latFrac

        return result.coerceIn(0.0, 100.0)
    }
}
