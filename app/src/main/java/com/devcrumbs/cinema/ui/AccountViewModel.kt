package com.devcrumbs.cinema.ui

import android.app.Application
import androidx.annotation.StringRes
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.AccountApi
import com.devcrumbs.cinema.data.AccountRepository
import com.devcrumbs.cinema.data.AccountState
import com.devcrumbs.cinema.data.ApiException
import com.devcrumbs.cinema.data.City
import com.devcrumbs.cinema.data.KeystoreSessionStore
import com.devcrumbs.cinema.data.NotLoggedInException
import com.devcrumbs.cinema.data.WatchlistAdd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Login, "Voglio vederlo", "Già visti", notifications — for every screen. */
class AccountViewModel @JvmOverloads constructor(
    app: Application,
    val repo: AccountRepository = AccountRepository(AccountApi(), KeystoreSessionStore(app)),
) : AndroidViewModel(app) {
    val state: StateFlow<AccountState> = repo.state

    private val _notices = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    /** Failed background actions, as messages for a snackbar. */
    val notices: SharedFlow<Int> = _notices

    /** Runs an account action; a failure becomes a snackbar message. */
    fun act(block: suspend () -> Unit): Job = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _notices.tryEmit(accountErrorText(e))
        }
    }

    /** The selected city changed (or the app started): its view of the lists. */
    fun onCity(city: City) {
        viewModelScope.launch { repo.refresh(city) }
    }

    fun toggleSeen(city: City, title: String) = act { repo.setSeen(city, title, !state.value.isSeen(title)) }

    fun toggleWatchlist(city: City, add: WatchlistAdd) = act {
        if (add.tmdbId in state.value.watchlistIds) repo.removeFromWatchlist(city, add.tmdbId)
        else repo.addToWatchlist(city, add)
    }

    fun logout() = repo.logout()
}

/** A user-facing message for an account call that failed. */
@StringRes
fun accountErrorText(e: Throwable): Int = when {
    e is NotLoggedInException -> R.string.login_required
    e is ApiException && e.code == 401 -> R.string.session_expired
    e is ApiException && e.code == 429 -> R.string.too_many_attempts
    e is ApiException -> R.string.server_error
    else -> R.string.network_error
}

/** Account actions for screens deep in the tree (film page, film cards); null in tests. */
class AccountHooks(val vm: AccountViewModel, val openLogin: () -> Unit)

val LocalAccount = staticCompositionLocalOf<AccountHooks?> { null }

/** The person's lists, for the "seen" / "on your list" marks on film cards. */
val LocalAccountState = compositionLocalOf { AccountState() }
