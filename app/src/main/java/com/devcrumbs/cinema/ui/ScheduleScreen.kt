package com.devcrumbs.cinema.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.CinemaShowtimes
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.MovieSchedule
import com.devcrumbs.cinema.data.Screening
import com.devcrumbs.cinema.data.cinemasIn
import com.devcrumbs.cinema.data.groupByMovie
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onChangeCity: () -> Unit,
    onRefresh: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onSelectCinema: (Int?) -> Unit,
) {
    val city = state.city ?: return
    val movies = remember(state.screenings, state.selectedCinemaId) {
        groupByMovie(state.screenings, state.selectedCinemaId)
    }
    val cinemas = remember(state.screenings) { cinemasIn(state.screenings) }
    val listState = rememberLazyListState()
    LaunchedEffect(city.slug, state.selectedDate, state.selectedCinemaId) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onChangeCity).padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(city.name, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = onChangeCity) {
                        Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.change_city))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateRow(state.dates, state.selectedDate, onSelectDate)
            if (cinemas.size > 1) CinemaRow(cinemas.map { it.id to it.name }, state.selectedCinemaId, onSelectCinema)
            if (state.loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }

            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.error && state.screenings.isEmpty() -> CenteredMessage(stringResource(R.string.load_error)) {
                        Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
                    }
                    !state.loading && movies.isEmpty() -> CenteredMessage(stringResource(R.string.no_screenings))
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (movies.isNotEmpty()) {
                            item {
                                Text(
                                    stringResource(R.string.films_count, movies.size, movies.sumOf { it.screeningCount }),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                        }
                        items(movies, key = { it.movieId }) { movie -> MovieCard(city, movie) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateRow(dates: List<LocalDate>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val today = LocalDate.now(CITY_ZONE)
    val todayLabel = stringResource(R.string.today)
    val tomorrowLabel = stringResource(R.string.tomorrow)
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val listState = rememberLazyListState()
    LaunchedEffect(dates, selected) {
        val index = dates.indexOf(selected)
        if (index >= 0) listState.animateScrollToItem(maxOf(0, index - 1))
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dates, key = { it.toString() }) { date ->
            val label = when (date) {
                today -> todayLabel
                today.plusDays(1) -> tomorrowLabel
                else -> date.format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
            FilterChip(selected = date == selected, onClick = { onSelect(date) }, label = { Text(label) })
        }
    }
}

@Composable
private fun CinemaRow(cinemas: List<Pair<Int, String>>, selected: Int?, onSelect: (Int?) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.all_cinemas)) },
            )
        }
        items(cinemas, key = { it.first }) { (id, name) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(if (selected == id) null else id) },
                label = { Text(name) },
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String, action: (@Composable () -> Unit)? = null) {
    // Scrollable so pull-to-refresh also works on an empty or failed day.
    LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(
                Modifier.padding(top = 96.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (action != null) {
                    Spacer(Modifier.height(16.dp))
                    action()
                }
            }
        }
    }
}

@Composable
private fun MovieCard(city: City, movie: MovieSchedule) {
    var expanded by rememberSaveable(movie.movieId) { mutableStateOf(false) }
    val s = movie.first
    val description = remember(s) {
        val en = Locale.getDefault().language == "en"
        (if (en) s.movieDescriptionEn ?: s.movieDescription else s.movieDescription ?: s.movieDescriptionEn)
            ?.takeIf { it.isNotBlank() }
    }

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clickable(enabled = description != null) { expanded = !expanded }.padding(12.dp),
        ) {
            Box(
                Modifier.size(width = 76.dp, height = 112.dp).clip(RoundedCornerShape(6.dp)),
            ) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
                AsyncImage(
                    model = CinemaApi.posterUrl(city, s.moviePosterUrl),
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(movie.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val original = s.movieOriginalTitle
                if (!original.isNullOrBlank() && !original.equals(movie.title, ignoreCase = true)) {
                    Text(
                        original,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val meta = listOfNotNull(
                    s.movieDirector?.takeIf { it.isNotBlank() },
                    s.movieYear?.toString(),
                    s.movieDurationMinutes?.let { stringResource(R.string.minutes, it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val rating = s.movieTmdbRating
                    if (rating != null && rating > 0 && (s.movieTmdbVoteCount ?: 0) >= 10) {
                        Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = Color(0xFFD4A843))
                        Text(String.format(Locale.getDefault(), "%.1f", rating), style = MaterialTheme.typography.labelMedium)
                    }
                    if (s.movieIsFirstWeek) Badge(stringResource(R.string.badge_new))
                    if (s.movieLastChanceDate != null) Badge(stringResource(R.string.badge_last_chance), MaterialTheme.colorScheme.error)
                }
                if (expanded && description != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(description, style = MaterialTheme.typography.bodySmall)
                } else if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            movie.cinemas.forEach { CinemaShowtimesRow(it) }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.15f)) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CinemaShowtimesRow(showtimes: CinemaShowtimes) {
    val uriHandler = LocalUriHandler.current
    // Room names only help when this film plays in more than one room here.
    val showRoom = showtimes.screenings.mapNotNull { it.roomName }.distinct().size > 1
    Column {
        Text(showtimes.cinemaName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            showtimes.screenings.forEach { screening ->
                val link = screening.bookingUrl ?: screening.cinemaWebsite
                AssistChip(
                    onClick = { link?.let { runCatching { uriHandler.openUri(it) } } },
                    enabled = link != null,
                    label = { ShowtimeLabel(screening, showRoom) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }
    }
}

@Composable
private fun ShowtimeLabel(screening: Screening, showRoom: Boolean) {
    val soldOut = screening.availability == "sold_out"
    val extras = listOfNotNull(
        screening.language?.takeIf { it.isNotBlank() && !it.equals("ITA", ignoreCase = true) },
        screening.roomName?.takeIf { showRoom && it.isNotBlank() },
        when (screening.availability) {
            "sold_out" -> stringResource(R.string.sold_out)
            "few_left" -> stringResource(R.string.few_left)
            else -> null
        },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            screening.time ?: "--:--",
            fontWeight = FontWeight.Bold,
            textDecoration = if (soldOut) TextDecoration.LineThrough else null,
        )
        if (extras.isNotEmpty()) {
            Text(
                " · " + extras.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (screening.availability == "few_left" || soldOut) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
