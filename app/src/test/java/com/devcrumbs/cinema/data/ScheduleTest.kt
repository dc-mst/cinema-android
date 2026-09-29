package com.devcrumbs.cinema.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTest {
    private fun row(id: Int, movie: Int, cinema: Int, time: String, title: String = "M$movie", cinemaName: String = "C$cinema") =
        Screening(
            id = id, date = "2026-09-29", time = time,
            cinemaId = cinema, cinemaName = cinemaName,
            movieId = movie, movieTitle = title,
        )

    @Test
    fun `groups per film then per cinema, films by earliest showtime`() {
        val rows = listOf(
            row(1, movie = 2, cinema = 1, time = "21:00", cinemaName = "Zeta"),
            row(2, movie = 1, cinema = 1, time = "18:00", cinemaName = "Zeta"),
            row(3, movie = 2, cinema = 2, time = "16:30", cinemaName = "Alfa"),
            row(4, movie = 2, cinema = 1, time = "17:00", cinemaName = "Zeta"),
        )
        val grouped = groupByMovie(rows)
        assertEquals(listOf(2, 1), grouped.map { it.movieId })
        assertEquals(listOf("Alfa", "Zeta"), grouped[0].cinemas.map { it.cinemaName })
        assertEquals(listOf("17:00", "21:00"), grouped[0].cinemas[1].screenings.map { it.time })
        assertEquals(3, grouped[0].screeningCount)
    }

    @Test
    fun `cinema filter keeps only that venue`() {
        val rows = listOf(row(1, 1, 1, "18:00"), row(2, 2, 2, "19:00"))
        assertEquals(listOf(2), groupByMovie(rows, cinemaId = 2).map { it.movieId })
        assertEquals(listOf(1, 2), cinemasIn(rows).map { it.id })
    }

    @Test
    fun `decodes a live api row and ignores unknown fields`() {
        val json = """[{"id":1,"date":"2026-09-29","time":"20:30","cinema_id":9,"cinema_name":"Fratelli Marx",
            "movie_id":6,"movie_title":"Coyote vs. Acme","movie_is_first_week":true,"movie_is_premiere":false,
            "availability":null,"movie_festival_premieres":null,"some_future_field":{"x":1}}]"""
        val rows = CinemaApi.defaultJson.decodeFromString<List<Screening>>(json)
        assertEquals("Fratelli Marx", rows.single().cinemaName)
        assertEquals(true, rows.single().movieIsFirstWeek)
    }
}
