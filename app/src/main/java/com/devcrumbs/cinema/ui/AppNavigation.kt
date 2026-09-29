package com.devcrumbs.cinema.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.devcrumbs.cinema.R

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    PROGRAMME("programme", R.string.tab_programme, Icons.Filled.DateRange),
    TOP("top", R.string.tab_top, Icons.Filled.Star),
    SEARCH("search", R.string.tab_search, Icons.Filled.Search),
}

private fun enc(s: String): String = Uri.encode(s)

private fun NavHostController.openFilm(slug: String) = navigate("film/${enc(slug)}")
private fun NavHostController.openCinema(slug: String) = navigate("cinema/${enc(slug)}")

/** Bottom-bar tabs + detail pages, all for the chosen city (the picker sits above this). */
@Composable
fun AppNavigation(vm: ScheduleViewModel, state: ScheduleUiState) {
    val city = state.city ?: return
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    // A different city: every open page belongs to the old one.
    LaunchedEffect(city.slug) { nav.popBackStack(Tab.PROGRAMME.route, inclusive = false) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.PROGRAMME.route, modifier = Modifier.padding(padding)) {
            composable(Tab.PROGRAMME.route) {
                ScheduleScreen(vm, state, onOpenFilm = nav::openFilm, onOpenCinema = nav::openCinema)
            }
            composable(Tab.TOP.route) {
                TopMoviesScreen(vm.api, city, onOpenDetail = { key -> nav.navigate("top/${enc(key)}") })
            }
            composable(Tab.SEARCH.route) {
                SearchScreen(
                    vm.api, city,
                    onOpenFilm = nav::openFilm,
                    onOpenTmdb = { id -> nav.navigate("tmdb/$id") },
                    onOpenGenre = { slug -> nav.navigate("genre/${enc(slug)}") },
                )
            }
            composable("film/{slug}", listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                FilmScreen(vm.api, city, entry.arguments?.getString("slug").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenCinema = nav::openCinema)
            }
            composable("tmdb/{id}", listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                TmdbFilmScreen(vm.api, city, entry.arguments?.getInt("id") ?: 0, onBack = { nav.popBackStack() })
            }
            composable("cinema/{slug}", listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                CinemaScreen(vm.api, city, entry.arguments?.getString("slug").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenFilm = nav::openFilm)
            }
            composable("genre/{slug}", listOf(navArgument("slug") { type = NavType.StringType })) { entry ->
                GenreScreen(vm.api, city, entry.arguments?.getString("slug").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenFilm = nav::openFilm, onOpenCinema = nav::openCinema)
            }
            composable("top/{key}", listOf(navArgument("key") { type = NavType.StringType })) { entry ->
                TopFilmScreen(vm.api, city, entry.arguments?.getString("key").orEmpty(),
                    onBack = { nav.popBackStack() }, onOpenFilm = nav::openFilm)
            }
        }
    }
}
