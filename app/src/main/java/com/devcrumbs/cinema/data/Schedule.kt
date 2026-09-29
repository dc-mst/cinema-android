package com.devcrumbs.cinema.data

/**
 * Turns the flat, time-ordered `/api/screenings` rows into the web app's
 * "by film" view: one entry per film, showtimes grouped by cinema.
 * Films are ordered by their earliest showtime, cinemas alphabetically.
 */
fun groupByMovie(screenings: List<Screening>, cinemaId: Int? = null): List<MovieSchedule> =
    screenings
        .filter { cinemaId == null || it.cinemaId == cinemaId }
        .groupBy { it.movieId }
        .map { (movieId, rows) ->
            val sorted = rows.sortedBy { it.time.orEmpty() }
            MovieSchedule(
                movieId = movieId,
                title = sorted.first().movieTitle.orEmpty(),
                first = sorted.first(),
                cinemas = sorted
                    .groupBy { it.cinemaId }
                    .map { (id, perCinema) ->
                        CinemaShowtimes(id, perCinema.first().cinemaName.orEmpty(), perCinema)
                    }
                    .sortedBy { it.cinemaName.lowercase() },
            )
        }
        .sortedWith(compareBy({ it.first.time.orEmpty() }, { it.title.lowercase() }))

/** Cinemas that have at least one screening in [screenings], alphabetically. */
fun cinemasIn(screenings: List<Screening>): List<CinemaRef> =
    screenings
        .distinctBy { it.cinemaId }
        .map { CinemaRef(it.cinemaId, it.cinemaName.orEmpty()) }
        .sortedBy { it.name.lowercase() }
