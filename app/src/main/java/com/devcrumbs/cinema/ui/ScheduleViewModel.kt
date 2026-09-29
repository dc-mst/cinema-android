package com.devcrumbs.cinema.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.CityRepository
import com.devcrumbs.cinema.data.Screening
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** Every city is in Italy; "today" follows the servers' Europe/Rome day. */
val CITY_ZONE: ZoneId = ZoneId.of("Europe/Rome")

data class ScheduleUiState(
    val city: City? = null,
    val dates: List<LocalDate> = emptyList(),
    val selectedDate: LocalDate? = null,
    val screenings: List<Screening> = emptyList(),
    val selectedCinemaId: Int? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    /** City picker shown over an already-chosen city (back returns to it). */
    val pickingCity: Boolean = false,
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CityRepository(app)
    private val api = CinemaApi()

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
        _state.update { it.copy(selectedDate = date, selectedCinemaId = null) }
        loadScreenings(city, date)
    }

    fun selectCinema(cinemaId: Int?) = _state.update { it.copy(selectedCinemaId = cinemaId) }

    fun refresh() {
        val current = _state.value
        val city = current.city ?: return
        if (current.dates.isEmpty() || current.selectedDate == null) loadCity(city)
        else loadScreenings(city, current.selectedDate)
    }

    private fun loadCity(city: City) = launchLoad {
        val today = LocalDate.now(CITY_ZONE)
        val dates = api.calendar(city).map(LocalDate::parse).filter { !it.isBefore(today) }
        val date = dates.firstOrNull() ?: today
        _state.update { it.copy(dates = dates, selectedDate = date) }
        api.screenings(city, date.toString())
    }

    private fun loadScreenings(city: City, date: LocalDate) = launchLoad {
        api.screenings(city, date.toString())
    }

    private fun launchLoad(block: suspend () -> List<Screening>) {
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
