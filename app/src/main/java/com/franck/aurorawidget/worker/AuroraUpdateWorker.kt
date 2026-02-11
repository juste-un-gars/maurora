/**
 * @file AuroraUpdateWorker.kt
 * @description Periodic worker that fetches aurora + weather data and updates all widgets.
 */
package com.franck.aurorawidget.worker

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.franck.aurorawidget.data.calculator.AuroraCalculator
import com.franck.aurorawidget.data.preferences.UserPreferences
import com.franck.aurorawidget.data.remote.HttpClientFactory
import com.franck.aurorawidget.data.remote.NoaaRepository
import com.franck.aurorawidget.data.remote.WeatherRepository
import com.franck.aurorawidget.widget.AuroraMediumWidgetProvider
import com.franck.aurorawidget.widget.AuroraWidgetProvider
import com.franck.aurorawidget.widget.WidgetDisplayData
import timber.log.Timber
import java.util.concurrent.TimeUnit

class AuroraUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("AuroraUpdateWorker started")
        val startTime = System.currentTimeMillis()

        val client = HttpClientFactory.create()
        return try {
            val noaaRepo = NoaaRepository(client)
            val weatherRepo = WeatherRepository(client)

            // Read user location from DataStore
            val prefs = UserPreferences(applicationContext)
            val settings = prefs.getSettings()
            val latitude = settings.latitude
            val longitude = settings.longitude

            // Fetch all data (aurora is required, weather/kp are optional)
            val ovationResult = noaaRepo.fetchOvationData()
            val ovationData = ovationResult.getOrElse { e ->
                Timber.e(e, "Worker failed to fetch OVATION data")
                return Result.retry()
            }

            val auroraProbability = AuroraCalculator.getAuroraProbability(
                ovationData, latitude, longitude
            )

            val weatherData = weatherRepo.fetchWeather(latitude, longitude).getOrNull()
            val kpData = noaaRepo.fetchKpIndex().getOrNull()

            // Compute combined visibility score if weather available
            val visibilityScore = weatherData?.let {
                AuroraCalculator.computeVisibilityScore(auroraProbability, it)
            }

            val displayData = WidgetDisplayData(
                visibilityScore = visibilityScore,
                auroraProbability = auroraProbability,
                cloudCover = weatherData?.cloudCover,
                kp = kpData?.value
            )

            updateAllWidgets(displayData)

            val duration = System.currentTimeMillis() - startTime
            Timber.d(
                "Worker done in %dms: aurora=%.1f%%, visibility=%s, cloud=%s, kp=%s",
                duration, auroraProbability,
                visibilityScore?.let { "%.1f%%".format(it) } ?: "N/A",
                weatherData?.cloudCover?.toString() ?: "N/A",
                kpData?.value?.let { "%.1f".format(it) } ?: "N/A"
            )
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "AuroraUpdateWorker unexpected error")
            Result.retry()
        } finally {
            client.close()
        }
    }

    private fun updateAllWidgets(data: WidgetDisplayData) {
        val context = applicationContext
        val appWidgetManager = AppWidgetManager.getInstance(context)

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
