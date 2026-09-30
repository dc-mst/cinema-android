package com.devcrumbs.cinema.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The account endpoints under `<city url>/api`. The token is valid in every
 * city, so calls go to whichever city is selected; only registration cares
 * which (that city becomes the account's origin).
 *
 * A `401` from any call taking a token means it expired or the account is
 * gone — [AccountRepository] logs out on it.
 */
class AccountApi(
    private val client: OkHttpClient = CinemaApi.defaultClient,
    private val json: Json = CinemaApi.defaultJson,
) {
    // ── Auth ──

    suspend fun register(city: City, name: String, email: String, password: String): AuthResponse =
        send(city, "auth/register", null, "POST", body {
            put("name", name); put("email", email); put("password", password)
        })

    suspend fun login(city: City, email: String, password: String): AuthResponse =
        send(city, "auth/login", null, "POST", body { put("email", email); put("password", password) })

    /** [credential] is the Google ID token; the server finds or creates the account by its verified email. */
    suspend fun google(city: City, credential: String): AuthResponse =
        send(city, "auth/google", null, "POST", body { put("credential", credential) })

    /** The reset link the server emails finishes on [city]'s website. */
    suspend fun forgotPassword(city: City, email: String): String? =
        send<MessageResponse>(city, "auth/forgot-password", null, "POST", body { put("email", email) }).message

    suspend fun me(city: City, token: String): AccountUser = send<MeResponse>(city, "auth/me", token).user

    // ── Voglio vederlo ──

    suspend fun watchlist(city: City, token: String): List<WatchlistItem> =
        send<WatchlistResponse>(city, "user/watchlist", token).watchlist

    suspend fun addToWatchlist(city: City, token: String, add: WatchlistAdd): WatchlistItem =
        send<WatchlistItemResponse>(city, "user/watchlist", token, "POST",
            json.encodeToString(WatchlistAdd.serializer(), add).toRequestBody(JSON)).item

    suspend fun setNotifyTiming(city: City, token: String, tmdbId: Int, timing: String): WatchlistItem =
        send<WatchlistItemResponse>(city, "user/watchlist/$tmdbId", token, "PUT", body { put("notify_timing", timing) }).item

    suspend fun removeFromWatchlist(city: City, token: String, tmdbId: Int) {
        send<MessageResponse>(city, "user/watchlist/$tmdbId", token, "DELETE")
    }

    /** Moves the film to "Già visti"; returns the seen key the server stored. */
    suspend fun markWatchlistSeen(city: City, token: String, tmdbId: Int): String? =
        send<MarkSeenResponse>(city, "user/watchlist/$tmdbId/mark-seen", token, "POST", body {}).movieKey

    // ── Già visti ──

    suspend fun seenMovies(city: City, token: String): List<String> =
        send<SeenMoviesResponse>(city, "user/seen-movies", token).movieKeys

    /** Replaces the whole list. */
    suspend fun putSeenMovies(city: City, token: String, keys: List<String>): List<String> =
        send<SeenMoviesResponse>(city, "user/seen-movies", token, "PUT", body {
            putJsonArray("movie_keys") { keys.forEach { add(JsonPrimitive(it)) } }
        }).movieKeys

    suspend fun deleteSeenMovie(city: City, token: String, key: String) {
        send<MessageResponse>(city, "user/seen-movies", token, "DELETE") { addPathSegment(key) }
    }

    /**
     * Public: the city's films whose normalised title is one of [keys]. Asked
     * in batches — a long seen list would not fit in one request line.
     */
    suspend fun moviesByKeys(city: City, keys: Collection<String>): List<KeyedMovie> =
        keys.chunked(KEYS_PER_REQUEST).flatMap { batch ->
            send<List<KeyedMovie>>(city, "movies", null) { addQueryParameter("keys", batch.joinToString(",")) }
        }.sortedBy { it.title.lowercase() }

    // ── Email / alerts per city ──

    suspend fun cities(city: City, token: String): List<AccountCity> =
        send<AccountCitiesResponse>(city, "user/cities", token).cities

    suspend fun setCitySwitches(
        city: City, token: String, slug: String, emailEnabled: Boolean? = null, alertsEnabled: Boolean? = null,
    ): AccountCity =
        send<AccountCityResponse>(city, "user/cities", token, "PUT", body {
            emailEnabled?.let { put("email_enabled", it) }
            alertsEnabled?.let { put("alerts_enabled", it) }
        }) { addPathSegment(slug) }.city

    // ── Notifications (every city's) ──

    suspend fun notifications(city: City, token: String): NotificationsResponse =
        send(city, "user/notifications", token) { addQueryParameter("all_cities", "1") }

    suspend fun readAllNotifications(city: City, token: String) {
        send<MessageResponse>(city, "user/notifications/read-all", token, "POST", body {}) {
            addQueryParameter("all_cities", "1")
        }
    }

    // ── Push: this install's FCM token (stored once for every city) ──

    suspend fun registerDevice(city: City, token: String, deviceToken: String) {
        send<MessageResponse>(city, "push/device", token, "POST", body {
            put("token", deviceToken); put("platform", "android")
        })
    }

    suspend fun removeDevice(city: City, token: String, deviceToken: String) {
        send<MessageResponse>(city, "push/device", token, "DELETE", body { put("token", deviceToken) })
    }

    // ── Preferences (language, theme: shared by every city) ──

    suspend fun preferences(city: City, token: String): JsonObject =
        send<PreferencesResponse>(city, "user/preferences", token).preferences

    suspend fun putPreferences(city: City, token: String, prefs: JsonObject) {
        send<JsonObject>(city, "user/preferences", token, "PUT", body { put("preferences", prefs) })
    }

    // ── Plumbing ──

    private fun body(build: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): RequestBody =
        buildJsonObject(build).toString().toRequestBody(JSON)

    private suspend inline fun <reified T> send(
        city: City,
        path: String,
        token: String?,
        method: String = "GET",
        body: RequestBody? = null,
        crossinline url: HttpUrl.Builder.() -> Unit = {},
    ): T = withContext(Dispatchers.IO) {
        val target = city.apiBase.toHttpUrl().newBuilder().addPathSegments(path).apply { url() }.build()
        val request = Request.Builder().url(target).header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val serverMessage = runCatching { json.decodeFromString<MessageResponse>(text).error }.getOrNull()
                throw ApiException(response.code, "HTTP ${response.code} for ${target.encodedPath}", serverMessage)
            }
            json.decodeFromString<T>(text)
        }
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
        const val KEYS_PER_REQUEST = 60
    }
}
