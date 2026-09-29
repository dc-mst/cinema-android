package com.devcrumbs.cinema.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * Decodes every endpoint the app uses from each city's LIVE API — catches a
 * DTO that no longer matches the backend. Opt-in (network):
 *   LIVE_API=1 ./gradlew testDebugUnitTest --tests '*LiveApiTest*'
 */
class LiveApiTest {
    private val api = CinemaApi()

    private fun cities(): List<City> =
        Json.decodeFromString(File("src/main/assets/cities.json").readText())

    @Test
    fun `every endpoint decodes for every city`() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        for (city in cities()) {
            val dates = api.calendar(city)
            val date = dates.firstOrNull() ?: LocalDate.now().toString()
            val rows = api.screenings(city, ScreeningQuery(date))
            val site = api.site(city)
            println("${city.slug}: ${dates.size} dates, ${rows.size} screenings on $date, " +
                "${site.festivalFilters.size} festivals, ${site.hiddenCinemas.size} hidden (site ${if (site.slug.isEmpty()) "absent" else "ok"})")
            api.screenings(city, ScreeningQuery(date, originalVersion = true, firstWeek = true))
            rows.firstOrNull()?.let { r ->
                val film = api.film(city, slugify(r.movieTitle.orEmpty()))
                println("  film ${film.status} '${film.movie?.title}' ${film.screenings.size} screenings, ${film.pastRuns.size} past runs")
                r.cinemaSlug?.let { println("  cinema ${api.cinema(city, it).name}") }
            }
            val top = api.topMovies(city)
            println("  top: ${top.movies.size}" + (top.movies.firstOrNull()?.let { " — #1 ${it.title} (${it.ticketsSold})" } ?: ""))
            top.movies.firstOrNull()?.let { api.topMovieFilm(city, it.key) }
            val genres = api.genres(city)
            genres.firstOrNull()?.let { api.genre(city, it.slug) }
            val search = api.searchMovies(city, "the")
            val tmdb = api.searchTmdb(city, "arrival")
            tmdb.firstOrNull()?.let { api.tmdbMovie(city, it.tmdbId) }
            println("  genres ${genres.size}, search ${search.size}, tmdb ${tmdb.size}")
        }
    }

    /**
     * The account endpoints can't be exercised without logging in (and a test
     * must not create accounts on the live sites): check each exists and
     * refuses a bad token with the 401 that logs the app out, and that the
     * public "seen" lookup decodes.
     */
    @Test
    fun `account endpoints exist in every city`() = runBlocking {
        assumeTrue(System.getenv("LIVE_API") == "1")
        val account = AccountApi()
        for (city in cities()) {
            val calls: List<Pair<String, suspend () -> Unit>> = listOf(
                "me" to { account.me(city, "invalid") },
                "watchlist" to { account.watchlist(city, "invalid") },
                "seen-movies" to { account.seenMovies(city, "invalid") },
                "cities" to { account.cities(city, "invalid") },
                "notifications" to { account.notifications(city, "invalid") },
                "preferences" to { account.preferences(city, "invalid") },
            )
            for ((name, call) in calls) {
                try {
                    call()
                    throw AssertionError("${city.slug} $name: accepted an invalid token")
                } catch (e: ApiException) {
                    if (e.code != 401) throw AssertionError("${city.slug} $name: HTTP ${e.code}, expected 401")
                }
            }
            val title = api.screenings(city, ScreeningQuery(LocalDate.now().toString())).firstOrNull()?.movieTitle
            val keyed = title?.let { account.moviesByKeys(city, listOf(normaliseTitle(it))) }.orEmpty()
            println("${city.slug}: account endpoints 401 ok, keys lookup ${keyed.size} film(s) for '$title'")
        }
    }
}
