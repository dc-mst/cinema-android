package com.devcrumbs.cinema.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The account client and repository against a fake city backend. Response
 * bodies are the backend's shapes (`user_payload()`, `UserWatchlist.to_dict()`,
 * `/api/user/cities`, `?all_cities=1` notifications).
 */
class AccountTest {
    private val server = MockWebServer()
    private val routes = mutableMapOf<String, (RecordedRequest) -> MockResponse>()
    private val requests = CopyOnWriteArrayList<RecordedRequest>()
    private lateinit var city: City

    private val api = AccountApi()
    private val json = Json

    @Before
    fun start() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += request
                val path = request.requestUrl!!.encodedPath
                return routes["${request.method} $path"]?.invoke(request) ?: MockResponse().setResponseCode(404)
            }
        }
        server.start()
        city = City("bologna", "Cinema Bologna", server.url("/").toString(), "Bologna")
    }

    @After
    fun stop() = server.shutdown()

    private fun route(key: String, code: Int = 200, body: (RecordedRequest) -> String) {
        routes[key] = { MockResponse().setResponseCode(code).setBody(body(it)) }
    }

    private fun bodyOf(r: RecordedRequest) = json.parseToJsonElement(r.body.readUtf8()).jsonObject

    private val userJson = """{"id": 7, "name": "Ada", "email": "ada@example.com", "language": "en",
        "origin_city": "bologna", "is_owner": false}"""

    private fun watchItem(tmdbId: Int, title: String, showing: Boolean = false) = """{"id": $tmdbId, "tmdb_id": $tmdbId,
        "title": "$title", "original_title": null, "poster_url": null, "year": 2025, "added_at": "2026-09-01T10:00:00",
        "notified_at": null, "notify_timing": "immediately", "target_date": null, "has_screenings": $showing,
        "latest_screening_date": null, "movie_key": "${normaliseTitle(title)}"}"""

    /** A logged-in backend: every list the repository loads after login. */
    private fun loggedInBackend(seen: List<String> = emptyList()) {
        route("POST /api/auth/login") { """{"token": "tok", "user": $userJson}""" }
        route("GET /api/auth/me") { """{"user": $userJson}""" }
        route("GET /api/user/seen-movies") { """{"movie_keys": ${seen.joinToString(",", "[", "]") { "\"$it\"" }}}""" }
        route("GET /api/user/watchlist") { """{"watchlist": [${watchItem(603, "Matrix", showing = true)}]}""" }
        route("GET /api/user/notifications") { """{"notifications": [], "unread_count": 2}""" }
        route("GET /api/user/preferences") { """{"preferences": {"theme": "dark", "language": "en", "selected_cinema": "x"}}""" }
    }

    private fun loggedIn(store: SessionStore = InMemorySessionStore()): AccountRepository = runBlocking {
        AccountRepository(api, store).also { it.login(city, " ada@example.com ", "secret") }
    }

    // ── Client ──

    @Test
    fun `login posts the credentials and decodes the user`() = runBlocking {
        loggedInBackend()
        val response = api.login(city, "ada@example.com", "secret")
        assertEquals("tok", response.token)
        assertEquals(AccountUser(7, "Ada", "ada@example.com", "en", "bologna"), response.user)
        val request = requests.single()
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        val body = bodyOf(request)
        assertEquals("ada@example.com", body["email"]!!.jsonPrimitive.content)
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun `authenticated calls send the bearer token`() = runBlocking {
        route("GET /api/user/notifications") {
            """{"notifications": [{"id": 1, "movie_id": 9, "movie_title": "Matrix", "poster_url": null,
                "city_slug": "milano", "created_at": "2026-09-28T18:30:00", "read_at": null, "city_name": "Milano Cinema"}],
                "unread_count": 1}"""
        }
        val response = api.notifications(city, "tok")
        assertEquals("Bearer tok", requests.single().getHeader("Authorization"))
        // Every city's notifications, each with its city.
        assertEquals("1", requests.single().requestUrl!!.queryParameter("all_cities"))
        val n = response.notifications.single()
        assertEquals("milano" to "Milano Cinema", n.citySlug to n.cityName)
        assertTrue(n.unread)
        assertEquals(1, response.unreadCount)
    }

    @Test
    fun `errors carry the status and the server's message`() = runBlocking {
        route("POST /api/auth/register", code = 409) { """{"error": "Esiste già un account con questa email."}""" }
        try {
            api.register(city, "", "ada@example.com", "secret")
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(409, e.code)
            assertEquals("Esiste già un account con questa email.", e.serverMessage)
        }
    }

    @Test
    fun `seen key with spaces is one path segment`() = runBlocking {
        route("DELETE /api/user/seen-movies/il%20grande%20lebowski") { """{"message": "ok"}""" }
        api.deleteSeenMovie(city, "tok", "il grande lebowski")
        assertEquals("DELETE", requests.single().method)
    }

    @Test
    fun `city switches send only what changed`() = runBlocking {
        route("PUT /api/user/cities/milano") {
            """{"city": {"slug": "milano", "email_enabled": false, "alerts_enabled": true, "is_origin": false, "first_seen_at": null}}"""
        }
        val row = api.setCitySwitches(city, "tok", "milano", alertsEnabled = true)
        assertEquals(setOf("alerts_enabled"), bodyOf(requests.single()).keys)
        assertTrue(row.alertsEnabled)
        assertFalse(row.emailEnabled)
    }

    @Test
    fun `cities and watchlist decode`() = runBlocking {
        route("GET /api/user/cities") {
            """{"cities": [{"slug": "bologna", "name": "Cinema Bologna", "url": "https://cinemabologna.com",
                "is_origin": true, "email_enabled": true, "alerts_enabled": true, "first_seen_at": "2026-01-01T00:00:00"}]}"""
        }
        route("GET /api/user/watchlist") { """{"watchlist": [${watchItem(603, "Matrix", showing = true)}]}""" }
        val c = api.cities(city, "tok").single()
        assertTrue(c.isOrigin && c.emailEnabled && c.alertsEnabled)
        val w = api.watchlist(city, "tok").single()
        assertEquals(603, w.tmdbId)
        assertTrue(w.hasScreenings)
        assertEquals(NotifyTiming.IMMEDIATELY, w.notifyTiming)
    }

    @Test
    fun `movies by keys are asked in batches`() = runBlocking {
        route("GET /api/movies") { r ->
            val keys = r.requestUrl!!.queryParameter("keys")!!.split(",")
            keys.joinToString(",", "[", "]") { """{"id": 1, "title": "${it.uppercase()}", "screenings": []}""" }
        }
        val keys = (1..130).map { "film $it" }
        val movies = api.moviesByKeys(city, keys)
        assertEquals(3, requests.size)
        assertEquals(130, movies.size)
        assertEquals(emptyList<KeyedMovie>(), api.moviesByKeys(city, emptyList()))
        assertEquals(3, requests.size)
    }

    // ── Repository ──

    @Test
    fun `login stores the session and loads the lists`() {
        loggedInBackend(seen = listOf("matrix"))
        val store = InMemorySessionStore()
        val repo = loggedIn(store)
        val state = repo.state.value
        assertEquals("tok", store.load()?.token)
        assertEquals("ada@example.com", bodyOf(requests.first()).getValue("email").jsonPrimitive.content) // trimmed
        assertEquals(setOf("matrix"), state.seenKeys)
        assertEquals(setOf(603), state.watchlistIds)
        assertEquals(2, state.unreadCount)
        assertEquals("dark", state.theme)
        assertTrue(state.isSeen("Matrix!"))
        // A new repository (next launch) starts logged in.
        assertTrue(AccountRepository(api, store).state.value.loggedIn)
    }

    @Test
    fun `a 401 logs out`() = runBlocking {
        loggedInBackend()
        val store = InMemorySessionStore()
        val repo = loggedIn(store)
        route("GET /api/user/cities", code = 401) { """{"error": "Token scaduto. Accedi di nuovo."}""" }
        try {
            repo.cities(city)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(401, e.code)
        }
        assertFalse(repo.state.value.loggedIn)
        assertNull(store.load())
    }

    @Test
    fun `offline refresh keeps the login`() = runBlocking {
        loggedInBackend()
        val repo = loggedIn()
        server.shutdown()
        repo.refresh(city)
        assertTrue(repo.state.value.loggedIn)
        assertEquals(setOf(603), repo.state.value.watchlistIds)
    }

    @Test
    fun `marking seen merges with the server's list`() = runBlocking {
        loggedInBackend()
        val repo = loggedIn()
        // The website marked another film since the app loaded the list.
        route("GET /api/user/seen-movies") { """{"movie_keys": ["dune"]}""" }
        var sent: List<String> = emptyList()
        route("PUT /api/user/seen-movies") { r ->
            bodyOf(r).also { body -> sent = body["movie_keys"]!!.jsonArray.map { it.jsonPrimitive.content } }.toString()
        }
        repo.setSeen(city, "L'Été", seen = true)
        assertEquals(listOf("dune", "lete"), sent)
        assertEquals(setOf("dune", "lete"), repo.state.value.seenKeys)
    }

    @Test
    fun `a refused change is rolled back`() = runBlocking {
        loggedInBackend(seen = listOf("matrix"))
        val repo = loggedIn()
        route("DELETE /api/user/seen-movies/matrix", code = 500) { """{"error": "Errore"}""" }
        try {
            repo.setSeen(city, "Matrix", seen = false)
            fail("expected ApiException")
        } catch (e: ApiException) {
            assertEquals(500, e.code)
        }
        assertEquals(setOf("matrix"), repo.state.value.seenKeys)
        assertTrue(repo.state.value.loggedIn)
    }

    @Test
    fun `watchlist add, duplicate and mark seen`() = runBlocking {
        loggedInBackend()
        val repo = loggedIn()
        route("POST /api/user/watchlist", code = 201) { r ->
            val body = bodyOf(r)
            assertEquals("2026-10-01", body["target_date"]!!.jsonPrimitive.content)
            """{"watchlist_item": ${watchItem(438631, "Dune")}}"""
        }
        repo.addToWatchlist(city, WatchlistAdd(438631, "Dune", year = 2021, targetDate = "2026-10-01"))
        assertEquals(listOf(438631, 603), repo.state.value.watchlist.map { it.tmdbId })

        // Already on the list (added on the website): reload instead of failing.
        route("POST /api/user/watchlist", code = 409) { """{"error": "Film già presente nella watchlist."}""" }
        repo.addToWatchlist(city, WatchlistAdd(603, "Matrix"))
        assertEquals(listOf(603), repo.state.value.watchlist.map { it.tmdbId })

        route("POST /api/user/watchlist/603/mark-seen") { """{"message": "ok", "movie_key": "matrix"}""" }
        repo.markWatchlistSeen(city, 603)
        assertTrue(repo.state.value.watchlist.isEmpty())
        assertTrue("matrix" in repo.state.value.seenKeys)
    }

    @Test
    fun `logout forgets everything`() {
        loggedInBackend(seen = listOf("matrix"))
        val store = InMemorySessionStore()
        val repo = loggedIn(store)
        repo.logout()
        assertEquals(AccountState(), repo.state.value)
        assertNull(store.load())
    }

    private class FakePush(val token: String? = "fcm-1") : PushDevice {
        var forgotten = 0
        override suspend fun token() = token
        override fun forget() { forgotten++ }
    }

    @Test
    fun `login registers this phone for push`() = runBlocking {
        loggedInBackend()
        route("POST /api/push/device") { """{"message": "ok", "push_configured": true}""" }
        val push = FakePush()
        AccountRepository(api, InMemorySessionStore(), push).login(city, "ada@example.com", "secret")
        val request = requests.single { it.requestUrl!!.encodedPath == "/api/push/device" }
        assertEquals("Bearer tok", request.getHeader("Authorization"))
        val body = bodyOf(request)
        assertEquals("fcm-1", body["token"]!!.jsonPrimitive.content)
        assertEquals("android", body["platform"]!!.jsonPrimitive.content)
    }

    /** A push device whose token is missing for the first [missing] calls, as before Firebase is ready. */
    private class LateToken(private var missing: Int) : PushDevice {
        override suspend fun token(): String? = if (missing-- > 0) null else "fcm-late"
        override fun forget() = Unit
    }

    private fun pushRegistrations() = requests.count { it.requestUrl!!.encodedPath == "/api/push/device" }

    @Test
    fun `a missing FCM token is retried in the background and then registers`() = runBlocking {
        loggedInBackend()
        route("POST /api/push/device") { """{"message": "ok"}""" }
        val problems = CopyOnWriteArrayList<String>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            AccountRepository(
                api, InMemorySessionStore(), LateToken(missing = 2),
                pushRetryDelaysMs = listOf(20, 20, 20), pushScope = scope, onPushProblem = { problems += it },
            ).login(city, "ada@example.com", "secret")
            assertEquals(0, pushRegistrations()) // first attempt found no token
            withTimeout(5_000) { while (pushRegistrations() == 0) delay(10) }
            assertEquals("fcm-late", bodyOf(requests.last { it.requestUrl!!.encodedPath == "/api/push/device" })["token"]!!.jsonPrimitive.content)
            assertEquals(listOf("no FCM token available", "no FCM token available"), problems.toList())
        } finally { scope.cancel() }
    }

    @Test
    fun `retries stop at logout and a build without push never retries`() = runBlocking {
        loggedInBackend()
        route("POST /api/push/device") { """{"message": "ok"}""" }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repo = AccountRepository(
                api, InMemorySessionStore(), LateToken(missing = 99),
                pushRetryDelaysMs = listOf(150), pushScope = scope,
            ).also { it.login(city, "ada@example.com", "secret") }
            repo.logout()
            delay(400)
            assertEquals(0, pushRegistrations())
            val problems = CopyOnWriteArrayList<String>()
            AccountRepository(api, InMemorySessionStore(), NoPushDevice, pushScope = scope, onPushProblem = { problems += it })
                .login(city, "ada@example.com", "secret")
            assertEquals(emptyList<String>(), problems.toList())
        } finally { scope.cancel() }
    }

    @Test
    fun `sign out unregisters the phone, then forgets its token`() = runBlocking {
        loggedInBackend()
        route("POST /api/push/device") { """{"message": "ok"}""" }
        route("DELETE /api/push/device") { """{"message": "ok", "removed": 1}""" }
        val push = FakePush()
        val repo = AccountRepository(api, InMemorySessionStore(), push).also { it.login(city, "ada@example.com", "secret") }
        repo.signOut(city)
        val delete = requests.single { it.method == "DELETE" }
        assertEquals("fcm-1", bodyOf(delete)["token"]!!.jsonPrimitive.content)
        assertFalse(repo.state.value.loggedIn)
        assertEquals(1, push.forgotten)
    }

    @Test
    fun `sign out works offline and an expired session still forgets the token`() = runBlocking {
        loggedInBackend()
        val push = FakePush()
        val repo = AccountRepository(api, InMemorySessionStore(), push).also { it.login(city, "ada@example.com", "secret") }
        route("GET /api/user/cities", code = 401) { """{"error": "Token scaduto."}""" }
        runCatching { repo.cities(city) }
        assertFalse(repo.state.value.loggedIn)
        assertEquals(1, push.forgotten) // the server row dies with the token

        val repo2 = AccountRepository(api, InMemorySessionStore(), push).also { it.login(city, "ada@example.com", "secret") }
        server.shutdown()
        repo2.signOut(city)
        assertFalse(repo2.state.value.loggedIn)
        assertEquals(2, push.forgotten)
    }

    @Test
    fun `new films switch is read and written in each city`() = runBlocking {
        loggedInBackend()
        val repo = loggedIn()
        route("GET /api/user/preferences") { """{"preferences": {"app_new_movies": true, "theme": "dark"}}""" }
        val unreachable = City("torino", "Cinema Torino", "http://127.0.0.1:1/", "Torino")
        assertEquals(mapOf("bologna" to true), repo.newFilmsSwitches(listOf(city, unreachable)))

        var sent: String? = null
        route("PUT /api/user/preferences") { r -> sent = r.body.readUtf8(); """{"preferences": {}}""" }
        repo.setNewFilms(city, false)
        assertEquals("""{"preferences":{"app_new_movies":false}}""", sent)
    }

    @Test
    fun `actions need a login`() = runBlocking {
        val repo = AccountRepository(api, InMemorySessionStore())
        try {
            repo.cities(city)
            fail("expected NotLoggedInException")
        } catch (_: NotLoggedInException) {
        }
        repo.refresh(city) // no-op
        assertTrue(requests.isEmpty())
    }
}
