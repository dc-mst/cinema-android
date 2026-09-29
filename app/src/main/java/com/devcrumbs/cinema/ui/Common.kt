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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.CinemaShowtimes
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.FestivalPremiere
import com.devcrumbs.cinema.data.LatLng
import com.devcrumbs.cinema.data.MovieSchedule
import com.devcrumbs.cinema.data.Screening
import com.devcrumbs.cinema.data.dedupeFestivalPremieres
import com.devcrumbs.cinema.data.endTime
import com.devcrumbs.cinema.data.festivalInitial
import com.devcrumbs.cinema.data.festivalName
import com.devcrumbs.cinema.data.formatKm
import com.devcrumbs.cinema.data.isOriginalVersion
import com.devcrumbs.cinema.data.screeningDistanceKm
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val GOLD = Color(0xFFD4A843)

// ── Loading ─────────────────────────────────────────────────────────────────

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data object Failed : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
}

/** Runs [block] when [key] changes; `retry()` runs it again. */
@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): Pair<Load<T>, () -> Unit> {
    var attempt by remember(key) { mutableIntStateOf(0) }
    val state by produceState<Load<T>>(Load.Loading, key, attempt) {
        value = Load.Loading
        value = try {
            Load.Ready(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed
        }
    }
    return state to { attempt++ }
}

@Composable
fun <T> LoadContent(load: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (load) {
        Load.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        Load.Failed -> CenteredMessage(stringResource(R.string.load_error)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
        is Load.Ready -> content(load.value)
    }
}

@Composable
fun CenteredMessage(text: String, action: (@Composable () -> Unit)? = null) {
    // Scrollable so pull-to-refresh also works on an empty or failed page.
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

// ── Screen frame ────────────────────────────────────────────────────────────

/** A screen inside the app's bottom-bar scaffold: own top bar, no double insets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: @Composable () -> Unit,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = { actions() },
            )
        },
        content = content,
    )
}

// ── Film bits ───────────────────────────────────────────────────────────────

@Composable
fun Poster(city: City, url: String?, contentDescription: String?, width: Dp = 76.dp, height: Dp = 112.dp, px: Int = 320) {
    Box(Modifier.size(width = width, height = height).clip(RoundedCornerShape(6.dp))) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {}
        AsyncImage(
            model = CinemaApi.posterUrl(city, url, px),
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
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

@Composable
fun Rating(rating: Double?, votes: Int?) {
    if (rating == null || rating <= 0 || (votes ?: 0) < 10) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, Modifier.size(14.dp), tint = GOLD)
        Text(String.format(Locale.getDefault(), " %.1f", rating), style = MaterialTheme.typography.labelMedium)
    }
}

/** Festival premiere badges: initial + year, full name for accessibility. */
@Composable
fun FestivalBadges(premieres: List<FestivalPremiere>?) {
    dedupeFestivalPremieres(premieres).forEach { fp ->
        val year = fp.editionYear?.let { " $it" }.orEmpty()
        Surface(shape = RoundedCornerShape(4.dp), color = GOLD.copy(alpha = 0.18f)) {
            Text(
                festivalInitial(fp.festival) + year,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        Text(
            festivalName(fp.festival),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
    }
}

/**
 * Joins facts with " · ", each in its own bidi isolate (FSI…PDI): a director's
 * name in a right-to-left script (TMDB returns some in Arabic or Hebrew) would
 * otherwise reorder the whole line ("80 · 2026 · <name> min").
 */
fun factsLine(parts: List<String>): String = parts.joinToString(" · ") { "\u2068$it\u2069" }

fun localizedDescription(it: String?, en: String?): String? {
    val english = Locale.getDefault().language == "en"
    return (if (english) en ?: it else it ?: en)?.takeIf { d -> d.isNotBlank() }
}

@Composable
fun dateLabel(date: LocalDate): String {
    val today = LocalDate.now(CITY_ZONE)
    val formatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    return when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> date.format(formatter).replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

/**
 * A film on the programme: poster, facts, badges, synopsis (tap to expand),
 * then its showtimes per cinema. Title → film page, cinema name → cinema page.
 */
@Composable
fun MovieCard(
    city: City,
    movie: MovieSchedule,
    location: LatLng?,
    onOpenFilm: (String) -> Unit,
    onOpenCinema: (String) -> Unit,
    showDates: Boolean = false,
) {
    var expanded by rememberSaveable(movie.movieId) { mutableStateOf(false) }
    val s = movie.first
    val description = remember(s) { localizedDescription(s.movieDescription, s.movieDescriptionEn) }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable { onOpenFilm(movie.title) }.padding(12.dp)) {
            Poster(city, s.moviePosterUrl, movie.title)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(movie.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val original = s.movieOriginalTitle
                if (!original.isNullOrBlank() && !original.equals(movie.title, ignoreCase = true)) {
                    Text(original, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val meta = listOfNotNull(
                    s.movieDirector?.trim()?.takeIf { it.isNotBlank() },
                    s.movieYear?.toString(),
                    s.movieDurationMinutes?.let { stringResource(R.string.minutes, it) },
                ).let(::factsLine)
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Rating(s.movieTmdbRating, s.movieTmdbVoteCount)
                    if (s.movieIsFirstWeek) Badge(stringResource(R.string.badge_new))
                    if (s.movieLastChanceDate != null) Badge(stringResource(R.string.badge_last_chance), MaterialTheme.colorScheme.error)
                }
                if (!s.movieFestivalPremieres.isNullOrEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) { FestivalBadges(s.movieFestivalPremieres) }
                }
                if (description != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // With "Vicino a me" on, nearest cinema first.
            val rows = if (location == null) movie.cinemas
            else movie.cinemas.sortedBy { screeningDistanceKm(it.screenings.first(), location) ?: Double.MAX_VALUE }
            rows.forEach { CinemaShowtimesRow(it, location, onOpenCinema, showDates) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CinemaShowtimesRow(
    showtimes: CinemaShowtimes,
    location: LatLng?,
    onOpenCinema: ((String) -> Unit)?,
    showDates: Boolean = false,
) {
    // Room names only help when this film plays in more than one room here.
    val showRoom = showtimes.screenings.mapNotNull { it.roomName }.distinct().size > 1
    val distance = screeningDistanceKm(showtimes.screenings.first(), location)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                showtimes.cinemaName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (onOpenCinema != null && showtimes.cinemaSlug != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clickable(enabled = onOpenCinema != null && showtimes.cinemaSlug != null) {
                    showtimes.cinemaSlug?.let { onOpenCinema?.invoke(it) }
                },
            )
            if (distance != null) {
                Text("  · " + formatKm(distance), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ShowtimeChips(showtimes.screenings, showRoom, showDates)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShowtimeChips(screenings: List<Screening>, showRoom: Boolean, showDates: Boolean = false) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        screenings.forEach { screening ->
            val link = screening.bookingUrl ?: screening.cinemaWebsite
            AssistChip(
                onClick = { link?.let { runCatching { uriHandler.openUri(it) } } },
                enabled = link != null,
                label = { ShowtimeLabel(screening, showRoom, showDates) },
            )
        }
    }
}

@Composable
private fun ShowtimeLabel(screening: Screening, showRoom: Boolean, showDate: Boolean) {
    val soldOut = screening.availability == "sold_out"
    val end = endTime(screening.time, screening.movieDurationMinutes)
    val extras = listOfNotNull(
        screening.language?.takeIf { it.isNotBlank() && (isOriginalVersion(it) || !it.equals("ITA", ignoreCase = true)) },
        screening.roomName?.takeIf { showRoom && it.isNotBlank() },
        when (screening.availability) {
            "sold_out" -> stringResource(R.string.sold_out)
            "few_left" -> stringResource(R.string.few_left)
            else -> null
        },
    )
    Column {
        if (showDate) {
            Text(dateLabel(LocalDate.parse(screening.date)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(screening.time ?: "--:--", fontWeight = FontWeight.Bold,
                textDecoration = if (soldOut) TextDecoration.LineThrough else null)
            if (end != null) {
                Text(" → ~$end", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (extras.isNotEmpty()) {
            Text(
                extras.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (screening.availability == "few_left" || soldOut) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
