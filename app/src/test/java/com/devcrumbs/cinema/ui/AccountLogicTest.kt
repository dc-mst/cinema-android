package com.devcrumbs.cinema.ui

import com.devcrumbs.cinema.data.FilmInfo
import com.devcrumbs.cinema.data.NotifyTiming
import com.devcrumbs.cinema.data.WatchlistAdd
import com.devcrumbs.cinema.data.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AccountLogicTest {
    private val today = LocalDate.of(2026, 10, 1)

    private fun item(timing: String, target: String?) =
        WatchlistItem(id = 1, tmdbId = 1, title = "X", notifyTiming = timing, targetDate = target, hasScreenings = true)

    @Test
    fun `reminder status mirrors the website's watchlist`() {
        assertEquals(ReminderStatus.CHOOSE, reminderStatus(item(NotifyTiming.IMMEDIATELY, null), today))
        assertEquals(ReminderStatus.CHOOSE, reminderStatus(item(NotifyTiming.SAME_DAY, "2026-10-01"), today))
        assertEquals(ReminderStatus.DATE_PASSED, reminderStatus(item(NotifyTiming.SAME_DAY, "2026-09-30"), today))
        // "none" with a date = the reminder already went out (the backend sets it after sending).
        assertEquals(ReminderStatus.SENT, reminderStatus(item(NotifyTiming.NONE, "2026-10-05"), today))
        assertEquals(ReminderStatus.SENT, reminderStatus(item(NotifyTiming.NONE, "2026-09-30"), today))
        assertEquals(ReminderStatus.CHOOSE, reminderStatus(item(NotifyTiming.NONE, null), today))
    }

    @Test
    fun `watchlist add needs a TMDB id`() {
        val film = FilmInfo(title = "Dune", originalTitle = "Dune", tmdbId = 438631, year = 2021, posterUrl = "p")
        assertEquals(WatchlistAdd(438631, "Dune", "Dune", "p", 2021, "2026-10-02"), watchlistAdd(film, "2026-10-02"))
        assertNull(watchlistAdd(film.copy(tmdbId = null), null))
        assertNull(watchlistAdd(film.copy(tmdbId = 0), null))
    }

    @Test
    fun `email check before sending`() {
        assertTrue(looksLikeEmail(" ada@example.com "))
        assertFalse(looksLikeEmail("ada@example"))
        assertFalse(looksLikeEmail("ada example.com"))
    }

    @Test
    fun `notification time is shown in the cities' zone`() {
        // Backend timestamps are naive UTC; 18:30 UTC is 20:30 in Italy in summer time.
        assertTrue(formatTimestamp("2026-09-28T18:30:00.123456")!!.endsWith("20:30"))
        assertNull(formatTimestamp("not a date"))
    }
}
