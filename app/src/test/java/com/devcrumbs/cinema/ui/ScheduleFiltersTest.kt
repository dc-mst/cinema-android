package com.devcrumbs.cinema.ui

import com.devcrumbs.cinema.data.FestivalFilter
import com.devcrumbs.cinema.data.LatLng
import com.devcrumbs.cinema.data.Screening
import com.devcrumbs.cinema.data.SiteConfig
import com.devcrumbs.cinema.data.TimeBand
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class ScheduleFiltersTest {
    private fun s(id: Int, time: String, lastChance: String? = null, lat: Double? = null, lng: Double? = null) =
        Screening(id = id, date = "2026-10-01", time = time, cinemaId = 1, movieId = id,
            movieLastChanceDate = lastChance, cinemaLat = lat, cinemaLng = lng)

    private val rows = listOf(
        s(1, "16:00", lastChance = "2026-10-02", lat = 44.50, lng = 11.34),
        s(2, "19:00", lat = 44.60, lng = 11.34),
        s(3, "22:00"),
    )

    private fun state(f: ScheduleFilters) =
        ScheduleUiState(screenings = rows, filters = f, selectedDate = LocalDate.of(2026, 10, 1))

    @Test
    fun `device side filters`() {
        assertEquals(listOf(1), state(ScheduleFilters(lastChance = true)).visibleScreenings().map { it.id })
        assertEquals(listOf(2), state(ScheduleFilters(timeBand = TimeBand.EVENING)).visibleScreenings().map { it.id })
        // 1 km around the first cinema: the second is ~11 km away, the third has no coordinates.
        val near = ScheduleFilters(location = LatLng(44.50, 11.34), radiusKm = 1)
        assertEquals(listOf(1), state(near).visibleScreenings().map { it.id })
        // Location without a radius only sorts — nothing is dropped.
        assertEquals(3, state(ScheduleFilters(location = LatLng(44.50, 11.34))).visibleScreenings().size)
    }

    @Test
    fun `from now only applies to today`() {
        val notToday = state(ScheduleFilters(timeBand = TimeBand.FROM_NOW))
        assertEquals(3, notToday.visibleScreenings(LocalTime.of(23, 0)).size)
    }

    @Test
    fun `event pills - festivals then seasonal venues in display order`() {
        val site = SiteConfig(
            festivalFilters = listOf(FestivalFilter("semi", "SEMI", "SEMI | notes")),
            eventCinemaSlugs = listOf("b", "a"),
            orderedVenueSlugs = listOf("a", "b"),
            venueLabels = mapOf("a" to "Arena A"),
        )
        val choices = ScheduleUiState(site = site).eventChoices
        assertEquals(listOf("SEMI", "Arena A", "b"), choices.map { it.label })
        assertEquals("SEMI | notes", (choices[0] as EventChoice.Festival).keyword)
    }
}
