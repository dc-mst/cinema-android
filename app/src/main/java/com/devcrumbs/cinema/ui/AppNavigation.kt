package com.devcrumbs.cinema.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.slugify

private enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    PROGRAMME("programme", R.string.tab_programme, Icons.Filled.DateRange),
    TOP("top", R.string.tab_top, Icons.Filled.Star),
    SEARCH("search", R.string.tab_search, Icons.Filled.Search),
    ACCOUNT(ROUTE_ACCOUNT, R.string.tab_account, Icons.Filled.Person),
}

private fun enc(s: String): String = Uri.encode(s)

private fun NavHostController.openFilm(slug: String) = navigate("film/${enc(slug)}")
private fun NavHostController.openCinema(slug: String) = navigate("cinema/${enc(slug)}")
private fun NavHostController.openTab(tab: Tab) = navigate(tab.route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

/** Bottom-bar tabs + detail pages, all for the chosen city (the picker sits above this). */
@Composable
fun AppNavigation(vm: ScheduleViewModel, state: ScheduleUiState, account: AccountViewModel = viewModel()) {
    val city = state.city ?: return
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val accountState by account.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    // A different city: every open page belongs to the old one.
    LaunchedEffect(city.slug) {
        nav.popBackStack(Tab.PROGRAMME.route, inclusive = false)
        account.onCity(city)
    }
    // A notification's film (push or the notifications list), once its city is shown.
    LaunchedEffect(city.slug, state.pendingFilm) {
        state.pendingFilm?.let { vm.consumePendingFilm(); nav.openFilm(it) }
    }
    AskNotificationPermission(accountState.loggedIn)
    LaunchedEffect(Unit) { account.notices.collect { snackbar.showSnackbar(context.getString(it)) } }

    val hooks = remember(account, nav) { AccountHooks(account) { nav.openTab(Tab.ACCOUNT) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = { nav.openTab(tab) },
                        icon = {
                            val unread = if (tab == Tab.ACCOUNT) accountState.unreadCount else 0
                            BadgedBox(badge = { if (unread > 0) androidx.compose.material3.Badge { Text("$unread") } }) {
                                Icon(tab.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalAccount provides hooks, LocalAccountState provides accountState) {
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
                composable(ROUTE_ACCOUNT) {
                    AccountScreen(account, city, vm.cities, onOpen = { nav.navigate(it) })
                }
                composable(ROUTE_WATCHLIST) {
                    WatchlistScreen(account, city, onBack = { nav.popBackStack() },
                        onOpenFilm = nav::openFilm, onOpenTmdb = { id -> nav.navigate("tmdb/$id") })
                }
                composable(ROUTE_SEEN) {
                    SeenScreen(account, city, onBack = { nav.popBackStack() }, onOpenFilm = nav::openFilm)
                }
                composable(ROUTE_NOTIFICATIONS) {
                    NotificationsScreen(account, city, onBack = { nav.popBackStack() }, onOpen = { n ->
                        vm.openFilm(n.citySlug, slugify(n.movieTitle.orEmpty()))
                    })
                }
                composable(ROUTE_CITIES) {
                    CitySwitchesScreen(account, city, vm.cities, onBack = { nav.popBackStack() })
                }
            }
        }
    }
}
