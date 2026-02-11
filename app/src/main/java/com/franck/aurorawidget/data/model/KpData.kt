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
