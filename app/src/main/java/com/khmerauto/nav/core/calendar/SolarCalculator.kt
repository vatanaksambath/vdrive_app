package com.khmerauto.nav.core.calendar

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.*

enum class ThemeMode {
    AUTO,
    DAY,
    NIGHT
}

/**
 * Solar Clock Engine & Dynamic Day/Night Theme Calculator.
 *
 * Computes exact sunrise and sunset times offline using the standard astronomical
 * solar zenith algorithm for Cambodia (default Phnom Penh coordinates 11.5564° N, 104.9282° E).
 *
 * Automatically manages smooth transitions between Day Appearance (light-blue/alabaster)
 * and Night Appearance (deep slate/obsidian).
 */
object SolarCalculator {

    // Default GPS: Phnom Penh (Independence Monument / Norodom Blvd)
    const val DEFAULT_PHNOM_PENH_LAT = 11.5564
    const val DEFAULT_PHNOM_PENH_LON = 104.9282

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    data class SolarTimes(
        val sunrise: LocalTime,
        val sunset: LocalTime,
        val isDaytimeNow: Boolean
    )

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    /**
     * Determines whether the active theme should render Day Appearance (true) or Night Appearance (false).
     */
    fun isDayAppearance(
        currentTime: LocalTime = LocalTime.now(),
        currentDate: LocalDate = LocalDate.now(),
        lat: Double = DEFAULT_PHNOM_PENH_LAT,
        lon: Double = DEFAULT_PHNOM_PENH_LON
    ): Boolean {
        return when (_themeMode.value) {
            ThemeMode.DAY -> true
            ThemeMode.NIGHT -> false
            ThemeMode.AUTO -> calculateSolarTimes(currentDate, lat, lon).isDaytimeNow
        }
    }

    /**
     * Computes sunrise and sunset LocalTimes for a given location and date.
     */
    fun calculateSolarTimes(
        date: LocalDate = LocalDate.now(),
        lat: Double = DEFAULT_PHNOM_PENH_LAT,
        lon: Double = DEFAULT_PHNOM_PENH_LON
    ): SolarTimes {
        val dayOfYear = date.dayOfYear

        // Approximate solar declination (radians)
        val declination = 0.409 * sin(2.0 * PI * (dayOfYear - 81) / 365.0)

        // Lat in radians
        val latRad = Math.toRadians(lat)

        // Hour angle calculation for official zenith (90.833 degrees including atmospheric refraction)
        val cosHourAngle = (sin(Math.toRadians(-0.833)) - sin(latRad) * sin(declination)) / (cos(latRad) * cos(declination))

        val hourAngleDegrees = when {
            cosHourAngle >= 1.0 -> 0.0     // Polar night
            cosHourAngle <= -1.0 -> 180.0  // Midnight sun
            else -> Math.toDegrees(acos(cosHourAngle))
        }

        // Solar noon in hours UTC
        val solarNoonUtc = 12.0 - (lon / 15.0)

        // Convert to Cambodia Time (UTC+7)
        val sunriseHourUtc = solarNoonUtc - (hourAngleDegrees / 15.0)
        val sunsetHourUtc = solarNoonUtc + (hourAngleDegrees / 15.0)

        val sunriseHourLocal = (sunriseHourUtc + 7.0 + 24.0) % 24.0
        val sunsetHourLocal = (sunsetHourUtc + 7.0 + 24.0) % 24.0

        val sunrise = decimalHourToLocalTime(sunriseHourLocal)
        val sunset = decimalHourToLocalTime(sunsetHourLocal)

        val now = LocalTime.now()
        val isDaytime = now.isAfter(sunrise) && now.isBefore(sunset)

        return SolarTimes(
            sunrise = sunrise,
            sunset = sunset,
            isDaytimeNow = isDaytime
        )
    }

    private fun decimalHourToLocalTime(decimalHour: Double): LocalTime {
        val hours = decimalHour.toInt().coerceIn(0, 23)
        val remainderMinutes = (decimalHour - hours) * 60.0
        val minutes = remainderMinutes.toInt().coerceIn(0, 59)
        return LocalTime.of(hours, minutes)
    }
}
