package com.devcrumbs.cinema.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One deployment of the platform (assets/cities.json, synced from city.toml). */
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

/**
 * `GET /api/site` — the city's public filter config (city.toml). Optional: a
 * backend deployed before the endpoint existed answers 404, and the app then
 * simply shows no event/festival filters and hides no cinema.
 */
@Serializable
data class SiteConfig(
    val slug: String = "",
    val name: String = "",
    @SerialName("city_name") val cityName: String = "",
    @SerialName("event_cinema_slugs") val eventCinemaSlugs: List<String> = emptyList(),
    @SerialName("venue_labels") val venueLabels: Map<String, String> = emptyMap(),
    @SerialName("ordered_venue_slugs") val orderedVenueSlugs: List<String> = emptyList(),
    @SerialName("festival_filters") val festivalFilters: List<FestivalFilter> = emptyList(),
    @SerialName("hidden_cinemas") val hiddenCinemas: List<String> = emptyList(),
) {
    companion object {
        val EMPTY = SiteConfig()
    }
}

@Serializable
data class FestivalFilter(val id: String, val label: String, val keyword: String = label)

@Serializable
data class FestivalPremiere(
    val festival: String,
    @SerialName("edition_year") val editionYear: Int? = null,
    val date: String? = null,
)

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
    @SerialName("cinema_lat") val cinemaLat: Double? = null,
    @SerialName("cinema_lng") val cinemaLng: Double? = null,
    @SerialName("movie_id") val movieId: Int,
    @SerialName("movie_title") val movieTitle: String? = null,
    @SerialName("movie_original_title") val movieOriginalTitle: String? = null,
    @SerialName("movie_director") val movieDirector: String? = null,
    @SerialName("movie_year") val movieYear: Int? = null,
    @SerialName("movie_duration_minutes") val movieDurationMinutes: Int? = null,
    @SerialName("movie_poster_url") val moviePosterUrl: String? = null,
    @SerialName("movie_description") val movieDescription: String? = null,
    @SerialName("movie_description_en") val movieDescriptionEn: String? = null,
    @SerialName("movie_tmdb_id") val movieTmdbId: Int? = null,
    @SerialName("movie_tmdb_rating") val movieTmdbRating: Double? = null,
    @SerialName("movie_tmdb_vote_count") val movieTmdbVoteCount: Int? = null,
    @SerialName("movie_is_first_week") val movieIsFirstWeek: Boolean = false,
    @SerialName("movie_last_chance_date") val movieLastChanceDate: String? = null,
    @SerialName("movie_festival_premieres") val movieFestivalPremieres: List<FestivalPremiere>? = null,
)

/** A film as the film/search endpoints return it (`Movie.to_dict()`). */
@Serializable
data class FilmInfo(
    val id: Int? = null,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String? = null,
    val director: String? = null,
    val year: Int? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val description: String? = null,
    @SerialName("description_en") val descriptionEn: String? = null,
    val genre: String? = null,
    @SerialName("age_rating") val ageRating: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("tmdb_rating") val tmdbRating: Double? = null,
    @SerialName("tmdb_vote_count") val tmdbVoteCount: Int? = null,
    @SerialName("festival_premieres") val festivalPremieres: List<FestivalPremiere>? = null,
    @SerialName("has_upcoming_screenings") val hasUpcomingScreenings: Boolean? = null,
    @SerialName("has_any_screenings") val hasAnyScreenings: Boolean? = null,
)

@Serializable
data class PastRun(
    @SerialName("cinema_name") val cinemaName: String? = null,
    @SerialName("cinema_slug") val cinemaSlug: String? = null,
    val dates: List<String> = emptyList(),
)

/**
 * `GET /api/film/<slug>`. `status`: current | historical | unscheduled |
 * tmdb_only | moved (then `slug` is where the film now lives).
 */
@Serializable
data class FilmPage(
    val status: String,
    val slug: String? = null,
    val movie: FilmInfo? = null,
    val screenings: List<Screening> = emptyList(),
    @SerialName("past_runs") val pastRuns: List<PastRun> = emptyList(),
)

/** `GET /api/tmdb/search` row — any film on TMDB. */
@Serializable
data class TmdbResult(
    @SerialName("tmdb_id") val tmdbId: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val year: Int? = null,
)

@Serializable
data class CinemaPage(
    val id: Int,
    val name: String,
    val slug: String,
    val address: String? = null,
    val website: String? = null,
    val phone: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val screenings: List<Screening> = emptyList(),
)

@Serializable
data class Genre(val name: String, val slug: String, val count: Int = 0)

@Serializable
data class GenrePage(val name: String, val slug: String, val screenings: List<Screening> = emptyList())

// ── Più visti (/api/top-movies) ─────────────────────────────────────────────

@Serializable
data class TopMoviesResponse(val movies: List<TopMovie> = emptyList())

@Serializable
data class TopMovie(
    val key: String,
    val title: String,
    val slug: String? = null,
    val year: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("tickets_sold") val ticketsSold: Int = 0,
    @SerialName("occupancy_mean") val occupancyMean: Double? = null,
    @SerialName("occupancy_best") val occupancyBest: Double? = null,
    val screenings: Int = 0,
    val cinemas: List<CinemaRefDto> = emptyList(),
    @SerialName("first_date") val firstDate: String? = null,
    @SerialName("last_date") val lastDate: String? = null,
    val estimated: Int = 0,
)

@Serializable
data class CinemaRefDto(val name: String, val slug: String)

@Serializable
data class TopFilmDetail(
    val film: TopMovie,
    val screenings: List<TopFilmScreening> = emptyList(),
    val truncated: Boolean = false,
)

@Serializable
data class TopFilmScreening(
    @SerialName("cinema_name") val cinemaName: String,
    @SerialName("cinema_slug") val cinemaSlug: String? = null,
    val date: String,
    val time: String,
    @SerialName("room_name") val roomName: String? = null,
    @SerialName("tickets_sold") val ticketsSold: Int? = null,
    @SerialName("occupancy_pct") val occupancyPct: Double? = null,
    @SerialName("is_estimated") val isEstimated: Boolean = false,
    @SerialName("is_final") val isFinal: Boolean = true,
)

// ── Grouped views ───────────────────────────────────────────────────────────

/** Screenings of one film at one cinema. */
data class CinemaShowtimes(
    val cinemaId: Int,
    val cinemaName: String,
    val cinemaSlug: String?,
    val screenings: List<Screening>,
)

/** One film, with its showtimes grouped per cinema. */
data class MovieSchedule(
    val movieId: Int,
    val title: String,
    val first: Screening,
    val cinemas: List<CinemaShowtimes>,
) {
    val screeningCount: Int get() = cinemas.sumOf { it.screenings.size }
}

data class CinemaRef(val id: Int, val name: String, val slug: String?)
