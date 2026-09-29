package com.devcrumbs.cinema

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devcrumbs.cinema.ui.CinemaTheme
import com.devcrumbs.cinema.ui.CityPickerScreen
import com.devcrumbs.cinema.ui.ScheduleScreen
import com.devcrumbs.cinema.ui.ScheduleViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CinemaTheme {
                val vm: ScheduleViewModel = viewModel()
                val state by vm.state.collectAsStateWithLifecycle()
                if (state.city == null || state.pickingCity) {
                    BackHandler(enabled = state.city != null) { vm.closeCityPicker() }
                    CityPickerScreen(cities = vm.cities, current = state.city, onSelect = vm::selectCity)
                } else {
                    ScheduleScreen(
                        state = state,
                        onChangeCity = vm::openCityPicker,
                        onRefresh = vm::refresh,
                        onSelectDate = vm::selectDate,
                        onSelectCinema = vm::selectCinema,
                    )
                }
            }
        }
    }
}
