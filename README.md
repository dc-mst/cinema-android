# Cinema — Android app

A native Kotlin / Jetpack Compose client for **every city** of the cinema platform
(Cinema Bologna, Firenze, Milano, Torino — the backend lives in `server3management`).
The user picks a city (remembered across launches), then browses that city's
programme day by day, grouped by film, with showtimes per cinema. Tapping a
showtime opens its booking page (or the cinema's website when a source has no
booking link).

It is a thin, read-only client of the public API each city already serves — no
backend change, no account, no new endpoint.

## Where the city list comes from

**The code contains no city names.** The list lives in
`app/src/main/assets/cities.json` (`slug`, `name`, `url`, `city_name` per city),
derived from the cinema platform's `backend/cities/*/city.toml` — the single
source of truth the backend and web frontend read, in
[`dc-mst/server3management`](https://github.com/dc-mst/server3management)
under `services/cinema-platform/`. After a city is added there (or a city's
`[site].url` changes), regenerate the file and commit it:

```bash
./gradlew syncCities -PcinemaPlatform=/path/to/server3management/services/cinema-platform
```

The task skips `_template` and fails if a `city.toml` lacks one of those keys.

## API used

All under `<city url>/api` — see the API Reference in `services/cinema-platform/README.md`:

| Call | Used for |
|------|----------|
| `GET /screenings/calendar` | Date strip (days with ≥1 screening; past days dropped, "today" in Europe/Rome) |
| `GET /screenings?date=YYYY-MM-DD` | The day's schedule. Same endpoint and same server-side 200-row cap as the web feed |
| `GET /img?u=<poster>&w=320` | Posters, resized to WebP by the city's poster proxy (302s to the original on failure; OkHttp follows it) |

`Screening` in `data/Models.kt` mirrors `Screening.to_dict()`; unknown fields are
ignored, so adding backend fields never breaks the app. Removing or renaming one
the app reads would.

## Layout

```
android/
├── app/build.gradle.kts          ← deps + syncCities task (city.toml → assets/cities.json)
├── app/src/main/assets/cities.json ← the city list (generated, committed)
└── app/src/main/java/com/devcrumbs/cinema/
    ├── MainActivity.kt           ← picker vs. schedule switch
    ├── data/Models.kt            ← City, Screening (API DTO), grouped view models
    ├── data/Schedule.kt          ← groupByMovie() / cinemasIn() — the "by film" grouping
    ├── data/CinemaApi.kt         ← OkHttp + kotlinx.serialization client, poster URL builder
    ├── data/CityRepository.kt    ← bundled city list + remembered choice (SharedPreferences)
    ├── ui/ScheduleViewModel.kt   ← state, loading, cancellation of stale requests
    ├── ui/CityPickerScreen.kt
    ├── ui/ScheduleScreen.kt      ← date chips, cinema filter, film cards, showtime chips
    └── ui/Theme.kt               ← web palette (frontend/tailwind.config.js `cinema.*`)
```

Strings: English default, Italian in `values-it/`. The film synopsis follows the
device language (`movie_description_en` for English, else Italian).

## Build

Needs JDK 17+ and an Android SDK (platform 35). Open the repo in Android Studio,
or:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # if ANDROID_HOME is not set
./gradlew testDebugUnitTest assembleDebug              # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                              # minified, unsigned
```

## Not done yet

- Release signing / Play Store listing (the release APK is unsigned).
- Login, watchlist, seen movies, push notifications — all exist in the API
  (JWT) but the app is anonymous for now.
- Film page, cinema page, search, "most viewed" — web-only today.
