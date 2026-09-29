package com.devcrumbs.cinema.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.FilmInfo
import com.devcrumbs.cinema.data.FilmPage
import com.devcrumbs.cinema.data.Screening
import com.devcrumbs.cinema.data.WatchlistAdd
import com.devcrumbs.cinema.data.groupByCinema
import com.devcrumbs.cinema.data.groupByDate
import com.devcrumbs.cinema.data.groupByMovie
import com.devcrumbs.cinema.data.slugify
import java.net.URLEncoder
import java.time.LocalDate

// ── Film ────────────────────────────────────────────────────────────────────

/** GET /api/film/<slug> — follows a "moved" answer (merged film) once. */
private suspend fun loadFilm(api: CinemaApi, city: City, slug: String): FilmPage {
    val page = api.film(city, slug)
    return if (page.status == "moved" && page.slug != null && page.slug != slug) api.film(city, page.slug) else page
}

@Composable
fun FilmScreen(api: CinemaApi, city: City, slug: String, onBack: () -> Unit, onOpenCinema: (String) -> Unit) {
    val (load, retry) = rememberLoad(city.slug + "/" + slug) { loadFilm(api, city, slug) }
    val title = (load as? Load.Ready)?.value?.movie?.title.orEmpty()
    ScreenScaffold(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { page ->
                val movie = page.movie ?: FilmInfo(title = slug)
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { FilmHeader(city, movie) }
                    item { FilmAccountActions(city, movie.title, watchlistAdd(movie, page.screenings.minOfOrNull { it.date })) }
                    item {
                        val status = when (page.status) {
                            "current" -> null
                            "historical" -> stringResource(R.string.no_longer_showing)
                            else -> stringResource(R.string.not_scheduled_in, city.cityName)
                        }
                        status?.let { Badge(it, MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(groupByDate(page.screenings), key = { it.first }) { (date, rows) ->
                        DaySection(date, rows, onOpenCinema)
                    }
                    if (page.pastRuns.isNotEmpty()) {
                        item {
                            Text(stringResource(R.string.past_runs), style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        }
                        items(page.pastRuns) { run ->
                            val dates = run.dates.sorted()
                            val span = if (dates.size > 1) "${dates.first()} → ${dates.last()}" else dates.firstOrNull().orEmpty()
                            Text("${run.cinemaName.orEmpty()} · $span", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** A film only TMDB knows (a search result no cinema here has listed). */
@Composable
fun TmdbFilmScreen(api: CinemaApi, city: City, tmdbId: Int, onBack: () -> Unit) {
    val (load, retry) = rememberLoad(city.slug + "/tmdb/" + tmdbId) { api.tmdbMovie(city, tmdbId) }
    val title = (load as? Load.Ready)?.value?.title.orEmpty()
    ScreenScaffold(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { movie ->
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { FilmHeader(city, movie) }
                    item { FilmAccountActions(city, movie.title, watchlistAdd(movie, null)) }
                    item { Badge(stringResource(R.string.not_scheduled_in, city.cityName), MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

/** "Voglio vederlo" needs a TMDB id; [firstDate] (earliest screening) is the reminder's date. */
internal fun watchlistAdd(movie: FilmInfo, firstDate: String?): WatchlistAdd? =
    movie.tmdbId?.takeIf { it > 0 && movie.title.isNotBlank() }?.let {
        WatchlistAdd(it, movie.title, movie.originalTitle, movie.posterUrl, movie.year, firstDate)
    }

@Composable
private fun FilmHeader(city: City, movie: FilmInfo) {
    val description = remember(movie) { localizedDescription(movie.description, movie.descriptionEn) }
    Column {
        Row {
            Poster(city, movie.posterUrl, movie.title, width = 110.dp, height = 162.dp, px = 480)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(movie.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                movie.originalTitle?.takeIf { it.isNotBlank() && !it.equals(movie.title, true) }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val meta = listOfNotNull(
                    movie.director?.trim()?.takeIf { it.isNotBlank() },
                    movie.year?.toString(),
                    movie.durationMinutes?.let { stringResource(R.string.minutes, it) },
                    movie.ageRating?.takeIf { it.isNotBlank() },
                ).let(::factsLine)
                if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall)
                movie.genre?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Rating(movie.tmdbRating, movie.tmdbVoteCount)
                if (!movie.festivalPremieres.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { FestivalBadges(movie.festivalPremieres) }
                }
            }
        }
        description?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DaySection(date: String, rows: List<Screening>, onOpenCinema: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(dateLabel(LocalDate.parse(date)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            groupByCinema(rows).forEach { CinemaShowtimesRow(it, null, onOpenCinema) }
        }
    }
}

// ── Cinema ──────────────────────────────────────────────────────────────────

@Composable
fun CinemaScreen(api: CinemaApi, city: City, slug: String, onBack: () -> Unit, onOpenFilm: (String) -> Unit) {
    val (load, retry) = rememberLoad(city.slug + "/c/" + slug) { api.cinema(city, slug) }
    val uriHandler = LocalUriHandler.current
    val title = (load as? Load.Ready)?.value?.name.orEmpty()
    ScreenScaffold(title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { cinema ->
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            cinema.address?.takeIf { it.isNotBlank() }?.let { address ->
                                val q = URLEncoder.encode("${cinema.name}, $address", "UTF-8")
                                val geo = if (cinema.lat != null && cinema.lng != null) "geo:${cinema.lat},${cinema.lng}?q=$q" else "geo:0,0?q=$q"
                                Text("📍 $address", color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(geo) } })
                            }
                            cinema.phone?.takeIf { it.isNotBlank() }?.let { phone ->
                                Text("☎ $phone", color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { runCatching { uriHandler.openUri("tel:$phone") } })
                            }
                            cinema.website?.takeIf { it.isNotBlank() }?.let { site ->
                                Text("🌐 " + site.substringAfter("://").trimEnd('/'), color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { runCatching { uriHandler.openUri(site) } })
                            }
                        }
                    }
                    if (cinema.screenings.isEmpty()) {
                        item { Text(stringResource(R.string.no_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(groupByDate(cinema.screenings), key = { it.first }) { (date, rows) ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(dateLabel(LocalDate.parse(date)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                groupByMovie(rows).forEachIndexed { i, film ->
                                    if (i > 0) HorizontalDivider()
                                    Text(film.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { slugify(film.title).takeIf { it.isNotEmpty() }?.let(onOpenFilm) })
                                    val showRoom = film.first.let { rows.filter { r -> r.movieId == it.movieId } }
                                        .mapNotNull { it.roomName }.distinct().size > 1
                                    ShowtimeChips(film.cinemas.flatMap { it.screenings }, showRoom)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Genre ───────────────────────────────────────────────────────────────────

@Composable
fun GenreScreen(api: CinemaApi, city: City, slug: String, onBack: () -> Unit, onOpenFilm: (String) -> Unit, onOpenCinema: (String) -> Unit) {
    val (load, retry) = rememberLoad(city.slug + "/g/" + slug) { api.genre(city, slug) }
    val title = (load as? Load.Ready)?.value?.name.orEmpty()
    ScreenScaffold(title = { Text(title) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { genre ->
                val films = remember(genre) { groupByMovie(genre.screenings) }
                if (films.isEmpty()) {
                    CenteredMessage(stringResource(R.string.no_upcoming))
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(films, key = { it.movieId }) { film ->
                            MovieCard(city, film, null,
                                onOpenFilm = { t -> slugify(t).takeIf { it.isNotEmpty() }?.let(onOpenFilm) },
                                onOpenCinema = onOpenCinema, showDates = true)
                        }
                    }
                }
            }
        }
    }
}
