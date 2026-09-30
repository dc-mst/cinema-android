package com.devcrumbs.cinema.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.devcrumbs.cinema.push.PushNotifications
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.AccountCity
import com.devcrumbs.cinema.data.ApiException
import com.devcrumbs.cinema.data.AppNotification
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.NotifyTiming
import com.devcrumbs.cinema.data.WatchlistAdd
import com.devcrumbs.cinema.data.WatchlistItem
import com.devcrumbs.cinema.data.normaliseTitle
import com.devcrumbs.cinema.data.slugify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Profile tab ─────────────────────────────────────────────────────────────

/** The account tab: login/registration when logged out, the person's menu otherwise. */
@Composable
fun AccountScreen(
    account: AccountViewModel,
    city: City,
    cities: List<City>,
    onOpen: (String) -> Unit,
) {
    val state by account.state.collectAsStateWithLifecycle()
    val user = state.user
    if (user == null) {
        ScreenScaffold(title = { Text(stringResource(R.string.account_title)) }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) { AuthForm(account, city) }
        }
        return
    }
    ScreenScaffold(title = { Text(stringResource(R.string.account_title)) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(user.name?.takeIf { it.isNotBlank() } ?: user.email,
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (!user.name.isNullOrBlank()) {
                        Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val origin = cities.firstOrNull { it.slug == user.originCity }?.name
                    if (origin != null) {
                        Text(stringResource(R.string.account_origin, origin), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                MenuRow(stringResource(R.string.watchlist_title), pluralCount(R.plurals.films_n, state.watchlist.size)) { onOpen(ROUTE_WATCHLIST) }
            }
            item {
                MenuRow(stringResource(R.string.seen_title), pluralCount(R.plurals.films_n, state.seenKeys.size)) { onOpen(ROUTE_SEEN) }
            }
            item {
                MenuRow(
                    stringResource(R.string.notifications_title),
                    if (state.unreadCount > 0) stringResource(R.string.unread_n, state.unreadCount) else null,
                ) { onOpen(ROUTE_NOTIFICATIONS) }
            }
            item { MenuRow(stringResource(R.string.city_switches_title), stringResource(R.string.city_switches_hint)) { onOpen(ROUTE_CITIES) } }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
            item {
                ChoiceRow(
                    label = stringResource(R.string.email_language),
                    options = listOf("it" to stringResource(R.string.language_it), "en" to stringResource(R.string.language_en)),
                    selected = user.language,
                    onSelect = { lang -> account.act { account.repo.setLanguage(city, lang) } },
                )
            }
            item {
                ChoiceRow(
                    label = stringResource(R.string.theme),
                    options = listOf("light" to stringResource(R.string.theme_light), "dark" to stringResource(R.string.theme_dark)),
                    selected = state.theme,
                    onSelect = { theme -> account.act { account.repo.setTheme(city, theme) } },
                )
            }
            item {
                OutlinedButton(onClick = { account.signOut(city) }, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.logout))
                }
            }
        }
    }
}

const val ROUTE_ACCOUNT = "account"
const val ROUTE_WATCHLIST = "account/watchlist"
const val ROUTE_SEEN = "account/seen"
const val ROUTE_NOTIFICATIONS = "account/notifications"
const val ROUTE_CITIES = "account/cities"

@Composable
private fun pluralCount(res: Int, n: Int): String = pluralStringResource(res, n, n)

@Composable
private fun MenuRow(title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ChoiceRow(label: String, options: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, text) ->
                FilterChip(selected = value == selected, onClick = { if (value != selected) onSelect(value) }, label = { Text(text) })
            }
        }
    }
}

// ── Login / registration / forgotten password ───────────────────────────────

private enum class AuthMode { LOGIN, REGISTER, FORGOT }

/** Plausible enough to send; the server validates for real. */
internal fun looksLikeEmail(s: String): Boolean = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(s.trim())

internal const val MIN_PASSWORD = 6

@Composable
private fun AuthForm(account: AccountViewModel, city: City) {
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val invalidEmail = stringResource(R.string.invalid_email)
    val shortPassword = stringResource(R.string.password_too_short, MIN_PASSWORD)
    val wrongLogin = stringResource(R.string.wrong_credentials)
    val emailTaken = stringResource(R.string.email_taken)
    val resetSent = stringResource(R.string.reset_sent, city.host)
    val errorTexts = mapOf(
        R.string.too_many_attempts to stringResource(R.string.too_many_attempts),
        R.string.server_error to stringResource(R.string.server_error),
        R.string.network_error to stringResource(R.string.network_error),
        R.string.session_expired to stringResource(R.string.session_expired),
        R.string.login_required to stringResource(R.string.login_required),
    )

    fun submit() {
        error = null
        info = null
        when {
            !looksLikeEmail(email) -> { error = invalidEmail; return }
            mode != AuthMode.FORGOT && password.length < MIN_PASSWORD -> { error = shortPassword; return }
        }
        busy = true
        scope.launch {
            try {
                when (mode) {
                    AuthMode.LOGIN -> account.repo.login(city, email, password)
                    AuthMode.REGISTER -> account.repo.register(city, name, email, password)
                    AuthMode.FORGOT -> { account.repo.forgotPassword(city, email); info = resetSent }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = when {
                    e is ApiException && e.code == 401 && mode == AuthMode.LOGIN -> wrongLogin
                    e is ApiException && e.code == 409 -> emailTaken
                    // Validation (400): the server's own words say what is wrong.
                    e is ApiException && e.code == 400 && e.serverMessage != null -> e.serverMessage
                    else -> errorTexts.getValue(accountErrorText(e))
                }
            } finally {
                busy = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                stringResource(
                    when (mode) {
                        AuthMode.LOGIN -> R.string.login
                        AuthMode.REGISTER -> R.string.register
                        AuthMode.FORGOT -> R.string.forgot_password
                    },
                ),
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when (mode) {
                    AuthMode.LOGIN -> stringResource(R.string.login_hint)
                    AuthMode.REGISTER -> stringResource(R.string.register_hint, city.cityName)
                    AuthMode.FORGOT -> stringResource(R.string.forgot_hint, city.host)
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (mode == AuthMode.REGISTER) {
            item {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.name_optional)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next))
            }
        }
        item {
            OutlinedTextField(email, { email = it }, label = { Text(stringResource(R.string.email)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email,
                    imeAction = if (mode == AuthMode.FORGOT) ImeAction.Done else ImeAction.Next))
        }
        if (mode != AuthMode.FORGOT) {
            item {
                OutlinedTextField(password, { password = it }, label = { Text(stringResource(R.string.password)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
            }
        }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        info?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item {
            Button(onClick = ::submit, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(
                    when (mode) {
                        AuthMode.LOGIN -> R.string.login
                        AuthMode.REGISTER -> R.string.register
                        AuthMode.FORGOT -> R.string.send_reset_link
                    },
                ))
            }
        }
        item {
            Column {
                fun switchTo(m: AuthMode) { mode = m; error = null; info = null }
                when (mode) {
                    AuthMode.LOGIN -> {
                        TextButton(onClick = { switchTo(AuthMode.REGISTER) }) { Text(stringResource(R.string.no_account)) }
                        TextButton(onClick = { switchTo(AuthMode.FORGOT) }) { Text(stringResource(R.string.forgot_password)) }
                    }
                    AuthMode.REGISTER, AuthMode.FORGOT ->
                        TextButton(onClick = { switchTo(AuthMode.LOGIN) }) { Text(stringResource(R.string.have_account)) }
                }
            }
        }
    }
}

// ── Voglio vederlo ──────────────────────────────────────────────────────────

@Composable
fun WatchlistScreen(
    account: AccountViewModel,
    city: City,
    onBack: () -> Unit,
    onOpenFilm: (String) -> Unit,
    onOpenTmdb: (Int) -> Unit,
) {
    val state by account.state.collectAsStateWithLifecycle()
    var loaded by remember(city.slug) { mutableStateOf(false) }
    LaunchedEffect(city.slug) { account.repo.refreshWatchlist(city); loaded = true }
    val (showing, waiting) = state.watchlist.partition { it.hasScreenings }

    ScreenScaffold(title = { Text(stringResource(R.string.watchlist_title)) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.watchlist.isEmpty() && !loaded ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.watchlist.isEmpty() -> CenteredMessage(stringResource(R.string.watchlist_empty, city.cityName))
                else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showing.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.showing_in, city.cityName)) }
                        items(showing, key = { it.tmdbId }) { item ->
                            WatchlistRow(account, city, item, onOpen = { onOpenFilm(slugify(item.title)) })
                        }
                    }
                    if (waiting.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.not_showing_in, city.cityName)) }
                        items(waiting, key = { it.tmdbId }) { item ->
                            WatchlistRow(account, city, item, onOpen = { onOpenTmdb(item.tmdbId) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun WatchlistRow(account: AccountViewModel, city: City, item: WatchlistItem, onOpen: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(city, item.posterUrl, item.title, width = 48.dp, height = 70.dp, px = 160)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.originalTitle?.takeIf { it.isNotBlank() && !it.equals(item.title, true) }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                item.year?.let { Text("$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (item.hasScreenings) ReminderControl(account, city, item)
            }
            IconButton(onClick = { account.act { account.repo.markWatchlistSeen(city, item.tmdbId) } }) {
                Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.mark_seen))
            }
            IconButton(onClick = { account.act { account.repo.removeFromWatchlist(city, item.tmdbId) } }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.remove))
            }
        }
    }
}

/** What the website's watchlist shows for a film that is showing: the reminder choice. */
internal enum class ReminderStatus { CHOOSE, SENT, DATE_PASSED }

internal fun reminderStatus(item: WatchlistItem, today: LocalDate): ReminderStatus {
    val target = item.targetDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    return when {
        target != null && target.isBefore(today) ->
            if (item.notifyTiming == NotifyTiming.NONE) ReminderStatus.SENT else ReminderStatus.DATE_PASSED
        target != null && item.notifyTiming == NotifyTiming.NONE -> ReminderStatus.SENT
        else -> ReminderStatus.CHOOSE
    }
}

@Composable
private fun timingLabel(timing: String): String = stringResource(
    when (timing) {
        NotifyTiming.NONE -> R.string.reminder_none
        NotifyTiming.SAME_DAY -> R.string.reminder_same_day
        NotifyTiming.THREE_DAYS_BEFORE -> R.string.reminder_three_days
        else -> R.string.reminder_immediately
    },
)

@Composable
private fun ReminderControl(account: AccountViewModel, city: City, item: WatchlistItem) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (reminderStatus(item, LocalDate.now(CITY_ZONE))) {
        ReminderStatus.SENT -> Text(stringResource(R.string.reminder_sent), style = MaterialTheme.typography.labelSmall, color = muted)
        ReminderStatus.DATE_PASSED -> Text(stringResource(R.string.reminder_date_passed), style = MaterialTheme.typography.labelSmall, color = muted)
        ReminderStatus.CHOOSE -> {
            var open by remember { mutableStateOf(false) }
            Box {
                Text(
                    stringResource(R.string.reminder_label) + ": " + timingLabel(item.notifyTiming) + " ▾",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { open = true }.padding(vertical = 4.dp),
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    NotifyTiming.REMINDERS.forEach { timing ->
                        DropdownMenuItem(text = { Text(timingLabel(timing)) }, onClick = {
                            open = false
                            account.act { account.repo.setNotifyTiming(city, item.tmdbId, timing) }
                        })
                    }
                }
            }
        }
    }
}

// ── Già visti ───────────────────────────────────────────────────────────────

@Composable
fun SeenScreen(account: AccountViewModel, city: City, onBack: () -> Unit, onOpenFilm: (String) -> Unit) {
    val state by account.state.collectAsStateWithLifecycle()
    // The city's films for the keys; keys this city never showed stay bare.
    // Reloaded when the first keys arrive (the list may still be loading at login).
    val (load, retry) = rememberLoad(city.slug to state.seenKeys.isEmpty()) { account.repo.seenMovies(city) }
    ScreenScaffold(title = { Text(stringResource(R.string.seen_title)) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { movies ->
                val known = movies.filter { normaliseTitle(it.title) in state.seenKeys }
                val knownKeys = known.mapTo(HashSet()) { normaliseTitle(it.title) }
                val others = state.seenKeys.filter { it !in knownKeys }.sorted()
                if (known.isEmpty() && others.isEmpty()) {
                    CenteredMessage(stringResource(R.string.seen_empty))
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(known, key = { "m" + normaliseTitle(it.title) }) { movie ->
                            Card(Modifier.fillMaxWidth()) {
                                Row(Modifier.clickable { slugify(movie.title).takeIf { it.isNotEmpty() }?.let(onOpenFilm) }.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Poster(city, movie.posterUrl, movie.title, width = 48.dp, height = 70.dp, px = 160)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(movie.title, fontWeight = FontWeight.SemiBold)
                                        movie.year?.let { Text("$it", style = MaterialTheme.typography.bodySmall) }
                                        if (movie.screenings.isNotEmpty()) Badge(stringResource(R.string.now_showing))
                                    }
                                    UnseeButton(account, city, movie.title)
                                }
                            }
                        }
                        items(others, key = { "k$it" }) { key ->
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(key.replaceFirstChar { it.titlecase(Locale.getDefault()) }, Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                UnseeButton(account, city, key)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UnseeButton(account: AccountViewModel, city: City, title: String) {
    IconButton(onClick = { account.act { account.repo.setSeen(city, title, seen = false) } }) {
        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove))
    }
}

// ── Notifications (every city) ──────────────────────────────────────────────

@Composable
fun NotificationsScreen(
    account: AccountViewModel,
    city: City,
    onBack: () -> Unit,
    onOpen: (AppNotification) -> Unit,
) {
    val (load, retry) = rememberLoad(Unit) { account.repo.notifications(city) }
    // Seen = read, as on the website; the list keeps showing what was new.
    LaunchedEffect(load) {
        if (load is Load.Ready && load.value.unreadCount > 0) account.act { account.repo.readAllNotifications(city) }
    }
    ScreenScaffold(title = { Text(stringResource(R.string.notifications_title)) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { response ->
                if (response.notifications.isEmpty()) {
                    CenteredMessage(stringResource(R.string.notifications_empty))
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(response.notifications, key = { it.id }) { n -> NotificationRow(city, n) { onOpen(n) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(city: City, n: AppNotification, onClick: () -> Unit) {
    val title = n.movieTitle.orEmpty()
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(enabled = title.isNotBlank(), onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Poster(city, n.posterUrl, title, width = 40.dp, height = 58.dp, px = 160)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.notification_text, title, n.cityName ?: n.citySlug),
                    fontWeight = if (n.unread) FontWeight.Bold else FontWeight.Normal)
                n.createdAt?.let { formatTimestamp(it) }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (n.unread) Badge(stringResource(R.string.badge_new))
        }
    }
}

/** Backend timestamps are naive UTC ISO strings. */
internal fun formatTimestamp(iso: String): String? = runCatching {
    val utc = java.time.LocalDateTime.parse(iso.substringBefore('+').removeSuffix("Z"))
    val local = OffsetDateTime.of(utc, java.time.ZoneOffset.UTC).atZoneSameInstant(CITY_ZONE)
    local.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault()))
}.getOrNull()

// ── Email / alerts per city ─────────────────────────────────────────────────

@Composable
fun CitySwitchesScreen(account: AccountViewModel, city: City, appCities: List<City>, onBack: () -> Unit) {
    val (load, retry) = rememberLoad(Unit) { account.repo.cities(city) }
    // Each city's own switch, from each city's API (loaded alongside, never blocking).
    val newFilms = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) { runCatching { newFilms.putAll(account.repo.newFilmsSwitches(appCities)) } }
    ScreenScaffold(title = { Text(stringResource(R.string.city_switches_title)) }, onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadContent(load, retry) { cities ->
                // Local copy, updated at once and rolled back if the server says no.
                val rows = remember(cities) { mutableStateMapOf<String, AccountCity>().apply { cities.forEach { put(it.slug, it) } } }
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(stringResource(R.string.city_switches_explainer), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(cities.map { it.slug }, key = { it }) { slug ->
                        val row = rows.getValue(slug)
                        fun change(updated: AccountCity, email: Boolean?, alerts: Boolean?) {
                            val before = rows.getValue(slug)
                            rows[slug] = updated
                            account.act {
                                try {
                                    rows[slug] = account.repo.setCitySwitches(city, slug, email, alerts).copy(name = before.name, url = before.url)
                                } catch (e: Exception) {
                                    rows[slug] = before
                                    throw e
                                }
                            }
                        }
                        val target = appCities.firstOrNull { it.slug == slug }
                        CitySwitchCard(
                            row,
                            onEmail = { change(row.copy(emailEnabled = it), it, null) },
                            onAlerts = { change(row.copy(alertsEnabled = it), null, it) },
                            newFilms = newFilms[slug],
                            onNewFilms = { on ->
                                if (target != null) {
                                    newFilms[slug] = on
                                    account.act {
                                        try {
                                            account.repo.setNewFilms(target, on)
                                        } catch (e: Exception) {
                                            newFilms[slug] = !on
                                            throw e
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CitySwitchCard(
    row: AccountCity,
    onEmail: (Boolean) -> Unit,
    onAlerts: (Boolean) -> Unit,
    newFilms: Boolean?,
    onNewFilms: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(row.name.ifBlank { row.slug }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (row.isOrigin) Badge(stringResource(R.string.home_city))
            }
            SwitchLine(stringResource(R.string.switch_email), row.emailEnabled, onEmail)
            SwitchLine(stringResource(R.string.switch_alerts), row.alertsEnabled, onAlerts)
            // null: not loaded yet, or the city could not be reached.
            SwitchLine(stringResource(R.string.switch_new_films), newFilms ?: false, onNewFilms, enabled = newFilms != null)
        }
    }
}

@Composable
private fun SwitchLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

// ── On film pages ───────────────────────────────────────────────────────────

/**
 * "Voglio vederlo" and "Già visto" toggles for a film page. Logged out, they
 * lead to the login. [add] is null when the film has no TMDB id (only "seen").
 */
@Composable
fun FilmAccountActions(city: City, title: String, add: WatchlistAdd?) {
    val hooks = LocalAccount.current ?: return
    val state = LocalAccountState.current
    if (title.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (add != null) {
            val listed = add.tmdbId in state.watchlistIds
            FilterChip(
                selected = listed,
                onClick = { if (state.loggedIn) hooks.vm.toggleWatchlist(city, add) else hooks.openLogin() },
                leadingIcon = { Icon(if (listed) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, Modifier.size(18.dp)) },
                label = { Text(stringResource(R.string.want_to_see)) },
            )
        }
        val seen = state.isSeen(title)
        FilterChip(
            selected = seen,
            onClick = { if (state.loggedIn) hooks.vm.toggleSeen(city, title) else hooks.openLogin() },
            leadingIcon = if (seen) { { Icon(Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
            label = { Text(stringResource(R.string.seen)) },
        )
    }
}

// ── Notification permission (Android 13+) ───────────────────────────────────

/**
 * Asks once, right after login, for permission to show alerts. Once per
 * install: Android itself stops showing the dialog after two refusals, and the
 * choice can be changed in the system settings.
 */
@Composable
fun AskNotificationPermission(loggedIn: Boolean) {
    if (Build.VERSION.SDK_INT < 33) return
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(loggedIn) {
        if (!loggedIn || PushNotifications.canNotify(context)) return@LaunchedEffect
        val prefs = context.getSharedPreferences("cinema", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ASKED_NOTIFICATIONS, false)) return@LaunchedEffect
        prefs.edit().putBoolean(KEY_ASKED_NOTIFICATIONS, true).apply()
        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
