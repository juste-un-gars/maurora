package com.franck.aurorawidget.data.calculator

import com.franck.aurorawidget.data.model.OvationData
import com.franck.aurorawidget.data.model.OvationPoint
import com.franck.aurorawidget.data.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class AuroraCalculatorTest {

    private fun makeData(vararg points: Triple<Int, Int, Int>): OvationData {
        return OvationData(
            observationTime = "2026-01-01T00:00:00Z",
            forecastTime = "2026-01-01T01:00:00Z",
            points = points.map { (lon, lat, aurora) -> OvationPoint(lon, lat, aurora) }
        )
    }

    @Test
    fun `exact grid point returns exact value`() {
        val data = makeData(Triple(10, 60, 42))
        val result = AuroraCalculator.getAuroraProbability(data, 60.0, 10.0)
        assertEquals(42.0, result, 0.01)
    }

    @Test
    fun `bilinear interpolation between 4 points`() {
        // 4 corners of a 1-degree cell
        val data = makeData(
            Triple(10, 60, 0),
            Triple(11, 60, 100),
            Triple(10, 61, 0),
            Triple(11, 61, 100)
        )
        // Midpoint in longitude → should be ~50
        val result = AuroraCalculator.getAuroraProbability(data, 60.0, 10.5)
        assertEquals(50.0, result, 0.01)
    }

    @Test
    fun `negative longitude is normalized`() {
        // -5 degrees → 355 degrees in OVATION grid
        val data = makeData(Triple(355, 50, 80))
        val result = AuroraCalculator.getAuroraProbability(data, 50.0, -5.0)
        assertEquals(80.0, result, 0.01)
    }

    @Test
    fun `normalizeLongitude converts correctly`() {
        assertEquals(0.0, AuroraCalculator.normalizeLongitude(0.0), 0.01)
        assertEquals(355.0, AuroraCalculator.normalizeLongitude(-5.0), 0.01)
        assertEquals(180.0, AuroraCalculator.normalizeLongitude(180.0), 0.01)
        assertEquals(180.0, AuroraCalculator.normalizeLongitude(-180.0), 0.01)
        assertEquals(1.0, AuroraCalculator.normalizeLongitude(361.0), 0.01)
    }

    @Test
    fun `empty data returns zero`() {
        val data = OvationData("", "", emptyList())
        assertEquals(0.0, AuroraCalculator.getAuroraProbability(data, 50.0, 10.0), 0.01)
    }

    @Test
    fun `result is clamped to 0-100`() {
        val data = makeData(Triple(10, 60, 100))
        val result = AuroraCalculator.getAuroraProbability(data, 60.0, 10.0)
        assert(result in 0.0..100.0)
    }

    // --- Night factor tests ---

    private fun weather(cloud: Int = 0, sunrise: String = "2026-02-11T08:00", sunset: String = "2026-02-11T17:30") =
        WeatherData(cloudCover = cloud, sunrise = sunrise, sunset = sunset, fetchTime = "2026-02-11T12:00")

    @Test
    fun `night factor is 0 during daytime`() {
        val noon = LocalDateTime.of(2026, 2, 11, 12, 0)
        val factor = AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", noon)
        assertEquals(0.0, factor, 0.01)
    }

    @Test
    fun `night factor is 1 well after sunset`() {
        val lateNight = LocalDateTime.of(2026, 2, 11, 23, 0)
        val factor = AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", lateNight)
        assertEquals(1.0, factor, 0.01)
    }

    @Test
    fun `night factor is 1 well before sunrise`() {
        val earlyMorning = LocalDateTime.of(2026, 2, 11, 3, 0)
        val factor = AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", earlyMorning)
        assertEquals(1.0, factor, 0.01)
    }

    @Test
    fun `night factor transitions at sunset`() {
        // Exactly at sunset → 0.0
        val atSunset = LocalDateTime.of(2026, 2, 11, 17, 30)
        assertEquals(0.0, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", atSunset), 0.01)

        // 15 min after sunset → 0.5 (halfway through 30-min twilight)
        val halfTwilight = LocalDateTime.of(2026, 2, 11, 17, 45)
        assertEquals(0.5, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", halfTwilight), 0.01)

        // 30 min after sunset → 1.0
        val endTwilight = LocalDateTime.of(2026, 2, 11, 18, 0)
        assertEquals(1.0, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", endTwilight), 0.01)
    }

    @Test
    fun `night factor transitions at sunrise`() {
        // 30 min before sunrise → 1.0
        val beforeSunrise = LocalDateTime.of(2026, 2, 11, 7, 30)
        assertEquals(1.0, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", beforeSunrise), 0.01)

        // 15 min before sunrise → 0.5
        val halfTwilight = LocalDateTime.of(2026, 2, 11, 7, 45)
        assertEquals(0.5, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", halfTwilight), 0.01)

        // Exactly at sunrise → 0.0
        val atSunrise = LocalDateTime.of(2026, 2, 11, 8, 0)
        assertEquals(0.0, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "2026-02-11T17:30", atSunrise), 0.01)
    }

    @Test
    fun `night factor returns 0_5 for blank sunrise or sunset`() {
        val now = LocalDateTime.of(2026, 2, 11, 12, 0)
        assertEquals(0.5, AuroraCalculator.computeNightFactor("", "2026-02-11T17:30", now), 0.01)
        assertEquals(0.5, AuroraCalculator.computeNightFactor("2026-02-11T08:00", "", now), 0.01)
    }

    // --- Combined visibility score tests ---

    @Test
    fun `visibility score combines all factors`() {
        // Night (23:00), clear sky (0% clouds), aurora 80%
        val night = LocalDateTime.of(2026, 2, 11, 23, 0)
        val score = AuroraCalculator.computeVisibilityScore(80.0, weather(cloud = 0), night)
        assertEquals(80.0, score, 0.01) // 80 * 1.0 * 1.0
    }

    @Test
    fun `visibility score is zero during daytime`() {
        val noon = LocalDateTime.of(2026, 2, 11, 12, 0)
        val score = AuroraCalculator.computeVisibilityScore(80.0, weather(cloud = 0), noon)
        assertEquals(0.0, score, 0.01) // 80 * 1.0 * 0.0
    }

    @Test
    fun `cloud cover reduces visibility score`() {
        val night = LocalDateTime.of(2026, 2, 11, 23, 0)
        val score = AuroraCalculator.computeVisibilityScore(80.0, weather(cloud = 50), night)
        assertEquals(40.0, score, 0.01) // 80 * 0.5 * 1.0
    }

    @Test
    fun `full cloud cover gives zero score`() {
        val night = LocalDateTime.of(2026, 2, 11, 23, 0)
        val score = AuroraCalculator.computeVisibilityScore(80.0, weather(cloud = 100), night)
        assertEquals(0.0, score, 0.01) // 80 * 0.0 * 1.0
    }
}
