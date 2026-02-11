/**
 * @file KpData.kt
 * @description Data model for NOAA planetary Kp index.
 */
package com.franck.aurorawidget.data.model

/**
 * Current Kp index value.
 * @property value Kp index (0.0-9.0)
 * @property timeTag Observation timestamp (e.g. "2026-02-11 12:00:00.000")
 */
data class KpData(
    val value: Double,
    val timeTag: String
)

/**
 * Single 3-hour Kp forecast point.
 * @property timeTag Timestamp (e.g. "2026-02-11 12:00:00")
 * @property kp Predicted Kp value (0.0-9.0)
 * @property type "observed", "estimated", or "predicted"
 */
data class KpForecastPoint(
    val timeTag: String,
    val kp: Double,
    val type: String
)

/**
 * 3-day Kp forecast (up to 24 points at 3-hour intervals).
 * @property points Forecast points sorted by time
 */
data class KpForecast(
    val points: List<KpForecastPoint>
)
