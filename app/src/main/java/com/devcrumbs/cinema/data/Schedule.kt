package com.devcrumbs.cinema.data

/**
 * Turns flat, time-ordered screening rows into the web app's "by film" view:
 * one entry per film, showtimes grouped by cinema. Films are ordered by their
 * earliest showtime, cinemas alphabetically.
 */
fun groupByMovie(screenings: List<Screening>): List<MovieSchedule> =
    screenings
        .groupBy { it.movieId }
        .map { (movieId, rows) ->
            val sorted = rows.sortedWith(compareBy({ it.date }, { it.time.orEmpty() }))
            MovieSchedule(
                movieId = movieId,
                title = sorted.first().movieTitle.orEmpty(),
                first = sorted.first(),
                cinemas = groupByCinema(sorted),
            )
        }
        .sortedWith(compareBy({ it.first.date }, { it.first.time.orEmpty() }, { it.title.lowercase() }))

fun groupByCinema(screenings: List<Screening>): List<CinemaShowtimes> =
    screenings
        .groupBy { it.cinemaId }
        .map { (id, rows) -> CinemaShowtimes(id, rows.first().cinemaName.orEmpty(), rows.first().cinemaSlug, rows) }
        .sortedBy { it.cinemaName.lowercase() }

/** Rows grouped by date (ISO), dates ascending — for film and cinema pages. */
fun groupByDate(screenings: List<Screening>): List<Pair<String, List<Screening>>> =
    screenings.groupBy { it.date }.toSortedMap().map { (d, rows) -> d to rows.sortedBy { it.time.orEmpty() } }

/** Cinemas that have at least one screening in [screenings], alphabetically,
 *  without the city's hidden ones (site config, e.g. seasonal venues shown as events). */
fun cinemasIn(screenings: List<Screening>, hidden: Collection<String> = emptySet()): List<CinemaRef> =
    screenings
        .distinctBy { it.cinemaId }
        .filter { it.cinemaSlug == null || it.cinemaSlug !in hidden }
        .map { CinemaRef(it.cinemaId, it.cinemaName.orEmpty(), it.cinemaSlug) }
        .sortedBy { it.name.lowercase() }
