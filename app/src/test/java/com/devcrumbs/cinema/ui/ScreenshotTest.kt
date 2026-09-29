package com.devcrumbs.cinema.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private fun waitForData() {
        // Network happens off the main thread; let it land, then settle.
        compose.waitUntil(20_000) {
            runCatching { compose.onRoot().fetchSemanticsNode() }.isSuccess &&
                compose.onAllNodes(androidx.compose.ui.test.hasProgressBarRangeInfo(
                    androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).fetchSemanticsNodes().isEmpty()
        }
        Thread.sleep(1500)
        compose.waitForIdle()
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())

    @Test
    fun programme() {
        val vm = ScheduleViewModel(ApplicationProvider.getApplicationContext<Application>())
        vm.selectCity(vm.cities.first { it.slug == city.slug })
        compose.setContent { CinemaTheme { AppNavigation(vm, vm.state.value.copy()) } }
        compose.waitUntil(20_000) { vm.state.value.screenings.isNotEmpty() }
        compose.setContent { CinemaTheme { AppNavigation(vm, vm.state.value) } }
        waitForData()
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

    @Test
    fun cinema() {
        compose.setContent { CinemaTheme { CinemaScreen(api, city, "lumiere", onBack = {}, onOpenFilm = {}) } }
        waitForData()
        compose.onRoot().captureRoboImage("cinema.png")
    }
}
