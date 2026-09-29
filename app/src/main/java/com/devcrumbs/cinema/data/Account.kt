package com.devcrumbs.cinema.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// DTOs of the account endpoints (auth, user, watchlist, notifications). One
// account and one token for every city: the backend keeps accounts in a shared
// database, so any city's API answers for the same person.

/** `user` of every auth response (`user_payload()` in the backend). */
@Serializable
data class AccountUser(
    val id: Int,
    val name: String? = null,
    val email: String = "",
    val language: String = "it",
    /** Slug of the city the account was created in (null for old accounts). */
    @SerialName("origin_city") val originCity: String? = null,
)

/** What the app keeps between launches (encrypted, see [SessionStore]). */
@Serializable
data class Session(val token: String, val user: AccountUser)

@Serializable
data class AuthResponse(val token: String, val user: AccountUser)

@Serializable
data class MeResponse(val user: AccountUser)

@Serializable
data class MessageResponse(val message: String? = null, val error: String? = null)

/**
 * "Voglio vederlo". [hasScreenings] and [latestScreeningDate] are computed by
 * the city whose API was asked — i.e. they describe the selected city.
 */
@Serializable
data class WatchlistItem(
    val id: Int,
    @SerialName("tmdb_id") val tmdbId: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val year: Int? = null,
    @SerialName("added_at") val addedAt: String? = null,
    @SerialName("notify_timing") val notifyTiming: String = NotifyTiming.IMMEDIATELY,
    @SerialName("target_date") val targetDate: String? = null,
    @SerialName("has_screenings") val hasScreenings: Boolean = false,
    @SerialName("latest_screening_date") val latestScreeningDate: String? = null,
    @SerialName("movie_key") val movieKey: String? = null,
)

/** `notify_timing` values the backend accepts. */
object NotifyTiming {
    const val IMMEDIATELY = "immediately"
    const val SAME_DAY = "same_day"
    const val THREE_DAYS_BEFORE = "three_days_before"
    const val NONE = "none"

    /** The reminder choices the website offers for a film that is showing. */
    val REMINDERS = listOf(NONE, SAME_DAY, THREE_DAYS_BEFORE)
}

/** Body of `POST /api/user/watchlist`. */
@Serializable
data class WatchlistAdd(
    @SerialName("tmdb_id") val tmdbId: Int,
    val title: String,
    @SerialName("original_title") val originalTitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val year: Int? = null,
    /** Earliest screening when added from a film that is showing (the website does the same). */
    @SerialName("target_date") val targetDate: String? = null,
)

@Serializable
data class WatchlistResponse(val watchlist: List<WatchlistItem> = emptyList())

@Serializable
data class WatchlistItemResponse(@SerialName("watchlist_item") val item: WatchlistItem)

@Serializable
data class MarkSeenResponse(@SerialName("movie_key") val movieKey: String? = null)

/** "Già visto": keys are `normaliseTitle(title)` ([normaliseTitle]). */
@Serializable
data class SeenMoviesResponse(@SerialName("movie_keys") val movieKeys: List<String> = emptyList())

/** A film of `GET /api/movies?keys=` — the city's films matching seen keys, with upcoming screenings. */
@Serializable
data class KeyedMovie(
    val id: Int? = null,
    val title: String = "",
    val year: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val screenings: List<Screening> = emptyList(),
)

/** One city of `GET /api/user/cities`: may it email / alert the person? */
@Serializable
data class AccountCity(
    val slug: String,
    val name: String = "",
    val url: String? = null,
    @SerialName("is_origin") val isOrigin: Boolean = false,
    @SerialName("email_enabled") val emailEnabled: Boolean = false,
    @SerialName("alerts_enabled") val alertsEnabled: Boolean = false,
)

@Serializable
data class AccountCitiesResponse(val cities: List<AccountCity> = emptyList())

@Serializable
data class AccountCityResponse(val city: AccountCity)

/** In-app notification ("a film on your list is showing"), tagged with its city. */
@Serializable
data class AppNotification(
    val id: Int,
    @SerialName("movie_id") val movieId: Int? = null,
    @SerialName("movie_title") val movieTitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("city_slug") val citySlug: String,
    /** Only with `?all_cities=1`, which the app always sends. */
    @SerialName("city_name") val cityName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("read_at") val readAt: String? = null,
) {
    val unread: Boolean get() = readAt == null
}

@Serializable
data class NotificationsResponse(
    val notifications: List<AppNotification> = emptyList(),
    @SerialName("unread_count") val unreadCount: Int = 0,
)

/** `GET /api/user/preferences`: person-level keys (language, theme…) plus this city's. */
@Serializable
data class PreferencesResponse(val preferences: JsonObject = JsonObject(emptyMap()))
