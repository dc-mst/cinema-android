package com.devcrumbs.cinema.data

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Cities come from the committed `assets/cities.json` (regenerated
 * from the platform's city.toml files by `syncCities` in app/build.gradle.kts); the chosen one is remembered.
 */
class CityRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("cinema", Context.MODE_PRIVATE)

    val cities: List<City> by lazy {
        appContext.assets.open("cities.json").bufferedReader().use {
            Json.decodeFromString<List<City>>(it.readText())
        }
    }

    var selectedSlug: String?
        get() = prefs.getString(KEY_CITY, null)
        set(value) = prefs.edit().putString(KEY_CITY, value).apply()

    fun selectedCity(): City? = selectedSlug?.let { slug -> cities.firstOrNull { it.slug == slug } }

    private companion object {
        const val KEY_CITY = "city_slug"
    }
}
