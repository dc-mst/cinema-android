# Cinema — Android app

A native Kotlin / Jetpack Compose client for **every city** of the cinema platform
(Cinema Bologna, Firenze, Milano, Torino — the backend lives in `server3management`).
The user picks a city (remembered across launches); three tabs then cover what
the city's website offers to a visitor who is not logged in:

- **Programma** — the day's films, grouped by film with showtimes per cinema;
  event/festival and cinema pills; a filter sheet with *Versione originale*,
  *Novità*, *Ultimi giorni*, time of day and *Vicino a me* (distance, radius,
  nearest cinema first). Each showtime shows the estimated end time; tapping it
  opens the booking page.
- **Più visti** — the attendance ranking (tickets or occupancy, 7/14/31 days),
  and per film its screenings with tickets sold.
- **Cerca** — films by title (the city's catalogue, then any film on TMDB), and
  genre shortcuts.
- **Film, cinema and genre pages** — all upcoming screenings and past runs of a
  film; a cinema's address (opens the map), phone, website and programme.

It is a read-only, anonymous client of the public API each city serves.

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

A city's **filter configuration** (festival pills and their notes keyword,
seasonal venues shown as events, hidden cinemas) comes at runtime from
`GET /api/site`. A backend deployed before that endpoint existed answers 404;
the app then shows no event pills and hides no cinema — everything else works.

## API used

All under `<city url>/api` — see the API Reference in `services/cinema-platform/README.md`:

| Call | Used for |
|------|----------|
| `GET /site` | Event/festival pills, hidden cinemas (optional, see above) |
| `GET /screenings/calendar` | Date strip (days with ≥1 screening; "today" in Europe/Rome) |
| `GET /screenings?date=&cinema=&event=&language=OV&first_week=true` | The day's programme. Cinema, event, VO and Novità are server-side filters, as on the website; Ultimi giorni, time of day and distance are applied on the device |
| `GET /film/<slug>` | Film page (slug built from the title exactly like the website's `slugify()`, `data/Slug.kt`); follows `status: moved` once |
| `GET /tmdb/search`, `GET /tmdb/movie/<id>` | Search beyond the local catalogue, and the page of a TMDB-only film (rate limited 30/min/IP: search is debounced 350 ms, min 2 chars) |
| `GET /movies?search=` | Search in the city's catalogue |
| `GET /cinemas/<slug>` | Cinema page |
| `GET /genres`, `GET /genres/<slug>` | Genre shortcuts and pages |
| `GET /top-movies`, `GET /top-movies/film?key=` | Più visti ranking and per-film detail |
| `GET /img?u=<poster>&w=` | Posters, resized to WebP by the city's poster proxy |

DTOs in `data/Models.kt` mirror the backend's `to_dict()` shapes; unknown fields
are ignored, so adding backend fields never breaks the app. Removing or renaming
one the app reads would — `LiveApiTest` (below) catches that.

## Layout

```
app/src/main/java/com/devcrumbs/cinema/
├── MainActivity.kt           ← city picker vs. the app
├── data/Models.kt            ← API DTOs + grouped view models
├── data/CinemaApi.kt         ← OkHttp + kotlinx.serialization client, poster URL builder
├── data/Schedule.kt          ← by-film / by-cinema / by-date grouping
├── data/Showtimes.kt         ← end time, time bands, distance, VO — ports of the website's showtimes.ts
├── data/Slug.kt              ← slugify() — port of the website's slug.ts
├── data/Festivals.kt         ← festival premiere badges
├── data/CityRepository.kt    ← bundled city list + remembered choice
└── ui/
    ├── AppNavigation.kt      ← bottom bar + routes (film, cinema, genre, tmdb, top detail)
    ├── ScheduleViewModel.kt  ← city, day, filters, /api/site
    ├── ScheduleScreen.kt     ← Programma: pills + filter sheet
    ├── TopMoviesScreen.kt    ← Più visti + per-film detail
    ├── SearchScreen.kt       ← search + genre shortcuts
    ├── DetailScreens.kt      ← film, TMDB film, cinema, genre pages
    ├── Common.kt             ← film card, showtime chips, badges, loading/error
    ├── Location.kt           ← one coarse fix for "Vicino a me" (no Play services)
    ├── CityPickerScreen.kt
    └── Theme.kt              ← web palette (frontend/tailwind.config.js `cinema.*`)
```

Strings: English default, Italian in `values-it/`. The film synopsis follows the
device language. Location permission (coarse) is asked only when *Vicino a me*
is switched on.

## Build and test

Needs JDK 17+ and an Android SDK (platform 35). Open the repo in Android Studio,
or:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # if ANDROID_HOME is not set
./gradlew testDebugUnitTest assembleDebug              # → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease                              # minified, unsigned
```

Opt-in tests that need the network:

```bash
LIVE_API=1    ./gradlew testDebugUnitTest --tests '*LiveApiTest*'    # every endpoint decodes, every city
SCREENSHOTS=1 ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' # renders screens with live data (Robolectric)
                                                                     # → app/build/outputs/roborazzi/
```

## Not done yet

Status, order and the backend work each step needs are tracked in
[`services/cinema-platform/docs/android-app-roadmap.md`](https://github.com/dc-mst/server3management/blob/master/services/cinema-platform/docs/android-app-roadmap.md)
in `dc-mst/server3management` — start there when resuming.

- Login, watchlist, seen movies, per-city email/alert switches, notifications
  from every city — the backend is ready (one account for every city), the app is
  anonymous for now.
- Push notifications to the app (needs Firebase Cloud Messaging + backend work).
- Google login in the app (needs an Android OAuth client per city project).
- Release signing / Play Store listing (the release APK is unsigned).
