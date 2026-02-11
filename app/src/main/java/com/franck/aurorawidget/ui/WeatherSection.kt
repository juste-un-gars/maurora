/**
 * @file WeatherSection.kt
 * @description Composables for the weather section on the dashboard.
 */
package com.franck.aurorawidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.franck.aurorawidget.R
import com.franck.aurorawidget.data.model.wmoCodeToDescription
import com.franck.aurorawidget.data.preferences.DashboardData
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Parsed hourly forecast entry
private data class HourlyEntry(val time: String, val temp: Double, val code: Int)

// Parsed daily forecast entry
private data class DailyEntry(
    val date: String,
    val high: Double,
    val low: Double,
    val code: Int,
    val precip: Int
)

/** Main weather section composable. */
@Composable
fun WeatherSection(data: DashboardData) {
    val hasWeather = data.temperature != null
    if (!hasWeather) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section title
        Text(
            stringResource(R.string.weather_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )

        // Current weather header
        CurrentWeatherHeader(data)

        // Hourly forecast
        val hourlyEntries = parseHourlyForecast(data.hourlyForecastCsv)
        if (hourlyEntries.isNotEmpty()) {
            HourlyForecastRow(hourlyEntries, data.isDay)
        }

        // Daily forecast
        val dailyEntries = parseDailyForecast(data.dailyForecastCsv)
        if (dailyEntries.isNotEmpty()) {
            DailyForecastList(dailyEntries)
        }
    }
}

@Composable
private fun CurrentWeatherHeader(data: DashboardData) {
    val temp = data.temperature ?: return
    val feelsLike = data.feelsLike ?: temp
    val code = data.weatherCode ?: 0
    val (descResId, emoji) = wmoCodeToDescription(code, data.isDay)
    val description = stringResource(descResId)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Temperature + emoji
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        "${temp.toInt()}°",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        emoji,
                        fontSize = 36.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text(
                        stringResource(R.string.weather_feels_like, feelsLike.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (data.highTemp != null && data.lowTemp != null) {
                        Text(
                            "  ·  " + stringResource(R.string.weather_high_low, data.highTemp.toInt(), data.lowTemp.toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastRow(entries: List<HourlyEntry>, isDay: Boolean) {
    // Filter to next 8 hours from current time
    val now = Calendar.getInstance()
    val currentHour = String.format(Locale.US, "%02d:%02d", now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
    val startIdx = entries.indexOfFirst { it.time >= currentHour }.coerceAtLeast(0)
    val upcoming = entries.drop(startIdx).take(8)
    if (upcoming.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(upcoming) { entry ->
                HourlyItem(entry, isDay)
            }
        }
    }
}

@Composable
private fun HourlyItem(entry: HourlyEntry, isDay: Boolean) {
    val (_, emoji) = wmoCodeToDescription(entry.code, isDay)
    Column(
        modifier = Modifier
            .width(56.dp)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            entry.time,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "${entry.temp.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun DailyForecastList(entries: List<DailyEntry>) {
    // Find global temp range for the bar visualization
    val globalMin = entries.minOf { it.low }
    val globalMax = entries.maxOf { it.high }
    val range = (globalMax - globalMin).coerceAtLeast(1.0)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            entries.forEachIndexed { index, entry ->
                DailyRow(entry, index, globalMin, range)
                if (index < entries.lastIndex) {
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DailyRow(
    entry: DailyEntry,
    dayIndex: Int,
    globalMin: Double,
    range: Double
) {
    val dayName = dayIndexToLabel(entry.date, dayIndex)
    val (_, emoji) = wmoCodeToDescription(entry.code, true)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Day name
        Text(
            dayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(44.dp)
        )

        // Emoji
        Text(emoji, fontSize = 18.sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)

        // Precip probability
        Text(
            if (entry.precip > 0) "${entry.precip}%" else "",
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF64B5F6),
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )

        Spacer(Modifier.width(8.dp))

        // Low temp
        Text(
            "${entry.low.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.End
        )

        Spacer(Modifier.width(6.dp))

        // Temperature range bar
        val lowFraction = ((entry.low - globalMin) / range).toFloat()
        val highFraction = ((entry.high - globalMin) / range).toFloat()
        TemperatureBar(
            lowFraction = lowFraction,
            highFraction = highFraction,
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
        )

        Spacer(Modifier.width(6.dp))

        // High temp
        Text(
            "${entry.high.toInt()}°",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(28.dp),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
private fun TemperatureBar(
    lowFraction: Float,
    highFraction: Float,
    modifier: Modifier = Modifier
) {
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val barColor = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(trackColor)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            if (lowFraction > 0f) {
                Spacer(Modifier.weight(lowFraction))
            }
            val barWeight = (highFraction - lowFraction).coerceAtLeast(0.01f)
            Box(
                modifier = Modifier
                    .weight(barWeight)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor)
            )
            val remainWeight = (1f - highFraction).coerceAtLeast(0.001f)
            Spacer(Modifier.weight(remainWeight))
        }
    }
}

/** Parse hourly forecast CSV: "HH:mm,temp,code;..." */
private fun parseHourlyForecast(csv: String): List<HourlyEntry> {
    if (csv.isBlank()) return emptyList()
    return csv.split(";").mapNotNull { entry ->
        val parts = entry.split(",")
        if (parts.size >= 3) {
            HourlyEntry(
                time = parts[0],
                temp = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                code = parts[2].toIntOrNull() ?: return@mapNotNull null
            )
        } else null
    }
}

/** Parse daily forecast CSV: "date,high,low,code,precip;..." */
private fun parseDailyForecast(csv: String): List<DailyEntry> {
    if (csv.isBlank()) return emptyList()
    return csv.split(";").mapNotNull { entry ->
        val parts = entry.split(",")
        if (parts.size >= 5) {
            DailyEntry(
                date = parts[0],
                high = parts[1].toDoubleOrNull() ?: return@mapNotNull null,
                low = parts[2].toDoubleOrNull() ?: return@mapNotNull null,
                code = parts[3].toIntOrNull() ?: return@mapNotNull null,
                precip = parts[4].toIntOrNull() ?: return@mapNotNull null
            )
        } else null
    }
}

/** Convert date string to day label. */
@Composable
private fun dayIndexToLabel(dateStr: String, index: Int): String {
    if (index == 0) return stringResource(R.string.day_today)
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdf.parse(dateStr) ?: return dateStr
        SimpleDateFormat("EEE", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        dateStr
    }
}
