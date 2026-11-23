package com.example.data

import android.util.Log
import com.example.model.DailyForecast
import com.example.model.WeatherContext
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class OpenMeteoResponse(
    val current: CurrentWeatherDto? = null,
    val daily: DailyWeatherDto? = null
)

@JsonClass(generateAdapter = true)
data class CurrentWeatherDto(
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    val precipitation: Double? = null,
    val rain: Double? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null
)

@JsonClass(generateAdapter = true)
data class DailyWeatherDto(
    val time: List<String>? = null,
    @Json(name = "precipitation_sum") val precipitationSum: List<Double>? = null,
    @Json(name = "weather_code") val weatherCode: List<Int>? = null
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,precipitation,rain,weather_code,wind_speed_10m",
        @Query("daily") daily: String = "precipitation_sum,weather_code",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3
    ): OpenMeteoResponse
}

class WeatherRepository {
    private val api: OpenMeteoApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    suspend fun fetchWeather(latitude: Double, longitude: Double): WeatherContext {
        return try {
            val response = api.getForecast(latitude = latitude, longitude = longitude)
            val current = response.current
            if (current == null) {
                WeatherContext(
                    latitude = latitude,
                    longitude = longitude,
                    isAvailable = false,
                    errorMessage = "Weather provider returned no current observations."
                )
            } else {
                val rain = current.rain ?: current.precipitation ?: 0.0
                val code = current.weatherCode ?: 0
                val condition = mapWmoCode(code)
                val severeWarning = when {
                    code in listOf(95, 96, 99) -> "Thunderstorm activity reported — elevated road hazard caution advised."
                    rain >= 15.0 -> "Heavy rainfall (${String.format("%.1f", rain)} mm) — waterlogging and low-visibility risk."
                    rain >= 5.0 -> "Active moderate rain (${String.format("%.1f", rain)} mm) — watch for slick surfaces and pooling."
                    else -> null
                }

                val forecasts = mutableListOf<DailyForecast>()
                val times = response.daily?.time.orEmpty()
                val sums = response.daily?.precipitationSum.orEmpty()
                val codes = response.daily?.weatherCode.orEmpty()
                for (i in times.indices) {
                    forecasts.add(
                        DailyForecast(
                            dateLabel = times[i],
                            precipitationSumMm = sums.getOrNull(i) ?: 0.0,
                            conditionLabel = mapWmoCode(codes.getOrNull(i) ?: 0)
                        )
                    )
                }

                WeatherContext(
                    latitude = latitude,
                    longitude = longitude,
                    temperatureC = current.temperature2m,
                    rainfallMm = rain,
                    windSpeedKmh = current.windSpeed10m,
                    weatherCode = code,
                    conditionLabel = condition,
                    severeWeatherWarning = severeWarning,
                    dailyForecast = forecasts,
                    source = "Open-Meteo",
                    observedAtMillis = System.currentTimeMillis(),
                    isAvailable = true
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WeatherRepo", "Failed to fetch Open-Meteo weather", e)
            WeatherContext(
                latitude = latitude,
                longitude = longitude,
                isAvailable = false,
                errorMessage = e.localizedMessage ?: "Unable to reach Open-Meteo weather service."
            )
        }
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear Sky"
        1, 2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Fog / Mist"
        51, 53, 55 -> "Drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy Rain"
        80, 81, 82 -> "Rain Showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Cloudy ($code)"
    }
}
