/**
 * @file AuroraWidgetProvider.kt
 * @description AppWidgetProvider for the Aurora home screen widgets (small 2x1 + medium 3x2).
 */
package com.franck.aurorawidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.franck.aurorawidget.MainActivity
import com.franck.aurorawidget.R
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data holder for all values displayed on the widget.
 */
data class WidgetDisplayData(
    val visibilityScore: Double? = null,
    val auroraProbability: Double? = null,
    val cloudCover: Int? = null,
    val kp: Double? = null
)

/**
 * Provides the small (2x1) Aurora widget.
 * Displays aurora probability percentage with color coding.
 */
class AuroraWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("Small widget onUpdate: %d widget(s)", appWidgetIds.size)
        for (widgetId in appWidgetIds) {
            updateSmallWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        fun updateSmallWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            data: WidgetDisplayData = WidgetDisplayData()
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_aurora_small)
            val score = data.visibilityScore ?: data.auroraProbability

            if (score != null) {
                val pct = score.toInt()
                views.setTextViewText(R.id.tv_probability, "$pct%")
                views.setTextColor(R.id.tv_probability, getColorForScore(context, pct))
            } else {
                views.setTextViewText(R.id.tv_probability, "--")
                views.setTextColor(R.id.tv_probability, context.getColor(R.color.widget_text))
            }

            views.setTextViewText(R.id.tv_last_update, timeFormat.format(Date()))
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent(context))
            appWidgetManager.updateAppWidget(widgetId, views)
            Timber.d("Small widget %d updated: score=%s", widgetId, score?.let { "%.1f%%".format(it) } ?: "null")
        }

        fun updateMediumWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            data: WidgetDisplayData = WidgetDisplayData()
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_aurora_medium)
            val score = data.visibilityScore

            // Visibility score (big center number)
            if (score != null) {
                val pct = score.toInt()
                views.setTextViewText(R.id.tv_visibility_score, "$pct%")
                views.setTextColor(R.id.tv_visibility_score, getColorForScore(context, pct))
            } else {
                views.setTextViewText(R.id.tv_visibility_score, "--")
                views.setTextColor(R.id.tv_visibility_score, context.getColor(R.color.widget_text))
            }

            // Cloud cover
            val cloudText = data.cloudCover?.let { "☁ $it%" } ?: "☁ --"
            views.setTextViewText(R.id.tv_cloud_cover, cloudText)

            // Kp index
            val kpText = data.kp?.let { "Kp %.1f".format(it) } ?: "Kp --"
            views.setTextViewText(R.id.tv_kp, kpText)

            // Timestamp + tap action
            views.setTextViewText(R.id.tv_last_update, timeFormat.format(Date()))
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent(context))

            appWidgetManager.updateAppWidget(widgetId, views)
            Timber.d("Medium widget %d updated: score=%s, cloud=%s, kp=%s",
                widgetId,
                score?.let { "%.1f%%".format(it) } ?: "null",
                data.cloudCover?.toString() ?: "null",
                data.kp?.let { "%.1f".format(it) } ?: "null"
            )
        }

        /** Creates a PendingIntent to open the config activity on widget tap. */
        private fun configPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        /** Returns the color for a given visibility score (0-100). */
        internal fun getColorForScore(context: Context, score: Int): Int {
            val colorRes = when {
                score < 5 -> R.color.aurora_none
                score < 20 -> R.color.aurora_unlikely
                score < 50 -> R.color.aurora_possible
                score < 80 -> R.color.aurora_likely
                else -> R.color.aurora_go_outside
            }
            return context.getColor(colorRes)
        }
    }
}

/**
 * Provides the medium (3x2) Aurora widget.
 * Displays visibility score, cloud cover, Kp index, and last update.
 */
class AuroraMediumWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("Medium widget onUpdate: %d widget(s)", appWidgetIds.size)
        for (widgetId in appWidgetIds) {
            AuroraWidgetProvider.updateMediumWidget(context, appWidgetManager, widgetId)
        }
    }
}
