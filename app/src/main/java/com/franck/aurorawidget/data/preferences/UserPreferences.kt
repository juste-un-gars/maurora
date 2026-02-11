/**
 * @file UserPreferences.kt
 * @description DataStore-backed user preferences for location and refresh settings.
 */
package com.franck.aurorawidget.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

/** Singleton DataStore instance. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aurora_settings")

/**
 * User location and settings.
 * @property latitude User latitude (-90 to 90)
 * @property longitude User longitude (-180 to 180)
 * @property locationName Display name (e.g. "Paris, France")
 * @property refreshMinutes Widget refresh interval in minutes (15, 30, 60)
 * @property useGps Whether to use GPS for location
 */
data class UserSettings(
    val latitude: Double = 48.86,
    val longitude: Double = 2.35,
    val locationName: String = "Paris, France",
    val refreshMinutes: Int = 30,
    val useGps: Boolean = false
)

/**
 * Manages user preferences via DataStore.
 */
class UserPreferences(val context: Context) {

    private object Keys {
        val LATITUDE = doublePreferencesKey("latitude")
        val LONGITUDE = doublePreferencesKey("longitude")
        val LOCATION_NAME = stringPreferencesKey("location_name")
        val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        val USE_GPS = stringPreferencesKey("use_gps") // stored as "true"/"false"
    }

    /** Observe settings as a Flow. */
    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            latitude = prefs[Keys.LATITUDE] ?: 48.86,
            longitude = prefs[Keys.LONGITUDE] ?: 2.35,
            locationName = prefs[Keys.LOCATION_NAME] ?: "Paris, France",
            refreshMinutes = prefs[Keys.REFRESH_MINUTES] ?: 30,
            useGps = prefs[Keys.USE_GPS]?.toBooleanStrictOrNull() ?: false
        )
    }

    /** Read current settings (one-shot). */
    suspend fun getSettings(): UserSettings = settingsFlow.first()

    /** Save location (manual or GPS). */
    suspend fun saveLocation(latitude: Double, longitude: Double, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LATITUDE] = latitude
            prefs[Keys.LONGITUDE] = longitude
            prefs[Keys.LOCATION_NAME] = name
        }
        Timber.d("Location saved: %s (%.4f, %.4f)", name, latitude, longitude)
    }

    /** Save refresh interval. */
    suspend fun saveRefreshMinutes(minutes: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.REFRESH_MINUTES] = minutes
        }
        Timber.d("Refresh interval saved: %d min", minutes)
    }

    /** Save GPS preference. */
    suspend fun saveUseGps(useGps: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USE_GPS] = useGps.toString()
        }
        Timber.d("Use GPS saved: %s", useGps)
    }
}
