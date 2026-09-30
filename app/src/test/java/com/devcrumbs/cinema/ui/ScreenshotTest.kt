package com.devcrumbs.cinema.ui

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.runtime.CompositionLocalProvider
import com.devcrumbs.cinema.data.AccountApi
import com.devcrumbs.cinema.data.AccountRepository
import com.devcrumbs.cinema.data.AccountUser
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.InMemorySessionStore
import com.devcrumbs.cinema.data.ScreeningQuery
import com.devcrumbs.cinema.data.Session
import com.devcrumbs.cinema.data.normaliseTitle
import com.devcrumbs.cinema.data.slugify
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDate
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows.shadowOf

/**
 * Renders the real screens on the JVM (Robolectric) with LIVE data, to look at
 * without a device. Opt-in (network):
 *   SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*'
 * Images: app/build/outputs/roborazzi/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val city = City("bologna", "Cinema Bologna", "https://cinemabologna.com", "Bologna")
    private val api = CinemaApi()

    @Before
    fun optIn() = assumeTrue(System.getenv("SCREENSHOTS") == "1")

    /** Network runs on IO threads; results land on Robolectric's paused main
     *  looper, so drive it until [done] (and nothing is loading), then settle. */
    private fun waitForData(done: () -> Boolean = { true }) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(250)
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            val loading = compose.onAllNodes(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
                .fetchSemanticsNodes().isNotEmpty()
            if (done() && !loading) break
        }
        Thread.sleep(1500) // posters (Coil, own dispatcher)
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun ComposeContentTestRule.onRoot() = onNode(isRoot())

    @Test
    fun programme() {
        val vm = ScheduleViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.selectCity(vm.cities.first { it.slug == city.slug })
        compose.setContent {
            val state by vm.state.collectAsState()
            CinemaTheme { AppNavigation(vm, state) }
        }
        waitForData { vm.state.value.screenings.isNotEmpty() }
        compose.onRoot().captureRoboImage("programme.png")
    }

    @Test
    fun topMovies() {
        compose.setContent { CinemaTheme { TopMoviesScreen(api, city, onOpenDetail = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("top.png")
    }

    @Test
    fun film() {
        val slug = runBlocking { api.topMovies(city).movies.first().slug!! }
        compose.setContent { CinemaTheme { FilmScreen(api, city, slug, onBack = {}, onOpenCinema = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("film.png")
    }

    // ── Account (logged in with canned account data; programme data is live) ──

    /**
     * Account endpoints answered on the device — a test must not log in to the
     * live sites. A film of today's programme is on the watchlist and another
     * is marked seen, so the marks show on the live screens too.
     */
    private fun loggedInAccount(): AccountViewModel {
        val films = runBlocking { api.screenings(city, ScreeningQuery(LocalDate.now(CITY_ZONE).toString())) }
            .filter { it.movieTmdbId != null }.distinctBy { it.movieId }
        val listed = films.first()
        val seen = films.drop(1).first()
        val watchlist = """[
            {"id": 1, "tmdb_id": ${listed.movieTmdbId}, "title": "${listed.movieTitle}", "poster_url": "${listed.moviePosterUrl}",
             "year": ${listed.movieYear}, "notify_timing": "same_day", "target_date": "${listed.date}", "has_screenings": true},
            {"id": 2, "tmdb_id": 27205, "title": "Inception", "original_title": "Inception", "year": 2010,
             "poster_url": "https://image.tmdb.org/t/p/w500/oYuLEt3zVCKq57qu2F8dT7NIa6f.jpg", "has_screenings": false}]"""
        val canned = mapOf(
            "/api/auth/me" to """{"user": {"id": 1, "name": "Ada", "email": "ada@example.com", "language": "it", "origin_city": "bologna"}}""",
            "/api/user/watchlist" to """{"watchlist": $watchlist}""",
            "/api/user/seen-movies" to """{"movie_keys": ["${normaliseTitle(seen.movieTitle.orEmpty())}", "il grande lebowski"]}""",
            "/api/user/notifications" to """{"unread_count": 1, "notifications": [
                {"id": 2, "movie_title": "${listed.movieTitle}", "poster_url": "${listed.moviePosterUrl}", "city_slug": "bologna",
                 "city_name": "Cinema Bologna", "created_at": "2026-09-28T16:05:00", "read_at": null},
                {"id": 1, "movie_title": "Inception", "city_slug": "milano", "city_name": "Cinema Milano",
                 "created_at": "2026-09-20T08:00:00", "read_at": "2026-09-21T08:00:00"}]}""",
            "/api/user/notifications/read-all" to """{"message": "ok"}""",
            "/api/user/cities" to """{"cities": [
                {"slug": "bologna", "name": "Cinema Bologna", "is_origin": true, "email_enabled": true, "alerts_enabled": true},
                {"slug": "firenze", "name": "Cinema Firenze", "email_enabled": false, "alerts_enabled": false},
                {"slug": "milano", "name": "Cinema Milano", "email_enabled": false, "alerts_enabled": true},
                {"slug": "torino", "name": "Cinema Torino", "email_enabled": false, "alerts_enabled": false}]}""",
            "/api/user/preferences" to """{"preferences": {"app_new_movies": true}}""",
        )
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            val body = canned[chain.request().url.encodedPath] ?: return@Interceptor chain.proceed(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build()
        val session = Session("token", AccountUser(1, "Ada", "ada@example.com", "it", "bologna"))
        val repo = AccountRepository(AccountApi(client), InMemorySessionStore(session))
        runBlocking { repo.refresh(city) }
        return AccountViewModel(ApplicationProvider.getApplicationContext(), repo)
    }

    @Test
    fun accountLoggedOut() {
        val account = AccountViewModel(ApplicationProvider.getApplicationContext(), AccountRepository(AccountApi(), InMemorySessionStore()))
        compose.setContent { CinemaTheme { AccountScreen(account, city, listOf(city), onOpen = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-login.png")
    }

    @Test
    fun accountProfile() {
        val account = loggedInAccount()
        compose.setContent { CinemaTheme { AccountScreen(account, city, listOf(city), onOpen = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-profile.png")
    }

    @Test
    fun watchlist() {
        val account = loggedInAccount()
        compose.setContent { CinemaTheme { WatchlistScreen(account, city, onBack = {}, onOpenFilm = {}, onOpenTmdb = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-watchlist.png")
    }

    @Test
    fun seen() {
        val account = loggedInAccount()
        compose.setContent { CinemaTheme { SeenScreen(account, city, onBack = {}, onOpenFilm = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-seen.png")
    }

    @Test
    fun notifications() {
        val account = loggedInAccount()
        compose.setContent { CinemaTheme { NotificationsScreen(account, city, onBack = {}, onOpen = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-notifications.png")
    }

    @Test
    fun citySwitches() {
        val account = loggedInAccount()
        compose.setContent { CinemaTheme { CitySwitchesScreen(account, city, ScheduleViewModel(ApplicationProvider.getApplicationContext<Application>()).cities, onBack = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("account-cities.png")
    }

    @Test
    fun programmeLoggedIn() {
        val account = loggedInAccount()
        val vm = ScheduleViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.selectCity(vm.cities.first { it.slug == city.slug })
        compose.setContent {
            val state by vm.state.collectAsState()
            CinemaTheme { AppNavigation(vm, state, account) }
        }
        waitForData { vm.state.value.screenings.isNotEmpty() }
        compose.onRoot().captureRoboImage("programme-logged-in.png")
    }

    @Test
    fun filmLoggedIn() {
        val account = loggedInAccount()
        val title = account.state.value.watchlist.first().title
        compose.setContent {
            val state by account.state.collectAsState()
            CinemaTheme {
                CompositionLocalProvider(LocalAccount provides AccountHooks(account) {}, LocalAccountState provides state) {
                    FilmScreen(api, city, slugify(title), onBack = {}, onOpenCinema = {})
                }
            }
        }
        waitForData()
        compose.onRoot().captureRoboImage("film-logged-in.png")
    }

    @Test
    fun cinema() {
        compose.setContent { CinemaTheme { CinemaScreen(api, city, "lumiere", onBack = {}, onOpenFilm = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("cinema.png")
    }
}
