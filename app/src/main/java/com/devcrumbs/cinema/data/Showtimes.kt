package com.devcrumbs.cinema.data

import java.time.LocalTime
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// Ports of the website's frontend/src/utils/showtimes.ts — keep the rules equal.

/** Estimated end ("HH:mm"): start + running time. Ads/trailers not included. */
fun endTime(start: String?, durationMinutes: Int?): String? {
    if (start == null || durationMinutes == null || durationMinutes <= 0) return null
    val parts = start.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    val total = (h * 60 + m + durationMinutes) % (24 * 60)
    return "%02d:%02d".format(total / 60, total % 60)
}

enum class TimeBand { ALL, FROM_NOW, AFTERNOON, EVENING, NIGHT }

private fun minutesOf(time: String?): Int? {
    val parts = time?.split(":") ?: return null
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return h * 60 + m
}

/** Does a showtime start inside the band? `now` only matters for FROM_NOW. */
fun inTimeBand(time: String?, band: TimeBand, now: LocalTime = LocalTime.now()): Boolean {
    val start = minutesOf(time) ?: return band == TimeBand.ALL
    return when (band) {
        TimeBand.AFTERNOON -> start < 18 * 60
        TimeBand.EVENING -> start >= 18 * 60 && start < 21 * 60
        TimeBand.NIGHT -> start >= 21 * 60
        TimeBand.FROM_NOW -> start >= now.hour * 60 + now.minute
        TimeBand.ALL -> true
    }
}

data class LatLng(val lat: Double, val lng: Double)

/** Great-circle distance in km. */
fun distanceKm(a: LatLng, b: LatLng): Double {
    val r = 6371.0
    fun rad(d: Double) = Math.toRadians(d)
    val dLat = rad(b.lat - a.lat)
    val dLng = rad(b.lng - a.lng)
    val h = sin(dLat / 2).let { it * it } + cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2).let { it * it }
    return 2 * r * asin(sqrt(h))
}

fun screeningDistanceKm(s: Screening, from: LatLng?): Double? {
    if (from == null || s.cinemaLat == null || s.cinemaLng == null) return null
    return distanceKm(from, LatLng(s.cinemaLat, s.cinemaLng))
}

fun formatKm(km: Double, locale: Locale = Locale.getDefault()): String =
    if (km < 1) "${(km * 1000 / 50).roundToInt() * 50} m"
    else String.format(locale, "%.1f km", km).replace(Regex("[.,]0 km$"), " km")

/** Original-version showtime — the website's `language=OV` filter (substring, any case). */
fun isOriginalVersion(language: String?): Boolean = language?.contains("OV", ignoreCase = true) == true
