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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

/** Singleton DataStore instance. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aurora_settings")

/**
 * Cached dashboard data from the last worker fetch.
 * @property visibilityScore Combined visibility score (0-100), null if weather unavailable
 * @property auroraProbability Raw aurora probability (0-100)
 * @property cloudCover Cloud cover percentage (0-100), null if weather unavailable
 * @property kp Current Kp index, null if unavailable
 * @property sunrise Today's sunrise (ISO 8601 local time), empty if unavailable
 * @property sunset Today's sunset (ISO 8601 local time), empty if unavailable
 * @property lastUpdate Epoch millis of the last successful fetch
 */
data class DashboardData(
    val visibilityScore: Double? = null,
    val auroraProbability: Double = 0.0,
    val cloudCover: Int? = null,
    val kp: Double? = null,
    val sunrise: String = "",
    val sunset: String = "",
    val lastUpdate: Long = 0L,
    val kpForecastCsv: String = "",
    val cloudForecastDailyCsv: String = ""
)

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
    val useGps: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val notificationThreshold: Int = 50,
    val lastNotificationTime: Long = 0L
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
        val NOTIFICATIONS_ENABLED = stringPreferencesKey("notifications_enabled")
        val NOTIFICATION_THRESHOLD = intPreferencesKey("notification_threshold")
        val LAST_NOTIFICATION_TIME = longPreferencesKey("last_notification_time")

        // Dashboard cache
        val DASH_VISIBILITY = stringPreferencesKey("dash_visibility") // nullable Double as string
        val DASH_AURORA = doublePreferencesKey("dash_aurora")
        val DASH_CLOUD = intPreferencesKey("dash_cloud")
        val DASH_KP = stringPreferencesKey("dash_kp") // nullable Double as string
        val DASH_SUNRISE = stringPreferencesKey("dash_sunrise")
        val DASH_SUNSET = stringPreferencesKey("dash_sunset")
        val DASH_LAST_UPDATE = longPreferencesKey("dash_last_update")
        val DASH_KP_FORECAST = stringPreferencesKey("dash_kp_forecast")
        val DASH_CLOUD_FORECAST_DAILY = stringPreferencesKey("dash_cloud_forecast_daily")
    }

    /** Observe settings as a Flow. */
    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            latitude = prefs[Keys.LATITUDE] ?: 48.86,
            longitude = prefs[Keys.LONGITUDE] ?: 2.35,
            locationName = prefs[Keys.LOCATION_NAME] ?: "Paris, France",
            refreshMinutes = prefs[Keys.REFRESH_MINUTES] ?: 30,
            useGps = prefs[Keys.USE_GPS]?.toBooleanStrictOrNull() ?: false,
            notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED]?.toBooleanStrictOrNull() ?: false,
            notificationThreshold = prefs[Keys.NOTIFICATION_THRESHOLD] ?: 50,
            lastNotificationTime = prefs[Keys.LAST_NOTIFICATION_TIME] ?: 0L
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

    /** Save notification preferences. */
    suspend fun saveNotificationSettings(enabled: Boolean, threshold: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATIONS_ENABLED] = enabled.toString()
            prefs[Keys.NOTIFICATION_THRESHOLD] = threshold
        }
        Timber.d("Notifications saved: enabled=%s, threshold=%d%%", enabled, threshold)
    }

    /** Observe cached dashboard data as a Flow. */
    val dashboardFlow: Flow<DashboardData> = context.dataStore.data.map { prefs ->
        DashboardData(
            visibilityScore = prefs[Keys.DASH_VISIBILITY]?.toDoubleOrNull(),
            auroraProbability = prefs[Keys.DASH_AURORA] ?: 0.0,
            cloudCover = prefs[Keys.DASH_CLOUD],
            kp = prefs[Keys.DASH_KP]?.toDoubleOrNull(),
            sunrise = prefs[Keys.DASH_SUNRISE] ?: "",
            sunset = prefs[Keys.DASH_SUNSET] ?: "",
            lastUpdate = prefs[Keys.DASH_LAST_UPDATE] ?: 0L,
            kpForecastCsv = prefs[Keys.DASH_KP_FORECAST] ?: "",
            cloudForecastDailyCsv = prefs[Keys.DASH_CLOUD_FORECAST_DAILY] ?: ""
        )
    }

    /** Read current dashboard data (one-shot). */
    suspend fun getDashboardData(): DashboardData = dashboardFlow.first()

    /** Save dashboard data from the last worker fetch. */
    suspend fun saveDashboardData(data: DashboardData) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DASH_VISIBILITY] = data.visibilityScore?.toString() ?: ""
            prefs[Keys.DASH_AURORA] = data.auroraProbability
            data.cloudCover?.let { prefs[Keys.DASH_CLOUD] = it }
            prefs[Keys.DASH_KP] = data.kp?.toString() ?: ""
            prefs[Keys.DASH_SUNRISE] = data.sunrise
            prefs[Keys.DASH_SUNSET] = data.sunset
            prefs[Keys.DASH_LAST_UPDATE] = data.lastUpdate
            prefs[Keys.DASH_KP_FORECAST] = data.kpForecastCsv
            prefs[Keys.DASH_CLOUD_FORECAST_DAILY] = data.cloudForecastDailyCsv
        }
        Timber.d("Dashboard data cached: visibility=%s, aurora=%.1f%%",
            data.visibilityScore?.let { "%.1f%%".format(it) } ?: "N/A",
            data.auroraProbability
        )
    }

    /** Record the time of the last notification sent. */
    suspend fun saveLastNotificationTime(timeMillis: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_NOTIFICATION_TIME] = timeMillis
        }
    }
}
