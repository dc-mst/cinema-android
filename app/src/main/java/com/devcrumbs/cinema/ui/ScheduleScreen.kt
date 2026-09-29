package com.devcrumbs.cinema.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.TimeBand
import com.devcrumbs.cinema.data.cinemasIn
import com.devcrumbs.cinema.data.groupByMovie
import com.devcrumbs.cinema.data.slugify
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    vm: ScheduleViewModel,
    state: ScheduleUiState,
    onOpenFilm: (String) -> Unit,
    onOpenCinema: (String) -> Unit,
) {
    val city = state.city ?: return
    val visible = remember(state.screenings, state.filters, state.selectedDate) { state.visibleScreenings() }
    val movies = remember(visible) { groupByMovie(visible) }
    val eventVenues = remember(state.site) { state.site.eventCinemaSlugs.toSet() }
    // Cinema pills: the day's cinemas without the hidden ones and without the
    // seasonal venues (those are event pills).
    val cinemas = remember(state.screenings, state.site) {
        cinemasIn(state.screenings, state.site.hiddenCinemas.toSet() + eventVenues)
    }
    var showSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(city.slug, state.selectedDate, state.filters) { listState.scrollToItem(0) }

    ScreenScaffold(
        title = {
            Text(city.name, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = vm::openCityPicker).padding(4.dp))
        },
        actions = {
            IconButton(onClick = { showSheet = true }) {
                BadgedBox(badge = {
                    if (state.filters.sheetCount > 0) androidx.compose.material3.Badge { Text("${state.filters.sheetCount}") }
                }) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.filters))
                }
            }
            IconButton(onClick = vm::openCityPicker) {
                Icon(Icons.Filled.LocationOn, contentDescription = stringResource(R.string.change_city))
            }
            IconButton(onClick = vm::refresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.refresh))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            DateRow(state.dates, state.selectedDate, vm::selectDate)
            PillRow(state, cinemas.map { it.slug to it.name }, vm)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

            PullToRefreshBox(isRefreshing = false, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                val anyFilter = state.filters != ScheduleFilters()
                when {
                    state.error && state.screenings.isEmpty() -> CenteredMessage(stringResource(R.string.load_error)) {
                        androidx.compose.material3.Button(onClick = vm::refresh) { Text(stringResource(R.string.retry)) }
                    }
                    !state.loading && movies.isEmpty() -> CenteredMessage(
                        stringResource(if (anyFilter) R.string.no_screenings_filtered else R.string.no_screenings),
                    ) {
                        if (anyFilter) TextButton(onClick = vm::clearFilters) { Text(stringResource(R.string.clear_filters)) }
                    }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (movies.isNotEmpty()) {
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.films_count, movies.size, movies.sumOf { it.screeningCount }),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                                    )
                                    if (anyFilter) TextButton(onClick = vm::clearFilters) { Text(stringResource(R.string.clear_filters)) }
                                }
                            }
                        }
                        items(movies, key = { it.movieId }) { movie ->
                            MovieCard(
                                city, movie, state.filters.location,
                                onOpenFilm = { title -> slugify(title).takeIf { it.isNotEmpty() }?.let(onOpenFilm) },
                                onOpenCinema = onOpenCinema,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            FilterSheet(state, vm)
        }
    }
}

@Composable
private fun DateRow(dates: List<LocalDate>, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
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
            FilterChip(selected = date == selected, onClick = { onSelect(date) }, label = { Text(dateLabel(date)) })
        }
    }
}

/** "All", then event pills (festivals, seasonal venues), then the day's cinemas. */
@Composable
private fun PillRow(state: ScheduleUiState, cinemas: List<Pair<String?, String>>, vm: ScheduleViewModel) {
    val f = state.filters
    val events = state.eventChoices
    if (events.isEmpty() && cinemas.size <= 1 && f.cinemaSlug == null) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = f.cinemaSlug == null && f.event == null,
                onClick = { vm.selectCinema(null) },
                label = { Text(stringResource(R.string.all_cinemas)) },
            )
        }
        items(events, key = { "e:" + it.id }) { event ->
            FilterChip(
                selected = f.event == event,
                onClick = { vm.selectEvent(if (f.event == event) null else event) },
                label = { Text(event.label) },
            )
        }
        items(cinemas.filter { it.first != null }, key = { "c:" + it.first }) { (slug, name) ->
            FilterChip(
                selected = f.cinemaSlug == slug,
                onClick = { vm.selectCinema(if (f.cinemaSlug == slug) null else slug) },
                label = { Text(name) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(state: ScheduleUiState, vm: ScheduleViewModel) {
    val f = state.filters
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    var locationFailed by remember { mutableStateOf(false) }

    fun locate() {
        locating = true
        locationFailed = false
        scope.launch {
            val here = currentLocation(context)
            locating = false
            if (here == null) locationFailed = true else vm.setLocation(here, f.radiusKm)
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else locationFailed = true
    }

    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.filters), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        SwitchRow(stringResource(R.string.filter_ov), stringResource(R.string.filter_ov_hint), f.originalVersion, vm::setOriginalVersion)
        SwitchRow(stringResource(R.string.badge_new), stringResource(R.string.filter_new_hint), f.firstWeek, vm::setFirstWeek)
        SwitchRow(stringResource(R.string.badge_last_chance), stringResource(R.string.filter_last_chance_hint), f.lastChance, vm::setLastChance)

        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.filter_time), style = MaterialTheme.typography.titleSmall)
        val isToday = state.selectedDate == LocalDate.now(CITY_ZONE)
        val bands = listOf(
            TimeBand.ALL to R.string.band_all,
            TimeBand.FROM_NOW to R.string.band_from_now,
            TimeBand.AFTERNOON to R.string.band_afternoon,
            TimeBand.EVENING to R.string.band_evening,
            TimeBand.NIGHT to R.string.band_night,
        ).filter { it.first != TimeBand.FROM_NOW || isToday }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            bands.forEach { (band, label) ->
                FilterChip(selected = f.timeBand == band, onClick = { vm.setTimeBand(band) }, label = { Text(stringResource(label)) })
            }
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow(
            stringResource(R.string.near_me),
            when {
                locating -> stringResource(R.string.locating)
                locationFailed -> stringResource(R.string.location_failed)
                else -> stringResource(R.string.near_me_hint)
            },
            f.location != null,
        ) { on ->
            if (!on) {
                vm.setLocation(null, null)
            } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            ) {
                locate()
            } else {
                permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
        if (f.location != null) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(null, 1, 2, 5, 10).forEach { km ->
                    FilterChip(
                        selected = f.radiusKm == km,
                        onClick = { vm.setLocation(f.location, km) },
                        label = { Text(km?.let { "$it km" } ?: stringResource(R.string.any_distance)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
