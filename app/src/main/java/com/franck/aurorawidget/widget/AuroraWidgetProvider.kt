/**
 * @file AuroraWidgetProvider.kt
 * @description AppWidgetProvider for the Aurora home screen widgets (small, medium, large).
 */
package com.franck.aurorawidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import com.franck.aurorawidget.MainActivity
import com.franck.aurorawidget.R
import com.franck.aurorawidget.data.model.KpForecast
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
    val kp: Double? = null,
    val kpForecast: KpForecast? = null,
    val isCached: Boolean = false
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

            val timeText = if (data.isCached) "⏳ ${timeFormat.format(Date())}" else timeFormat.format(Date())
            views.setTextViewText(R.id.tv_last_update, timeText)
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent(context))
            appWidgetManager.updateAppWidget(widgetId, views)
            Timber.d("Small widget %d updated: score=%s cached=%s", widgetId, score?.let { "%.1f%%".format(it) } ?: "null", data.isCached)
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
            val timeText = if (data.isCached) "⏳ ${timeFormat.format(Date())}" else timeFormat.format(Date())
            views.setTextViewText(R.id.tv_last_update, timeText)
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

        fun updateLargeWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            widgetId: Int,
            data: WidgetDisplayData = WidgetDisplayData()
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_aurora_large)
            val score = data.visibilityScore

            // Visibility score
            if (score != null) {
                val pct = score.toInt()
                views.setTextViewText(R.id.tv_visibility_score, "$pct%")
                views.setTextColor(R.id.tv_visibility_score, getColorForScore(context, pct))
            } else {
                views.setTextViewText(R.id.tv_visibility_score, "--")
                views.setTextColor(R.id.tv_visibility_score, context.getColor(R.color.widget_text))
            }

            // Cloud cover + Kp
            views.setTextViewText(R.id.tv_cloud_cover, data.cloudCover?.let { "☁ $it%" } ?: "☁ --")
            views.setTextViewText(R.id.tv_kp, data.kp?.let { "Kp %.1f".format(it) } ?: "Kp --")

            // Kp forecast graph
            val forecast = data.kpForecast
            if (forecast != null && forecast.points.isNotEmpty()) {
                val bitmap = renderKpGraph(context, forecast)
                views.setImageViewBitmap(R.id.iv_kp_graph, bitmap)
            }

            val timeText = if (data.isCached) "⏳ ${timeFormat.format(Date())}" else timeFormat.format(Date())
            views.setTextViewText(R.id.tv_last_update, timeText)
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent(context))
            appWidgetManager.updateAppWidget(widgetId, views)
            Timber.d("Large widget %d updated: score=%s, forecast=%d pts",
                widgetId,
                score?.let { "%.1f%%".format(it) } ?: "null",
                forecast?.points?.size ?: 0
            )
        }

        /**
         * Renders a Kp bar chart as a Bitmap for the widget ImageView.
         * Each bar = one 3-hour Kp forecast point, colored by intensity.
         */
        internal fun renderKpGraph(context: Context, forecast: KpForecast): Bitmap {
            val width = 400
            val height = 200
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val points = forecast.points
            if (points.isEmpty()) return bitmap

            val barCount = points.size
            val gap = 2f
            val barWidth = (width.toFloat() - gap * (barCount - 1)) / barCount
            val maxKp = 9f // Kp scale max

            val paint = Paint().apply { isAntiAlias = true }
            val textPaint = Paint().apply {
                isAntiAlias = true
                color = context.getColor(R.color.widget_text_secondary)
                textSize = 18f
                textAlign = Paint.Align.CENTER
            }

            // Draw threshold line at Kp 5 (storm level)
            val thresholdY = height * (1f - 5f / maxKp)
            paint.color = 0x40FFFFFF // semi-transparent white
            paint.strokeWidth = 1f
            canvas.drawLine(0f, thresholdY, width.toFloat(), thresholdY, paint)

            // Draw bars
            for (i in points.indices) {
                val kp = points[i].kp.toFloat().coerceIn(0f, maxKp)
                val barHeight = (kp / maxKp) * height
                val left = i * (barWidth + gap)
                val top = height - barHeight

                paint.color = getKpBarColor(kp)
                canvas.drawRect(left, top, left + barWidth, height.toFloat(), paint)
            }

            // Day labels (every 8 bars = 1 day)
            for (day in 0 until (barCount / 8).coerceAtMost(3)) {
                val x = (day * 8 + 4) * (barWidth + gap)
                canvas.drawText("D+${day + 1}", x, 16f, textPaint)
            }

            return bitmap
        }

        /** Returns bar color based on Kp value. */
        private fun getKpBarColor(kp: Float): Int = when {
            kp < 4f -> 0xFF4CAF50.toInt()  // Green — quiet
            kp < 5f -> 0xFFFFEB3B.toInt()  // Yellow — unsettled
            kp < 6f -> 0xFFFF9800.toInt()  // Orange — minor storm
            else -> 0xFFF44336.toInt()      // Red — storm
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

/**
 * Provides the large (4x2) Aurora widget.
 * Displays visibility score, details, and a Kp 3-day forecast bar chart.
 */
class AuroraLargeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Timber.d("Large widget onUpdate: %d widget(s)", appWidgetIds.size)
        for (widgetId in appWidgetIds) {
            AuroraWidgetProvider.updateLargeWidget(context, appWidgetManager, widgetId)
        }
    }
}
