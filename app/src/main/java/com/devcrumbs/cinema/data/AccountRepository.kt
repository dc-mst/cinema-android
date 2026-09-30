package com.devcrumbs.cinema.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * This install's push identity (Firebase Cloud Messaging), kept out of the
 * repository so tests and previews need no Firebase.
 */
interface PushDevice {
    /** False when this build has no push at all (tests, previews): nothing to register or retry. */
    val available: Boolean get() = true

    /** The current FCM token, or null when push is unavailable. */
    suspend fun token(): String?

    /** Invalidates the token (fire and forget): the server then drops it on its next send. */
    fun forget()
}

object NoPushDevice : PushDevice {
    override val available = false
    override suspend fun token(): String? = null
    override fun forget() = Unit
}

/** An account action was asked for while logged out. */
class NotLoggedInException : IllegalStateException("Not logged in")

/** What the rest of the app needs to know about the person. */
data class AccountState(
    val session: Session? = null,
    /** "Già visti": `normaliseTitle(title)` keys. */
    val seenKeys: Set<String> = emptySet(),
    /** "Voglio vederlo", as the selected city reports it (`has_screenings` is that city's). */
    val watchlist: List<WatchlistItem> = emptyList(),
    /** Unread notifications from every city. */
    val unreadCount: Int = 0,
    /** Person-level theme preference ("light" | "dark"), shared with the websites; null = follow the system. */
    val theme: String? = null,
) {
    val user: AccountUser? get() = session?.user
    val loggedIn: Boolean get() = session != null
    val watchlistIds: Set<Int> get() = watchlist.mapTo(HashSet()) { it.tmdbId }

    fun isSeen(title: String): Boolean = normaliseTitle(title) in seenKeys
}

/**
 * Login state and the person's lists. Changes are applied to [state] at once
 * and rolled back if the server refuses them; a `401` on any call means the
 * token expired (30 days) or the account is gone, and logs out.
 */
class AccountRepository(
    private val api: AccountApi,
    private val store: SessionStore,
    private val push: PushDevice = NoPushDevice,
    /** Waits before each background retry of a failed push registration. */
    private val pushRetryDelaysMs: List<Long> = listOf(5_000, 30_000, 120_000, 600_000),
    private val pushScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    /** Why a push registration attempt failed (the app logs it; tests collect it). */
    private val onPushProblem: (String) -> Unit = {},
) {
    private var pushRetry: Job? = null
    private val _state = MutableStateFlow(AccountState(session = store.load()))
    val state: StateFlow<AccountState> = _state.asStateFlow()

    // ── Login ──

    /** Registering through [city] makes it the account's origin city. */
    suspend fun register(city: City, name: String, email: String, password: String) {
        val response = api.register(city, name.trim(), email.trim(), password)
        start(city, Session(response.token, response.user))
    }

    suspend fun login(city: City, email: String, password: String) {
        val response = api.login(city, email.trim(), password)
        start(city, Session(response.token, response.user))
    }

    suspend fun forgotPassword(city: City, email: String): String? = api.forgotPassword(city, email.trim())

    /**
     * Logs out here. Also invalidates this install's push token: when the
     * session expired (401) the server row cannot be removed with it, and the
     * phone must stop receiving the old account's alerts anyway.
     */
    fun logout() {
        pushRetry?.cancel()
        store.save(null)
        _state.value = AccountState()
        push.forget()
    }

    /** The user's own logout: unregisters this phone first (best-effort), then [logout]. */
    suspend fun signOut(city: City) {
        val session = _state.value.session
        if (session != null) {
            runCatching { push.token()?.let { api.removeDevice(city, session.token, it) } }
        }
        logout()
    }

    /**
     * Registers this install for push (after login, at start, when FCM rotates
     * the token). The first attempt runs here; if it fails — no FCM token yet
     * (first launch, phone locked, offline) or the server call errors — it is
     * retried in the background with growing waits, so a transient failure no
     * longer leaves the phone silently without push until the next app start.
     */
    suspend fun registerDevice(city: City, deviceToken: String? = null) {
        if (!_state.value.loggedIn || !push.available) return
        if (tryRegisterDevice(city, deviceToken)) return
        pushRetry?.cancel()
        pushRetry = pushScope.launch {
            for (wait in pushRetryDelaysMs) {
                delay(wait)
                if (!isActive || !_state.value.loggedIn) return@launch
                if (tryRegisterDevice(city, deviceToken)) return@launch
            }
            onPushProblem("push registration gave up after ${pushRetryDelaysMs.size} retries")
        }
    }

    private suspend fun tryRegisterDevice(city: City, deviceToken: String?): Boolean {
        val token = deviceToken ?: runCatching { push.token() }.getOrNull()
        if (token == null) {
            onPushProblem("no FCM token available")
            return false
        }
        return runCatching { authed { api.registerDevice(city, it, token) } }
            .onFailure { onPushProblem("registering the device failed: ${it.message}") }
            .isSuccess
    }

    private suspend fun start(city: City, session: Session) {
        store.save(session)
        _state.value = AccountState(session = session)
        refresh(city)
    }

    /**
     * Reloads everything for [city]: user, seen list, watchlist, unread count,
     * theme. Offline keeps what is known; a 401 logs out.
     */
    suspend fun refresh(city: City) {
        if (!_state.value.loggedIn) return
        val user = runCatching { authed { api.me(city, it) } }.getOrNull()
        if (!_state.value.loggedIn) return
        if (user != null) updateSession { it.copy(user = user) }
        runCatching { authed { api.seenMovies(city, it) } }.onSuccess { keys -> _state.update { it.copy(seenKeys = keys.toSet()) } }
        refreshWatchlist(city)
        runCatching { authed { api.notifications(city, it) } }.onSuccess { r -> _state.update { it.copy(unreadCount = r.unreadCount) } }
        runCatching { authed { api.preferences(city, it) } }.onSuccess { prefs ->
            val theme = prefs["theme"]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
            _state.update { it.copy(theme = theme?.takeIf { t -> t == "light" || t == "dark" }) }
        }
        registerDevice(city)
    }

    /** `has_screenings` depends on the city asked: call again after a city change. */
    suspend fun refreshWatchlist(city: City) {
        if (!_state.value.loggedIn) return
        runCatching { authed { api.watchlist(city, it) } }.onSuccess { list -> _state.update { it.copy(watchlist = list) } }
    }

    // ── Già visti ──

    /**
     * Marks [title] seen or not. Marking merges into the server's current list
     * (the website may have added some since this app last loaded it).
     */
    suspend fun setSeen(city: City, title: String, seen: Boolean) {
        val key = normaliseTitle(title)
        if (key.isEmpty()) return
        val before = _state.value.seenKeys
        _state.update { it.copy(seenKeys = if (seen) it.seenKeys + key else it.seenKeys - key) }
        try {
            authed { token ->
                if (seen) {
                    val server = api.seenMovies(city, token)
                    val saved = api.putSeenMovies(city, token, (server + key).distinct())
                    _state.update { it.copy(seenKeys = saved.toSet()) }
                } else {
                    try {
                        api.deleteSeenMovie(city, token, key)
                    } catch (e: ApiException) {
                        if (e.code != 404) throw e // already gone
                    }
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(seenKeys = before) }
            throw e
        }
    }

    suspend fun seenMovies(city: City): List<KeyedMovie> = api.moviesByKeys(city, _state.value.seenKeys)

    // ── Voglio vederlo ──

    suspend fun addToWatchlist(city: City, add: WatchlistAdd) {
        val item = try {
            authed { api.addToWatchlist(city, it, add) }
        } catch (e: ApiException) {
            if (e.code != 409) throw e
            null // already on the list (added elsewhere): just reload
        }
        if (item != null) _state.update { s -> s.copy(watchlist = listOf(item) + s.watchlist.filter { it.tmdbId != item.tmdbId }) }
        else refreshWatchlist(city)
    }

    suspend fun removeFromWatchlist(city: City, tmdbId: Int) {
        val before = _state.value.watchlist
        _state.update { s -> s.copy(watchlist = s.watchlist.filter { it.tmdbId != tmdbId }) }
        try {
            authed { token ->
                try {
                    api.removeFromWatchlist(city, token, tmdbId)
                } catch (e: ApiException) {
                    if (e.code != 404) throw e
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(watchlist = before) }
            throw e
        }
    }

    suspend fun setNotifyTiming(city: City, tmdbId: Int, timing: String) {
        val item = authed { api.setNotifyTiming(city, it, tmdbId, timing) }
        _state.update { s -> s.copy(watchlist = s.watchlist.map { if (it.tmdbId == tmdbId) item else it }) }
    }

    /** Off the watchlist, onto "Già visti" (one server call does both). */
    suspend fun markWatchlistSeen(city: City, tmdbId: Int) {
        val key = authed { api.markWatchlistSeen(city, it, tmdbId) }
        _state.update { s ->
            s.copy(
                watchlist = s.watchlist.filter { it.tmdbId != tmdbId },
                seenKeys = if (key.isNullOrEmpty()) s.seenKeys else s.seenKeys + key,
            )
        }
    }

    // ── Cities, notifications, preferences ──

    suspend fun cities(city: City): List<AccountCity> = authed { api.cities(city, it) }

    suspend fun setCitySwitches(city: City, slug: String, emailEnabled: Boolean? = null, alertsEnabled: Boolean? = null): AccountCity =
        authed { api.setCitySwitches(city, it, slug, emailEnabled, alertsEnabled) }

    /**
     * "New films" push per city. Each city keeps this switch in its own
     * database, so it is read and written through that city's own API (the
     * token is valid everywhere). A city that cannot be reached is left out.
     */
    suspend fun newFilmsSwitches(cities: List<City>): Map<String, Boolean> = coroutineScope {
        cities.map { c ->
            async {
                runCatching { authed { api.preferences(c, it) } }.getOrNull()?.let { prefs ->
                    c.slug to (prefs[NEW_FILMS_PREF]?.let { runCatching { it.jsonPrimitive.booleanOrNull }.getOrNull() } ?: false)
                }
            }
        }.awaitAll().filterNotNull().toMap()
    }

    suspend fun setNewFilms(target: City, on: Boolean) {
        authed { api.putPreferences(target, it, buildJsonObject { put(NEW_FILMS_PREF, JsonPrimitive(on)) }) }
    }

    suspend fun notifications(city: City): NotificationsResponse {
        val response = authed { api.notifications(city, it) }
        _state.update { it.copy(unreadCount = response.unreadCount) }
        return response
    }

    suspend fun readAllNotifications(city: City) {
        authed { api.readAllNotifications(city, it) }
        _state.update { it.copy(unreadCount = 0) }
    }

    /** Language of the emails and of the websites ("it" | "en"), shared by every city. */
    suspend fun setLanguage(city: City, language: String) {
        authed { api.putPreferences(city, it, buildJsonObject { put("language", JsonPrimitive(language)) }) }
        updateSession { it.copy(user = it.user.copy(language = language)) }
    }

    suspend fun setTheme(city: City, theme: String) {
        val before = _state.value.theme
        _state.update { it.copy(theme = theme) }
        try {
            authed { api.putPreferences(city, it, buildJsonObject { put("theme", JsonPrimitive(theme)) }) }
        } catch (e: Exception) {
            _state.update { it.copy(theme = before) }
            throw e
        }
    }

    // ── Plumbing ──

    companion object {
        /** City-scoped preference the backend's new-films push reads (`push.APP_NEW_MOVIES_PREF_KEY`). */
        const val NEW_FILMS_PREF = "app_new_movies"
    }

    private fun updateSession(change: (Session) -> Session) {
        val current = _state.value.session ?: return
        val updated = change(current)
        store.save(updated)
        _state.update { it.copy(session = updated) }
    }

    private suspend fun <T> authed(block: suspend (String) -> T): T {
        val session = _state.value.session ?: throw NotLoggedInException()
        try {
            return block(session.token)
        } catch (e: ApiException) {
            // Only the token that failed: a new login in the meantime stays.
            if (e.code == 401 && _state.value.session?.token == session.token) logout()
            throw e
        }
    }
}
