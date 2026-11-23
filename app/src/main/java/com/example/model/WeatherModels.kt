package com.example.model

data class DailyForecast(
    val dateLabel: String,
    val precipitationSumMm: Double,
    val conditionLabel: String
)

data class WeatherContext(
    val latitude: Double = RegionCatalog.defaultRegion.centerLat,
    val longitude: Double = RegionCatalog.defaultRegion.centerLon,
    val temperatureC: Double? = null,
    val rainfallMm: Double? = null,
    val windSpeedKmh: Double? = null,
    val weatherCode: Int? = null,
    val conditionLabel: String = "Unavailable",
    val severeWeatherWarning: String? = null,
    val dailyForecast: List<DailyForecast> = emptyList(),
    val source: String = "Open-Meteo",
    val observedAtMillis: Long = 0L,
    val isAvailable: Boolean = false,
    val errorMessage: String? = null
) {
    val isStale: Boolean
        get() = !isAvailable || observedAtMillis <= 0L ||
            (System.currentTimeMillis() - observedAtMillis) > 45 * 60 * 1000L
}
