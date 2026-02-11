package com.franck.aurorawidget.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherDataTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parse Open-Meteo response correctly`() {
        val raw = """
        {
          "latitude": 48.86,
          "longitude": 2.36,
          "current_units": { "cloud_cover": "%" },
          "current": {
            "time": "2026-02-11T14:00",
            "interval": 900,
            "cloud_cover": 75
          },
          "daily_units": { "sunrise": "iso8601", "sunset": "iso8601" },
          "daily": {
            "time": ["2026-02-11"],
            "sunrise": ["2026-02-11T08:15"],
            "sunset": ["2026-02-11T17:45"]
          }
        }
        """.trimIndent()

        val response = json.decodeFromString<WeatherResponse>(raw)
        val data = response.toWeatherData()

        assertEquals(75, data.cloudCover)
        assertEquals("2026-02-11T08:15", data.sunrise)
        assertEquals("2026-02-11T17:45", data.sunset)
        assertEquals("2026-02-11T14:00", data.fetchTime)
    }

    @Test
    fun `toWeatherData handles empty daily arrays`() {
        val response = WeatherResponse(
            current = CurrentWeather(cloudCover = 50, time = "2026-02-11T12:00"),
            daily = DailyWeather(sunrise = emptyList(), sunset = emptyList())
        )
        val data = response.toWeatherData()

        assertEquals(50, data.cloudCover)
        assertEquals("", data.sunrise)
        assertEquals("", data.sunset)
    }
}
