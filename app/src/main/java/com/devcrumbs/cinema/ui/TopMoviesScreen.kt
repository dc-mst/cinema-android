package com.devcrumbs.cinema.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.TopMovie
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * Più visti: the most-watched films over a rolling window, from the booking
 * platforms' own seat counters (GET /api/top-movies — the website's /piu-visti).
 */
@Composable
fun TopMoviesScreen(api: CinemaApi, city: City, onOpenDetail: (String) -> Unit) {
    var days by rememberSaveable { mutableIntStateOf(7) }
    var metric by rememberSaveable { mutableStateOf("tickets") }
    val (load, retry) = rememberLoad(Triple(city.slug, days, metric)) { api.topMovies(city, days, metric) }

    ScreenScaffold(title = { Text(stringResource(R.string.top_title), fontWeight = FontWeight.Bold) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(7, 14, 31)) { d ->
                    FilterChip(selected = days == d, onClick = { days = d }, label = { Text(stringResource(R.string.last_days, d)) })
                }
                item {
                    FilterChip(selected = metric == "tickets", onClick = { metric = "tickets" },
                        label = { Text(stringResource(R.string.metric_tickets)) })
                }
                item {
                    FilterChip(selected = metric == "occupancy", onClick = { metric = "occupancy" },
                        label = { Text(stringResource(R.string.metric_occupancy)) })
                }
            }
            LoadContent(load, retry) { data ->
                if (data.movies.isEmpty()) {
                    CenteredMessage(stringResource(R.string.top_empty))
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            Text(stringResource(R.string.top_explainer), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        itemsIndexed(data.movies, key = { _, m -> m.key }) { index, movie ->
                            TopMovieRow(city, index + 1, movie, metric) { onOpenDetail(movie.key) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMovieRow(city: City, rank: Int, movie: TopMovie, metric: String, onClick: () -> Unit) {
    val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("$rank", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                color = GOLD, modifier = Modifier.width(36.dp))
            Poster(city, movie.posterUrl, movie.title, width = 48.dp, height = 70.dp, px = 160)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(movie.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val primary = if (metric == "occupancy") movie.occupancyMean?.let { stringResource(R.string.occupancy_value, it) }
                else stringResource(R.string.tickets_value, nf.format(movie.ticketsSold))
                primary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                Text(
                    stringResource(R.string.top_meta, movie.screenings, movie.cinemas.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One ranked film, screening by screening (GET /api/top-movies/film). */
@Composable
fun TopFilmScreen(api: CinemaApi, city: City, key: String, onBack: () -> Unit, onOpenFilm: (String) -> Unit) {
    val (load, retry) = rememberLoad(city.slug + key) { api.topMovieFilm(city, key) }
    val nf = NumberFormat.getIntegerInstance(Locale.getDefault())
    ScreenScaffold(
        title = { Text((load as? Load.Ready)?.value?.film?.title ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onBack = onBack,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { detail ->
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Poster(city, detail.film.posterUrl, detail.film.title)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(detail.film.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.tickets_value, nf.format(detail.film.ticketsSold)),
                                    style = MaterialTheme.typography.bodyMedium)
                                detail.film.occupancyMean?.let {
                                    Text(stringResource(R.string.occupancy_value, it), style = MaterialTheme.typography.bodySmall)
                                }
                                detail.film.slug?.let { slug ->
                                    OutlinedButton(onClick = { onOpenFilm(slug) }, modifier = Modifier.padding(top = 6.dp)) {
                                        Text(stringResource(R.string.open_film_page))
                                    }
                                }
                            }
                        }
                    }
                    items(detail.screenings) { s ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("${dateLabel(LocalDate.parse(s.date))} · ${s.time}", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(s.cinemaName, s.roomName).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                s.ticketsSold?.let {
                                    Text((if (s.isEstimated) "~" else "") + nf.format(it), fontWeight = FontWeight.SemiBold)
                                }
                                s.occupancyPct?.let {
                                    Text(stringResource(R.string.occupancy_value, it), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
