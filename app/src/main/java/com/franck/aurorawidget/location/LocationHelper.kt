/**
 * @file LocationHelper.kt
 * @description Simple helper to get user location using Android LocationManager.
 */
package com.franck.aurorawidget.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.Locale

/**
 * Result of a location request.
 */
data class LocationResult(
    val latitude: Double,
    val longitude: Double,
    val name: String
)

/**
 * Gets user location from the system LocationManager.
 * Uses last known location for speed (no active GPS fix needed).
 */
object LocationHelper {

    /** Returns true if location permission is granted. */
    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets the last known location from any available provider.
     * @return [LocationResult] or null if unavailable.
     */
    fun getLastKnownLocation(context: Context): LocationResult? {
        if (!hasPermission(context)) {
            Timber.w("Location permission not granted")
            return null
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Try GPS first, then network, then passive
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                @Suppress("MissingPermission")
                val loc = lm.getLastKnownLocation(provider)
                if (loc != null && (bestLocation == null || loc.time > bestLocation.time)) {
                    bestLocation = loc
                }
            } catch (_: Exception) {
                // Provider not available
            }
        }

        if (bestLocation == null) {
            Timber.w("No last known location available")
            return null
        }

        val name = reverseGeocode(context, bestLocation.latitude, bestLocation.longitude)
        Timber.d("Location found: %s", name)

        return LocationResult(
            latitude = bestLocation.latitude,
            longitude = bestLocation.longitude,
            name = name
        )
    }

    /** Reverse geocode coordinates to a city/country name. */
    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String {
        return try {
            @Suppress("DEPRECATION")
            val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            when {
                addr == null -> "Unknown location"
                addr.locality != null -> "${addr.locality}, ${addr.countryName ?: ""}"
                addr.adminArea != null -> "${addr.adminArea}, ${addr.countryName ?: ""}"
                else -> addr.countryName ?: "Unknown location"
            }
        } catch (e: Exception) {
            Timber.w(e, "Reverse geocode failed")
            "Unknown location"
        }
    }
}
