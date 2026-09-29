package com.devcrumbs.cinema.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.CinemaApi
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.FilmInfo
import com.devcrumbs.cinema.data.TmdbResult
import com.devcrumbs.cinema.data.slugify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** Local catalogue first, then any other film on TMDB — like the website's search. */
private data class SearchResults(val local: List<FilmInfo>, val tmdb: List<TmdbResult>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    api: CinemaApi,
    city: City,
    onOpenFilm: (String) -> Unit,
    onOpenTmdb: (Int) -> Unit,
    onOpenGenre: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<SearchResults?>(null) }
    var searching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val q = query.trim()

    // 350 ms debounce, 2 characters minimum (TMDB search is rate limited to 30/min).
    LaunchedEffect(city.slug, q) {
        results = null
        failed = false
        if (q.length < 2) return@LaunchedEffect
        delay(350)
        searching = true
        try {
            results = coroutineScope {
                val local = async { api.searchMovies(city, q) }
                val tmdb = async { runCatching { api.searchTmdb(city, q) }.getOrDefault(emptyList()) }
                val localFilms = local.await()
                val known = localFilms.mapNotNull { it.tmdbId }.toSet()
                SearchResults(localFilms, tmdb.await().filter { it.tmdbId !in known })
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        } finally {
            searching = false
        }
    }

    ScreenScaffold(title = { Text(stringResource(R.string.search_title), fontWeight = FontWeight.Bold) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) }
                },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            when {
                q.length < 2 -> GenreShortcuts(api, city, onOpenGenre)
                searching && results == null -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator()
                }
                failed -> CenteredMessage(stringResource(R.string.load_error))
                results != null -> {
                    val r = results!!
                    if (r.local.isEmpty() && r.tmdb.isEmpty()) {
                        CenteredMessage(stringResource(R.string.search_empty))
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(r.local, key = { "l" + (it.id ?: it.title.hashCode()) }) { film ->
                                ResultRow(city, film.title, film.year, film.posterUrl, localStatus(film)) {
                                    slugify(film.title).takeIf { it.isNotEmpty() }?.let(onOpenFilm)
                                }
                            }
                            items(r.tmdb, key = { "t" + it.tmdbId }) { film ->
                                ResultRow(city, film.title, film.year, film.posterUrl, stringResource(R.string.not_scheduled)) {
                                    onOpenTmdb(film.tmdbId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun localStatus(film: FilmInfo): String? = when {
    film.hasUpcomingScreenings == true -> null
    film.hasAnyScreenings == true -> stringResource(R.string.no_longer_showing)
    else -> stringResource(R.string.not_scheduled)
}

@Composable
private fun ResultRow(city: City, title: String, year: Int?, poster: String?, status: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Poster(city, poster, title, width = 44.dp, height = 64.dp, px = 160)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title + (year?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (status != null) {
                Spacer(Modifier.height(2.dp))
                Badge(status, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenreShortcuts(api: CinemaApi, city: City, onOpenGenre: (String) -> Unit) {
    val (load, _) = rememberLoad(city.slug) { api.genres(city) }
    val genres = (load as? Load.Ready)?.value.orEmpty()
    if (genres.isEmpty()) return
    Column(Modifier.padding(12.dp)) {
        Text(stringResource(R.string.genres), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            genres.forEach { g -> AssistChip(onClick = { onOpenGenre(g.slug) }, label = { Text("${g.name} · ${g.count}") }) }
        }
    }
}
