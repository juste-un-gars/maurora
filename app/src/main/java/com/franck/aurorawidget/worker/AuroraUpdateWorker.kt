/**
 * @file AuroraUpdateWorker.kt
 * @description Periodic worker that fetches aurora + weather data and updates all widgets.
 */
package com.franck.aurorawidget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.franck.aurorawidget.data.calculator.AuroraCalculator
import com.franck.aurorawidget.data.preferences.DashboardData
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.data.remote.HttpClientFactory
import com.franck.aurorawidget.data.remote.NoaaRepository
import com.franck.aurorawidget.data.remote.WeatherRepository
import com.franck.aurorawidget.notification.AuroraNotifier
import com.franck.aurorawidget.widget.AuroraLargeWidgetProvider
import com.franck.aurorawidget.widget.AuroraMediumWidgetProvider
import com.franck.aurorawidget.widget.AuroraMiniWidgetProvider
import com.franck.aurorawidget.widget.AuroraWidgetProvider
import com.franck.aurorawidget.widget.WidgetDisplayData
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

class AuroraUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("AuroraUpdateWorker started")
        val startTime = System.currentTimeMillis()

        val prefs = UserPreferences(applicationContext)

        // Early network check — use cache if offline
        if (!isNetworkAvailable()) {
            Timber.w("No network — using cached data")
            updateWidgetsFromCache(prefs)
            return Result.success()
        }

        val client = HttpClientFactory.create()
        return try {
            val noaaRepo = NoaaRepository(client)
            val weatherRepo = WeatherRepository(client)

            val settings = prefs.getSettings()
            val latitude = settings.latitude
            val longitude = settings.longitude

            // Fetch all data (aurora is required, weather/kp are optional)
            val ovationResult = noaaRepo.fetchOvationData()
            val ovationData = ovationResult.getOrElse {
                Timber.w("OVATION fetch failed — falling back to cached data")
                updateWidgetsFromCache(prefs)
                return Result.success()
            }

            val auroraProbability = AuroraCalculator.getAuroraProbability(
                ovationData, latitude, longitude
            )

            val weatherData = weatherRepo.fetchWeather(latitude, longitude).getOrNull()
            val kpData = noaaRepo.fetchKpIndex().getOrNull()
            val kpForecast = noaaRepo.fetchKpForecast().getOrNull()
            val cloudForecast = weatherRepo.fetchCloudForecast(latitude, longitude).getOrNull()

            // Compute combined visibility score if weather available
            val visibilityScore = weatherData?.let {
                AuroraCalculator.computeVisibilityScore(auroraProbability, it)
            }

            val displayData = WidgetDisplayData(
                visibilityScore = visibilityScore,
                auroraProbability = auroraProbability,
                cloudCover = weatherData?.cloudCover,
                kp = kpData?.value,
                kpForecast = kpForecast
            )

            // Serialize forecasts as CSV for dashboard cache
            val kpCsv = kpForecast?.points
                ?.joinToString(",") { String.format(Locale.US, "%.2f", it.kp) } ?: ""
            val cloudDailyCsv = cloudForecast?.dailyCloudCover
                ?.joinToString(",") ?: ""

            // Serialize hourly forecast: HH:mm,temp,code;...
            val hourlyForecastCsv = cloudForecast?.let { fc ->
                val count = minOf(fc.hourlyTimes.size, fc.hourlyTemperatures.size, fc.hourlyWeatherCodes.size)
                (0 until count).joinToString(";") { i ->
                    val time = fc.hourlyTimes[i].let { t -> if (t.length >= 16) t.substring(11, 16) else t }
                    String.format(Locale.US, "%s,%.1f,%d", time, fc.hourlyTemperatures[i], fc.hourlyWeatherCodes[i])
                }
            } ?: ""

            // Serialize daily forecast: date,high,low,code,precip;...
            val dailyForecastCsv = cloudForecast?.let { fc ->
                val count = minOf(
                    fc.dailyDates.size, fc.dailyHighs.size, fc.dailyLows.size,
                    fc.dailyWeatherCodes.size, fc.dailyPrecipProbability.size
                )
                (0 until count).joinToString(";") { i ->
                    String.format(Locale.US, "%s,%.1f,%.1f,%d,%d",
                        fc.dailyDates[i], fc.dailyHighs[i], fc.dailyLows[i],
                        fc.dailyWeatherCodes[i], fc.dailyPrecipProbability[i])
                }
            } ?: ""

            // Cache data for dashboard display
            prefs.saveDashboardData(DashboardData(
                visibilityScore = visibilityScore,
                auroraProbability = auroraProbability,
                cloudCover = weatherData?.cloudCover,
                kp = kpData?.value,
                sunrise = weatherData?.sunrise ?: "",
                sunset = weatherData?.sunset ?: "",
                lastUpdate = System.currentTimeMillis(),
                kpForecastCsv = kpCsv,
                cloudForecastDailyCsv = cloudDailyCsv,
                temperature = weatherData?.temperature,
                feelsLike = weatherData?.feelsLike,
                weatherCode = weatherData?.weatherCode,
                isDay = weatherData?.isDay ?: true,
                highTemp = cloudForecast?.dailyHighs?.firstOrNull(),
                lowTemp = cloudForecast?.dailyLows?.firstOrNull(),
                hourlyForecastCsv = hourlyForecastCsv,
                dailyForecastCsv = dailyForecastCsv
            ))

            updateAllWidgets(displayData)

            // Check if aurora alert notification should be sent
            AuroraNotifier.notifyIfNeeded(
                applicationContext, visibilityScore, auroraProbability, settings, prefs
            )

            val duration = System.currentTimeMillis() - startTime
            Timber.d(
                "Worker done in %dms: aurora=%.1f%%, visibility=%s, cloud=%s, kp=%s",
                duration, auroraProbability,
                visibilityScore?.let { "%.1f%%".format(it) } ?: "N/A",
                weatherData?.cloudCover?.toString() ?: "N/A",
                kpData?.value?.let { "%.1f".format(it) } ?: "N/A"
            )
            Result.success()
        } catch (e: CancellationException) {
            throw e // Never swallow cancellation
        } catch (e: Exception) {
            Timber.e(e, "AuroraUpdateWorker unexpected error: %s", e.message)
            updateWidgetsFromCache(prefs)
            Result.success() // Don't retry aggressively, periodic will re-run
        } finally {
            client.close()
        }
    }

    private fun updateAllWidgets(data: WidgetDisplayData) {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)

        // Update mini widgets (1x1)
        val miniIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AuroraMiniWidgetProvider::class.java)
        )
        Timber.d("Updating %d mini widget(s)", miniIds.size)
        for (id in miniIds) {
            AuroraWidgetProvider.updateMiniWidget(context, appWidgetManager, id, data)
        }

        // Update small widgets (2x1)
        val smallIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AuroraWidgetProvider::class.java)
        )
        Timber.d("Updating %d small widget(s)", smallIds.size)
        for (id in smallIds) {
            AuroraWidgetProvider.updateSmallWidget(context, appWidgetManager, id, data)
        }

        // Update medium widgets (3x2)
        val mediumIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AuroraMediumWidgetProvider::class.java)
        )
        Timber.d("Updating %d medium widget(s)", mediumIds.size)
        for (id in mediumIds) {
            AuroraWidgetProvider.updateMediumWidget(context, appWidgetManager, id, data)
        }

        // Update large widgets (4x2)
        val largeIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, AuroraLargeWidgetProvider::class.java)
        )
        Timber.d("Updating %d large widget(s)", largeIds.size)
        for (id in largeIds) {
            AuroraWidgetProvider.updateLargeWidget(context, appWidgetManager, id, data)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private suspend fun updateWidgetsFromCache(prefs: UserPreferences) {
        val cached = prefs.getDashboardData()
        if (cached.lastUpdate > 0L) {
            val cachedDisplay = WidgetDisplayData(
                visibilityScore = cached.visibilityScore,
                auroraProbability = cached.auroraProbability,
                cloudCover = cached.cloudCover,
                kp = cached.kp,
                isCached = true
            )
            updateAllWidgets(cachedDisplay)
            val ageMin = (System.currentTimeMillis() - cached.lastUpdate) / 60_000
            Timber.d("Widgets updated from cache (age: %d min)", ageMin)
        } else {
            Timber.w("No cached data available — widgets show placeholder")
        }
    }

    companion object {
        private const val WORK_NAME = "aurora_periodic_update"

        /**
         * Schedules periodic aurora updates + immediate first fetch.
         * @param context Application context
         * @param intervalMinutes Refresh interval (default 30, min 15)
         */
        fun schedule(context: Context, intervalMinutes: Long = 30) {
            val interval = intervalMinutes.coerceAtLeast(15)
            val wm = WorkManager.getInstance(context)

            // Immediate one-time fetch so widgets show data right away
            wm.enqueue(OneTimeWorkRequestBuilder<AuroraUpdateWorker>().build())
            Timber.d("AuroraUpdateWorker: immediate fetch enqueued")

            // Periodic updates — REPLACE to pick up new interval
            val periodic = PeriodicWorkRequestBuilder<AuroraUpdateWorker>(
                interval, TimeUnit.MINUTES
            ).build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodic
            )
            Timber.d("AuroraUpdateWorker: periodic (%d min) scheduled", interval)
        }
    }
}
