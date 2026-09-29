package com.devcrumbs.cinema.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Read-only client for the public endpoints every city serves under `/api`. */
class CinemaApi(
    private val client: OkHttpClient = defaultClient,
    private val json: Json = defaultJson,
) {
    /** ISO dates (yyyy-MM-dd) that have at least one screening. */
    suspend fun calendar(city: City): List<String> =
        get(city.apiBase.toHttpUrl().newBuilder().addPathSegments("screenings/calendar").build().toString())

    suspend fun screenings(city: City, date: String): List<Screening> =
        get(
            city.apiBase.toHttpUrl().newBuilder()
                .addPathSegment("screenings")
                .addQueryParameter("date", date)
                .build().toString()
        )

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            json.decodeFromString<T>(response.body!!.string())
        }
    }

    companion object {
        val defaultJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        private val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * Same-origin, resized WebP of a poster via the city's `/api/img`
         * proxy (see README "Poster proxy"). Widths snap to the server ladder.
         */
        fun posterUrl(city: City, original: String?, width: Int = 320): String? =
            original?.takeIf { it.isNotBlank() }?.let {
                city.apiBase.toHttpUrl().newBuilder()
                    .addPathSegment("img")
                    .addQueryParameter("u", it)
                    .addQueryParameter("w", width.toString())
                    .build().toString()
            }
    }
}
