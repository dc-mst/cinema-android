package com.devcrumbs.cinema.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

internal fun row(
    id: Int, movie: Int, cinema: Int, time: String,
    title: String = "M$movie", cinemaName: String = "C$cinema", date: String = "2026-09-29",
    slug: String? = "c$cinema",
) = Screening(
    id = id, date = date, time = time,
    cinemaId = cinema, cinemaName = cinemaName, cinemaSlug = slug,
    movieId = movie, movieTitle = title,
)

class ScheduleTest {
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
    fun `cinema pills leave out hidden cinemas`() {
        val rows = listOf(row(1, 1, 1, "18:00"), row(2, 2, 2, "19:00", slug = "arena"))
        assertEquals(listOf(1, 2), cinemasIn(rows).map { it.id })
        assertEquals(listOf(1), cinemasIn(rows, setOf("arena")).map { it.id })
    }

    @Test
    fun `groups by date ascending`() {
        val rows = listOf(row(1, 1, 1, "18:00", date = "2026-10-02"), row(2, 1, 1, "16:00", date = "2026-10-01"))
        assertEquals(listOf("2026-10-01", "2026-10-02"), groupByDate(rows).map { it.first })
    }

    @Test
    fun `decodes a live api row and ignores unknown fields`() {
        val json = """[{"id":1,"date":"2026-09-29","time":"20:30","cinema_id":9,"cinema_name":"Fratelli Marx",
            "cinema_lat":45.07,"cinema_lng":7.68,"movie_id":6,"movie_title":"Coyote vs. Acme","movie_is_first_week":true,
            "movie_festival_premieres":[{"festival":"cannes","edition_year":2026,"date":"2026-05-19"}],
            "availability":null,"some_future_field":{"x":1}}]"""
        val r = CinemaApi.defaultJson.decodeFromString<List<Screening>>(json).single()
        assertEquals("Fratelli Marx", r.cinemaName)
        assertTrue(r.movieIsFirstWeek)
        assertEquals("cannes", r.movieFestivalPremieres!!.single().festival)
    }

    @Test
    fun `decodes site config, film page and ranking`() {
        val site = CinemaApi.defaultJson.decodeFromString<SiteConfig>("""{"slug":"x","festival_filters":
            [{"id":"semi","label":"SEMI","keyword":"SEMI | long"}],"hidden_cinemas":["jolly"],"extra":1}""")
        assertEquals("SEMI | long", site.festivalFilters.single().keyword)
        val film = CinemaApi.defaultJson.decodeFromString<FilmPage>("""{"status":"historical","slug":"a",
            "movie":{"id":1,"title":"A","director":null},"past_runs":[{"cinema_name":"L","dates":["2026-09-01"]}],"indexable":true}""")
        assertEquals("historical", film.status)
        assertEquals(emptyList<Screening>(), film.screenings)
        val top = CinemaApi.defaultJson.decodeFromString<TopMoviesResponse>("""{"coverage":{},"movies":[{"key":"tmdb:1",
            "title":"A","tickets_sold":6043,"occupancy_mean":24.7,"screenings":98,"cinemas":[{"name":"S","slug":"s"}]}]}""")
        assertEquals(6043, top.movies.single().ticketsSold)
    }
}

class ShowtimesTest {
    @Test
    fun `end time wraps past midnight and needs a running time`() {
        assertEquals("22:44", endTime("20:30", 134))
        assertEquals("00:40", endTime("23:00", 100))
        assertNull(endTime("20:30", null))
        assertNull(endTime("20:30", 0))
    }

    @Test
    fun `time bands match the website`() {
        assertTrue(inTimeBand("17:59", TimeBand.AFTERNOON))
        assertFalse(inTimeBand("18:00", TimeBand.AFTERNOON))
        assertTrue(inTimeBand("18:00", TimeBand.EVENING))
        assertTrue(inTimeBand("21:00", TimeBand.NIGHT))
        assertTrue(inTimeBand("20:00", TimeBand.FROM_NOW, LocalTime.of(20, 0)))
        assertFalse(inTimeBand("19:59", TimeBand.FROM_NOW, LocalTime.of(20, 0)))
    }

    @Test
    fun `distance and formatting`() {
        val bologna = LatLng(44.4949, 11.3426)
        val lumiere = LatLng(44.5007, 11.3371)
        assertEquals(0.77, distanceKm(bologna, lumiere), 0.02)
        assertEquals("750 m", formatKm(0.77, Locale.ITALY))
        assertEquals("2,4 km", formatKm(2.43, Locale.ITALY))
        assertEquals("3 km", formatKm(3.01, Locale.ITALY))
    }

    @Test
    fun `original version is a substring match, any case`() {
        assertTrue(isOriginalVersion("OV sub ITA"))
        assertTrue(isOriginalVersion("ov"))
        assertFalse(isOriginalVersion("ITA"))
        assertFalse(isOriginalVersion(null))
    }
}

class SlugTest {
    /** Expected values produced by the website's slug.ts logic (Node). */
    @Test
    fun `slugify matches the website`() {
        val cases = mapOf(
            "Dune - Parte tre" to "dune-parte-tre",
            "Avengers: Endgame Extra" to "avengers-endgame-extra",
            "L'Été dernier" to "lete-dernier",
            "Città  aperta!" to "citta-aperta",
            "Amélie  —  Le fabuleux destin" to "amelie-le-fabuleux-destin",
            "8½" to "8",
            "   " to "",
            "Ça" to "ca",
            "E.T. l'extra-terrestre" to "et-lextraterrestre",
            "Mission: Impossible – Dead Reckoning" to "mission-impossible-dead-reckoning",
            "Straße" to "strae",
            "Hokum Night" to "hokumnight",
        )
        cases.forEach { (title, slug) -> assertEquals(title, slug, slugify(title)) }
        assertEquals("arrival-2016", buildTmdbSlug("Arrival", 2016))
        assertEquals("arrival", buildTmdbSlug("Arrival", null))
    }

    @Test
    fun `festival premieres deduped per festival, earliest first`() {
        val list = listOf(
            FestivalPremiere("venice", 2026, "2026-09-01"),
            FestivalPremiere("cannes", 2026, "2026-05-20"),
            FestivalPremiere("cannes", 2026, "2026-05-19"),
        )
        assertEquals(listOf("cannes" to "2026-05-19", "venice" to "2026-09-01"),
            dedupeFestivalPremieres(list).map { it.festival to it.date })
        assertEquals("TO", festivalInitial("tiff"))
    }
}

class FactsLineTest {
    @Test
    fun `each fact is direction-isolated`() {
        assertEquals("⁨يوفال⁩ · ⁨2026⁩", com.devcrumbs.cinema.ui.factsLine(listOf("يوفال", "2026")))
        assertEquals("", com.devcrumbs.cinema.ui.factsLine(emptyList()))
    }
}
