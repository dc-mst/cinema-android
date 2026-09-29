package com.devcrumbs.cinema.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.CityRepository
import com.devcrumbs.cinema.data.LatLng
import com.devcrumbs.cinema.data.Screening
import com.devcrumbs.cinema.data.ScreeningQuery
import com.devcrumbs.cinema.data.SiteConfig
import com.devcrumbs.cinema.data.TimeBand
import com.devcrumbs.cinema.data.inTimeBand
import com.devcrumbs.cinema.data.screeningDistanceKm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Every city is in Italy; "today" follows the servers' Europe/Rome day. */
val CITY_ZONE: ZoneId = ZoneId.of("Europe/Rome")

/** An event pill: a festival (matched in screening notes) or a seasonal venue. */
sealed interface EventChoice {
    val id: String
    val label: String

    data class Festival(override val id: String, override val label: String, val keyword: String) : EventChoice
    data class Venue(override val id: String, override val label: String) : EventChoice
}

/** The programme's filters — the website's feed filters. */
data class ScheduleFilters(
    val cinemaSlug: String? = null,
    val event: EventChoice? = null,
    val originalVersion: Boolean = false,
    val firstWeek: Boolean = false,
    val lastChance: Boolean = false,
    val timeBand: TimeBand = TimeBand.ALL,
    val location: LatLng? = null,
    val radiusKm: Int? = null,
) {
    /** Filters inside the sheet (cinema/event pills are counted separately). */
    val sheetCount: Int
        get() = listOf(originalVersion, firstWeek, lastChance, timeBand != TimeBand.ALL, location != null).count { it }
}

data class ScheduleUiState(
    val city: City? = null,
    val site: SiteConfig = SiteConfig.EMPTY,
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    /** The day's rows after the server-side filters (cinema, event, VO, Novità). */
    val screenings: List<Screening> = emptyList(),
    val filters: ScheduleFilters = ScheduleFilters(),
    val loading: Boolean = false,
    val error: Boolean = false,
    /** City picker shown over an already-chosen city (back returns to it). */
    val pickingCity: Boolean = false,
) {
    /** Event pills from /api/site: festivals first, then the seasonal venues in display order. */
    val eventChoices: List<EventChoice>
        get() = site.festivalFilters.map { EventChoice.Festival(it.id, it.label, it.keyword) } +
            site.orderedVenueSlugs.ifEmpty { site.eventCinemaSlugs }
                .map { EventChoice.Venue(it, site.venueLabels[it] ?: it) }

    /** Rows after the device-side filters (Ultimi giorni, time band, distance). */
    fun visibleScreenings(now: LocalTime = LocalTime.now(CITY_ZONE)): List<Screening> {
        val isToday = selectedDate == LocalDate.now(CITY_ZONE)
        val band = if (filters.timeBand == TimeBand.FROM_NOW && !isToday) TimeBand.ALL else filters.timeBand
        return screenings.filter { s ->
            (!filters.lastChance || s.movieLastChanceDate != null) &&
                inTimeBand(s.time, band, now) &&
                (filters.location == null || filters.radiusKm == null ||
                    (screeningDistanceKm(s, filters.location)?.let { it <= filters.radiusKm } ?: false))
        }
    }
}

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CityRepository(app)
    val api = CinemaApi()

    val cities: List<City> get() = repo.cities

    private val _state = MutableStateFlow(ScheduleUiState(city = repo.selectedCity()))
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        _state.value.city?.let { loadCity(it) }
    }

    fun selectCity(city: City) {
        if (city == _state.value.city) {
            _state.update { it.copy(pickingCity = false) }
            return
        }
        repo.selectedSlug = city.slug
        _state.value = ScheduleUiState(city = city)
        loadCity(city)
    }

    fun openCityPicker() = _state.update { it.copy(pickingCity = true) }

    fun closeCityPicker() = _state.update { it.copy(pickingCity = false) }

    fun selectDate(date: LocalDate) {
        val city = _state.value.city ?: return
        _state.update { it.copy(selectedDate = date) }
        loadScreenings(city)
    }

    fun selectCinema(slug: String?) = updateFilters(refetch = true) { it.copy(cinemaSlug = slug, event = null) }

    fun selectEvent(event: EventChoice?) = updateFilters(refetch = true) { it.copy(event = event, cinemaSlug = null) }

    fun setOriginalVersion(on: Boolean) = updateFilters(refetch = true) { it.copy(originalVersion = on) }

    fun setFirstWeek(on: Boolean) = updateFilters(refetch = true) { it.copy(firstWeek = on) }

    fun setLastChance(on: Boolean) = updateFilters(refetch = false) { it.copy(lastChance = on) }

    fun setTimeBand(band: TimeBand) = updateFilters(refetch = false) { it.copy(timeBand = band) }

    fun setLocation(location: LatLng?, radiusKm: Int?) =
        updateFilters(refetch = false) { it.copy(location = location, radiusKm = if (location == null) null else radiusKm) }

    fun clearFilters() = updateFilters(refetch = true) { ScheduleFilters() }

    fun refresh() {
        val current = _state.value
        val city = current.city ?: return
        if (current.dates.isEmpty() || current.selectedDate == null) loadCity(city) else loadScreenings(city)
    }

    private fun updateFilters(refetch: Boolean, change: (ScheduleFilters) -> ScheduleFilters) {
        _state.update { it.copy(filters = change(it.filters)) }
        if (refetch) _state.value.city?.let { loadScreenings(it) }
    }

    private fun loadCity(city: City) = launchLoad {
        val site = async { runCatching { api.site(city) }.getOrDefault(SiteConfig.EMPTY) }
        val today = LocalDate.now(CITY_ZONE)
        val dates = api.calendar(city).map(LocalDate::parse).filter { !it.isBefore(today) }
        val date = dates.firstOrNull() ?: today
        _state.update { it.copy(dates = dates, selectedDate = date, site = site.await()) }
        api.screenings(city, query(date))
    }

    private fun loadScreenings(city: City) = launchLoad {
        val date = _state.value.selectedDate ?: LocalDate.now(CITY_ZONE)
        api.screenings(city, query(date))
    }

    private fun query(date: LocalDate): ScreeningQuery {
        val f = _state.value.filters
        return ScreeningQuery(
            date = date.toString(),
            cinemaSlug = (f.event as? EventChoice.Venue)?.id ?: f.cinemaSlug,
            event = (f.event as? EventChoice.Festival)?.keyword,
            originalVersion = f.originalVersion,
            firstWeek = f.firstWeek,
        )
    }

    private fun launchLoad(block: suspend kotlinx.coroutines.CoroutineScope.() -> List<Screening>) {
        loadJob?.cancel()
        _state.update { it.copy(loading = true, error = false) }
        loadJob = viewModelScope.launch {
            try {
                val rows = block()
                _state.update { it.copy(screenings = rows, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }
}
