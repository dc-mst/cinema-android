package com.devcrumbs.cinema.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * HTTP error with its status, so callers can tell "not found" from "offline".
 * [serverMessage] is the body's `error` text when the backend sent one.
 */
class ApiException(val code: Int, message: String, val serverMessage: String? = null) : IOException(message)

/** Server-side filters of `GET /api/screenings` (the website sends the same). */
data class ScreeningQuery(
    val date: String,
    val cinemaSlug: String? = null,
    val event: String? = null,          // notes keyword
    val originalVersion: Boolean = false,
    val firstWeek: Boolean = false,
)

/** Read-only client for the public endpoints every city serves under `/api`. */
class CinemaApi(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) {
    /** ISO dates (yyyy-MM-dd) that have at least one screening. */
    suspend fun calendar(city: City): List<String> = get(url(city, "screenings/calendar"))

    suspend fun screenings(city: City, q: ScreeningQuery): List<Screening> =
        get(url(city, "screenings") {
            addQueryParameter("date", q.date)
            q.cinemaSlug?.let { addQueryParameter("cinema", it) }
            q.event?.let { addQueryParameter("event", it) }
            if (q.originalVersion) addQueryParameter("language", "OV")
            if (q.firstWeek) addQueryParameter("first_week", "true")
        })

    /** City filter config; [SiteConfig.EMPTY] when the backend predates /api/site. */
    suspend fun site(city: City): SiteConfig =
        try {
            get(url(city, "site"))
        } catch (e: ApiException) {
            if (e.code == 404) SiteConfig.EMPTY else throw e
        }

    suspend fun film(city: City, slug: String): FilmPage = get(url(city, "film/$slug"))

    /** Live TMDB details for a film no local source has (search → TMDB result). */
    suspend fun tmdbMovie(city: City, tmdbId: Int): FilmInfo = get(url(city, "tmdb/movie/$tmdbId"))

    suspend fun cinema(city: City, slug: String): CinemaPage = get(url(city, "cinemas/$slug"))

    suspend fun searchMovies(city: City, query: String): List<FilmInfo> =
        get(url(city, "movies") { addQueryParameter("search", query); addQueryParameter("limit", "20") })

    suspend fun searchTmdb(city: City, query: String): List<TmdbResult> =
        get(url(city, "tmdb/search") { addQueryParameter("q", query) })

    suspend fun genres(city: City): List<Genre> = get(url(city, "genres"))

    suspend fun genre(city: City, slug: String): GenrePage = get(url(city, "genres/$slug"))

    /** Più visti: `days` window (1–31) or one `day` inside it; metric tickets|occupancy. */
    suspend fun topMovies(city: City, days: Int = 7, metric: String = "tickets", limit: Int = 20): TopMoviesResponse =
        get(url(city, "top-movies") {
            addQueryParameter("days", days.toString())
            addQueryParameter("metric", metric)
            addQueryParameter("limit", limit.toString())
        })

    suspend fun topMovieFilm(city: City, key: String): TopFilmDetail =
        get(url(city, "top-movies/film") { addQueryParameter("key", key) })

    private fun url(city: City, path: String, build: HttpUrl.Builder.() -> Unit = {}): String =
        city.apiBase.toHttpUrl().newBuilder().addPathSegments(path).apply(build).build().toString()

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw ApiException(response.code, "HTTP ${response.code} for $url")
            json.decodeFromString<T>(response.body!!.string())
        }
    }

    companion object {
        val defaultJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        internal val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Same-origin, resized WebP of a poster via the city's `/api/img`
         * proxy (see README "Poster proxy"). Widths snap to the server ladder.
         */
        fun posterUrl(city: City, original: String?, width: Int = 320): String? =
            original?.takeIf { it.isNotBlank() }?.let {
                city.apiBase.toHttpUrl().newBuilder()
                    .addPathSegment("img")
                    .addQueryParameter("u", it)
                    .addQueryParameter("w", width.toString())
                    .build().toString()
            }
    }
}
