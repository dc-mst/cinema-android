package com.devcrumbs.cinema.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One deployment of the platform, generated from `backend/cities/<slug>/city.toml`. */
@Serializable
data class City(
    val slug: String,
    val name: String,
    val url: String,
    @SerialName("city_name") val cityName: String,
) {
    val apiBase: String get() = url.trimEnd('/') + "/api"
    val host: String get() = url.substringAfter("://").trimEnd('/')
}

/** A row of `GET /api/screenings` (see `Screening.to_dict()` in the backend). */
@Serializable
data class Screening(
    val id: Int,
    val date: String,
    val time: String? = null,
    val language: String? = null,
    @SerialName("room_name") val roomName: String? = null,
    @SerialName("booking_url") val bookingUrl: String? = null,
    val notes: String? = null,
    val availability: String? = null,
    @SerialName("cinema_id") val cinemaId: Int,
    @SerialName("cinema_name") val cinemaName: String? = null,
    @SerialName("cinema_slug") val cinemaSlug: String? = null,
    @SerialName("cinema_website") val cinemaWebsite: String? = null,
    @SerialName("cinema_address") val cinemaAddress: String? = null,
    @SerialName("movie_id") val movieId: Int,
    @SerialName("movie_title") val movieTitle: String? = null,
    @SerialName("movie_original_title") val movieOriginalTitle: String? = null,
    @SerialName("movie_director") val movieDirector: String? = null,
    @SerialName("movie_year") val movieYear: Int? = null,
    @SerialName("movie_duration_minutes") val movieDurationMinutes: Int? = null,
    @SerialName("movie_poster_url") val moviePosterUrl: String? = null,
    @SerialName("movie_description") val movieDescription: String? = null,
    @SerialName("movie_description_en") val movieDescriptionEn: String? = null,
    @SerialName("movie_tmdb_rating") val movieTmdbRating: Double? = null,
    @SerialName("movie_tmdb_vote_count") val movieTmdbVoteCount: Int? = null,
    @SerialName("movie_is_first_week") val movieIsFirstWeek: Boolean = false,
    @SerialName("movie_last_chance_date") val movieLastChanceDate: String? = null,
)

/** Screenings of one film at one cinema on the selected day. */
data class CinemaShowtimes(
    val cinemaId: Int,
    val cinemaName: String,
    val screenings: List<Screening>,
)

/** One film on the selected day, with its showtimes grouped per cinema. */
data class MovieSchedule(
    val movieId: Int,
    val title: String,
    val first: Screening,
    val cinemas: List<CinemaShowtimes>,
) {
    val screeningCount: Int get() = cinemas.sumOf { it.screenings.size }
}

data class CinemaRef(val id: Int, val name: String)
