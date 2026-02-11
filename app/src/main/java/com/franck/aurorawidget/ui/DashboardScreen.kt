/**
 * @file DashboardScreen.kt
 * @description Main dashboard screen showing aurora visibility data.
 */
package com.franck.aurorawidget.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.franck.aurorawidget.R
import com.franck.aurorawidget.data.preferences.DashboardData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val STALE_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour

/** Aurora score color coding matching colors.xml values. */
private fun scoreColor(score: Double): Color = when {
    score < 5 -> Color(0xFF9E9E9E)   // Gray
    score < 20 -> Color(0xFF4CAF50)  // Green
    score < 50 -> Color(0xFFFFEB3B)  // Yellow
    score < 80 -> Color(0xFFFF9800)  // Orange
    else -> Color(0xFFF44336)        // Red
}

private fun scoreLabelRes(score: Double): Int = when {
    score < 5 -> R.string.score_nothing
    score < 20 -> R.string.score_unlikely
    score < 50 -> R.string.score_possible
    score < 80 -> R.string.score_likely
    else -> R.string.score_go_outside
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    data: DashboardData,
    locationName: String,
    onNavigateToSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dashboard_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.dashboard_settings))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.dashboard_refresh))
            }
        }
    ) { padding ->
        if (data.lastUpdate == 0L) {
            NoDataState(modifier = Modifier.padding(padding))
        } else {
            DashboardContent(data, locationName, Modifier.padding(padding))
        }
    }
}

@Composable
private fun NoDataState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.dashboard_no_data),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_no_data_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    locationName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Offline banner
        val ageMs = System.currentTimeMillis() - data.lastUpdate
        val isStale = ageMs > STALE_THRESHOLD_MS
        if (isStale) {
            val ageText = formatAge(ageMs)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Color(0xFFFF9800).copy(alpha = 0.15f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.dashboard_cached_data, ageText),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFF9800)
                )
            }
        }

        // Location
        Text(
            locationName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Main score
        val displayScore = data.visibilityScore ?: data.auroraProbability
        val color = scoreColor(displayScore)

        Box(
            modifier = Modifier
                .size(160.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${displayScore.toInt()}%",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    stringResource(if (data.visibilityScore != null) R.string.dashboard_visibility else R.string.dashboard_aurora),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            stringResource(scoreLabelRes(displayScore)),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = color
        )

        // Weather section
        WeatherSection(data)

        // Detail cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(stringResource(R.string.dashboard_aurora), "%.0f%%".format(data.auroraProbability), Modifier.weight(1f))
            InfoCard(stringResource(R.string.dashboard_kp), data.kp?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(stringResource(R.string.dashboard_clouds), data.cloudCover?.let { "$it%" } ?: "—", Modifier.weight(1f))
            InfoCard(
                stringResource(R.string.dashboard_sun),
                if (data.sunrise.isNotBlank()) "${formatTime(data.sunrise)} / ${formatTime(data.sunset)}" else "—",
                Modifier.weight(1f)
            )
        }

        // Kp Forecast chart
        val kpValues = parseKpForecast(data.kpForecastCsv)
        if (kpValues.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.dashboard_kp_forecast),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            KpForecastChart(kpValues, modifier = Modifier.fillMaxWidth().height(120.dp))
        }

        // 3-Day Outlook
        val dailyClouds = parseCloudForecast(data.cloudForecastDailyCsv)
        if (dailyClouds.isNotEmpty() && kpValues.isNotEmpty()) {
            Text(
                stringResource(R.string.dashboard_3day_outlook),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val dailyKpMax = kpValues.chunked(8).map { chunk ->
                chunk.maxOrNull() ?: 0.0
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (day in 0 until minOf(dailyClouds.size, dailyKpMax.size, 3)) {
                    DayOutlookCard(
                        dayLabel = dayLabel(day),
                        maxKp = dailyKpMax[day],
                        avgCloud = dailyClouds[day],
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Last update
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.dashboard_updated, formatTimestamp(data.lastUpdate)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Extract HH:mm from ISO 8601 datetime string. */
private fun formatTime(iso: String): String {
    if (iso.length < 16) return iso
    return iso.substring(11, 16)
}

/** Format epoch millis as "HH:mm" today or "MMM dd HH:mm" if older. */
private fun formatTimestamp(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

/** Format age in human-readable form. */
private fun formatAge(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "${minutes}min"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}min"
        else -> "${minutes / 1440}d"
    }
}

/** Parse CSV string to Kp values list. */
private fun parseKpForecast(csv: String): List<Double> {
    if (csv.isBlank()) return emptyList()
    return csv.split(",").mapNotNull { it.trim().toDoubleOrNull() }
}

/** Parse CSV string to daily cloud cover values. */
private fun parseCloudForecast(csv: String): List<Int> {
    if (csv.isBlank()) return emptyList()
    return csv.split(",").mapNotNull { it.trim().toIntOrNull() }
}

/** Returns day label: "Today", "Tomorrow", or day name. */
@Composable
private fun dayLabel(dayOffset: Int): String {
    if (dayOffset == 0) return stringResource(R.string.day_today)
    if (dayOffset == 1) return stringResource(R.string.day_tomorrow)
    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
    return SimpleDateFormat("EEE", Locale.getDefault()).format(cal.time)
}

/** Kp bar color matching widget scheme. */
private fun kpBarColor(kp: Double): Color = when {
    kp < 4.0 -> Color(0xFF4CAF50)  // Green — quiet
    kp < 5.0 -> Color(0xFFFFEB3B)  // Yellow — unsettled
    kp < 6.0 -> Color(0xFFFF9800)  // Orange — minor storm
    else -> Color(0xFFF44336)       // Red — storm
}

/** Compose bar chart for Kp forecast (one bar per 3-hour slot). */
@Composable
private fun KpForecastChart(kpValues: List<Double>, modifier: Modifier = Modifier) {
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            drawKpBars(kpValues, textColor)
        }
    }
}

private fun DrawScope.drawKpBars(kpValues: List<Double>, textColor: Color) {
    val maxKp = 9f
    val barCount = kpValues.size
    if (barCount == 0) return

    val gap = 2.dp.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount

    // Threshold line at Kp 5
    val thresholdY = size.height * (1f - 5f / maxKp)
    drawLine(
        color = textColor.copy(alpha = 0.3f),
        start = Offset(0f, thresholdY),
        end = Offset(size.width, thresholdY),
        strokeWidth = 1.dp.toPx()
    )

    // "Kp 5" label
    drawContext.canvas.nativeCanvas.drawText(
        "Kp 5",
        4.dp.toPx(),
        thresholdY - 2.dp.toPx(),
        android.graphics.Paint().apply {
            color = textColor.copy(alpha = 0.5f).hashCode()
            textSize = 9.sp.toPx()
        }
    )

    // Bars
    for (i in kpValues.indices) {
        val kp = kpValues[i].coerceIn(0.0, maxKp.toDouble()).toFloat()
        val barHeight = (kp / maxKp) * size.height
        val left = i * (barWidth + gap)
        val top = size.height - barHeight

        drawRect(
            color = kpBarColor(kpValues[i]),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight)
        )
    }

    // Day separator lines (every 8 bars = 1 day)
    for (day in 1..(barCount / 8).coerceAtMost(2)) {
        val x = day * 8 * (barWidth + gap) - gap / 2
        drawLine(
            color = textColor.copy(alpha = 0.2f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}

/** Card showing one day's max Kp and average cloud cover. */
@Composable
private fun DayOutlookCard(
    dayLabel: String,
    maxKp: Double,
    avgCloud: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                dayLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.dashboard_kp_format, maxKp),
                style = MaterialTheme.typography.bodySmall,
                color = kpBarColor(maxKp)
            )
            Text(
                stringResource(R.string.dashboard_cloud_format, avgCloud),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
