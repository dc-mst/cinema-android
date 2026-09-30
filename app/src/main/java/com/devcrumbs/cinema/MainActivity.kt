package com.devcrumbs.cinema

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devcrumbs.cinema.push.PushNotifications
import com.devcrumbs.cinema.ui.CinemaTheme
import com.devcrumbs.cinema.ui.CityPickerScreen
import com.devcrumbs.cinema.ui.AccountViewModel
import com.devcrumbs.cinema.ui.AppNavigation
import com.devcrumbs.cinema.ui.ScheduleViewModel

class MainActivity : ComponentActivity() {
    private val schedule: ScheduleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openFromNotification(intent)
        enableEdgeToEdge()
        setContent {
            val account: AccountViewModel = viewModel()
            val accountState by account.state.collectAsStateWithLifecycle()
            CinemaTheme(dark = accountState.theme?.let { it == "dark" }) {
                val vm = schedule
                val state by vm.state.collectAsStateWithLifecycle()
                if (state.city == null || state.pickingCity) {
                    BackHandler(enabled = state.city != null) { vm.closeCityPicker() }
                    CityPickerScreen(cities = vm.cities, current = state.city, onSelect = vm::selectCity)
                } else {
                    AppNavigation(vm, state, account)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromNotification(intent)
    }

    /** A tapped push notification (push/Push.kt): the film, in the city that sent it. */
    private fun openFromNotification(intent: Intent?) {
        val film = intent?.getStringExtra(PushNotifications.EXTRA_FILM) ?: return
        schedule.openFilm(intent.getStringExtra(PushNotifications.EXTRA_CITY).orEmpty(), film)
        intent.removeExtra(PushNotifications.EXTRA_FILM)
    }
}
