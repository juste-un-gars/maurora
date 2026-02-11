/**
 * @file WeatherData.kt
 * @description Data models for Open-Meteo weather API response.
 */
package com.franck.aurorawidget.data.model

import com.franck.aurorawidget.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Raw JSON response from Open-Meteo forecast endpoint.
 * Only the fields we need are mapped; unknown keys are ignored.
 */
@Serializable
data class WeatherResponse(
    val current: CurrentWeather,
    val daily: DailyWeather
)

@Serializable
data class CurrentWeather(
    @SerialName("cloud_cover") val cloudCover: Int,
    @SerialName("temperature_2m") val temperature: Double = 0.0,
    @SerialName("apparent_temperature") val apparentTemperature: Double = 0.0,
    @SerialName("weather_code") val weatherCode: Int = 0,
    @SerialName("is_day") val isDay: Int = 1,
    val time: String
)

@Serializable
data class DailyWeather(
    val sunrise: List<String>,
    val sunset: List<String>
)

/**
 * Processed weather data ready for use in score calculation.
 * @property cloudCover Cloud cover percentage (0-100)
 * @property sunrise Today's sunrise time (ISO 8601 local time, e.g. "2026-02-11T08:15")
 * @property sunset Today's sunset time (ISO 8601 local time, e.g. "2026-02-11T17:30")
 * @property fetchTime Time of the current weather observation (ISO 8601)
 */
data class WeatherData(
    val cloudCover: Int,
    val sunrise: String,
    val sunset: String,
    val fetchTime: String,
    val temperature: Double = 0.0,
    val feelsLike: Double = 0.0,
    val weatherCode: Int = 0,
    val isDay: Boolean = true
)

/** Converts API response to domain model, extracting today's sunrise/sunset. */
fun WeatherResponse.toWeatherData(): WeatherData = WeatherData(
    cloudCover = current.cloudCover,
    sunrise = daily.sunrise.firstOrNull().orEmpty(),
    sunset = daily.sunset.firstOrNull().orEmpty(),
    fetchTime = current.time,
    temperature = current.temperature,
    feelsLike = current.apparentTemperature,
    weatherCode = current.weatherCode,
    isDay = current.isDay == 1
)

/**
 * Raw JSON response from Open-Meteo with hourly cloud cover for multi-day forecast.
 */
@Serializable
data class WeatherForecastResponse(
    val hourly: HourlyWeather,
    val daily: DailyForecastWeather
)

@Serializable
data class HourlyWeather(
    val time: List<String>,
    @SerialName("cloud_cover") val cloudCover: List<Int>,
    @SerialName("temperature_2m") val temperature: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList()
)

@Serializable
data class DailyForecastWeather(
    val time: List<String> = emptyList(),
    val sunrise: List<String> = emptyList(),
    val sunset: List<String> = emptyList(),
    @SerialName("temperature_2m_max") val temperatureMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitationProbabilityMax: List<Int> = emptyList()
)

/**
 * Processed multi-day forecast with cloud, temperature, and weather data.
 * @property dailyCloudCover List of daily average cloud cover (0-100), one per day
 * @property hourlyCloudCover All hourly cloud values (for detailed chart)
 * @property hourlyTimes ISO 8601 times for each hourly slot
 * @property hourlyTemperatures Hourly temperatures
 * @property hourlyWeatherCodes WMO weather codes per hour
 * @property dailyDates Date strings for each day
 * @property dailyHighs Daily max temperature
 * @property dailyLows Daily min temperature
 * @property dailyWeatherCodes WMO code per day
 * @property dailyPrecipProbability Max precipitation probability per day (0-100)
 */
data class CloudForecast(
    val dailyCloudCover: List<Int>,
    val hourlyCloudCover: List<Int>,
    val hourlyTimes: List<String> = emptyList(),
    val hourlyTemperatures: List<Double> = emptyList(),
    val hourlyWeatherCodes: List<Int> = emptyList(),
    val dailyDates: List<String> = emptyList(),
    val dailyHighs: List<Double> = emptyList(),
    val dailyLows: List<Double> = emptyList(),
    val dailyWeatherCodes: List<Int> = emptyList(),
    val dailyPrecipProbability: List<Int> = emptyList()
)

/** Converts forecast response to domain model with daily averages. */
fun WeatherForecastResponse.toCloudForecast(): CloudForecast {
    val hourlyClouds = hourly.cloudCover
    val dailyAvg = hourlyClouds.chunked(24).map { dayHours ->
        dayHours.average().toInt()
    }
    return CloudForecast(
        dailyCloudCover = dailyAvg,
        hourlyCloudCover = hourlyClouds,
        hourlyTimes = hourly.time,
        hourlyTemperatures = hourly.temperature,
        hourlyWeatherCodes = hourly.weatherCode,
        dailyDates = daily.time,
        dailyHighs = daily.temperatureMax,
        dailyLows = daily.temperatureMin,
        dailyWeatherCodes = daily.weatherCode,
        dailyPrecipProbability = daily.precipitationProbabilityMax
    )
}

/**
 * Maps WMO weather code to (@StringRes resId, emoji) pair.
 * @param code WMO weather interpretation code (0-99)
 * @param isDay true for daytime icons, false for nighttime
 * @return Pair of (@StringRes resource ID, emoji string)
 */
fun wmoCodeToDescription(code: Int, isDay: Boolean = true): Pair<Int, String> = when (code) {
    0 -> R.string.wmo_clear_sky to if (isDay) "☀\uFE0F" else "\uD83C\uDF19"
    1 -> R.string.wmo_mainly_clear to if (isDay) "\uD83C\uDF24\uFE0F" else "\uD83C\uDF19"
    2 -> R.string.wmo_partly_cloudy to if (isDay) "⛅" else "☁\uFE0F"
    3 -> R.string.wmo_overcast to "☁\uFE0F"
    45, 48 -> R.string.wmo_fog to "\uD83C\uDF2B\uFE0F"
    51 -> R.string.wmo_light_drizzle to if (isDay) "\uD83C\uDF26\uFE0F" else "\uD83C\uDF27\uFE0F"
    53 -> R.string.wmo_drizzle to if (isDay) "\uD83C\uDF26\uFE0F" else "\uD83C\uDF27\uFE0F"
    55 -> R.string.wmo_dense_drizzle to "\uD83C\uDF27\uFE0F"
    61 -> R.string.wmo_light_rain to "\uD83C\uDF27\uFE0F"
    63 -> R.string.wmo_rain to "\uD83C\uDF27\uFE0F"
    65 -> R.string.wmo_heavy_rain to "\uD83C\uDF27\uFE0F"
    71 -> R.string.wmo_light_snow to "\uD83C\uDF28\uFE0F"
    73 -> R.string.wmo_snow to "\uD83C\uDF28\uFE0F"
    75 -> R.string.wmo_heavy_snow to "\uD83C\uDF28\uFE0F"
    77 -> R.string.wmo_snow_grains to "\uD83C\uDF28\uFE0F"
    80, 81, 82 -> R.string.wmo_rain_showers to if (isDay) "\uD83C\uDF26\uFE0F" else "\uD83C\uDF27\uFE0F"
    85, 86 -> R.string.wmo_snow_showers to "\uD83C\uDF28\uFE0F"
    95 -> R.string.wmo_thunderstorm to "⛈\uFE0F"
    96, 99 -> R.string.wmo_thunderstorm_hail to "⛈\uFE0F"
    else -> R.string.wmo_unknown to "❓"
}
