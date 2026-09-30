package com.devcrumbs.cinema.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.devcrumbs.cinema.MainActivity
import com.devcrumbs.cinema.R
import com.devcrumbs.cinema.data.AccountApi
import com.devcrumbs.cinema.data.AccountRepository
import com.devcrumbs.cinema.data.CityRepository
import com.devcrumbs.cinema.data.KeystoreSessionStore
import com.devcrumbs.cinema.data.PushDevice
import com.devcrumbs.cinema.data.slugify
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Push from every city, through Firebase Cloud Messaging. The backend
 * (`app/services/fcm.py`) sends data-only messages — title, body, the film and
 * the city that sent it — and the app builds the notification, so a tap opens
 * that film in that city. Which cities may alert the person is the account's
 * Email/Alerts switches, decided on the server.
 */
object FirebasePushDevice : PushDevice {
    override suspend fun token(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }.onFailure { cont.resume(null) } // Firebase not initialised (tests)
    }

    override fun forget() {
        runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }
}

class CinemaMessagingService : FirebaseMessagingService() {
    /** FCM rotated this install's token: tell the server (runs on a worker thread). */
    override fun onNewToken(token: String) {
        val cities = CityRepository(this)
        val city = cities.selectedCity() ?: cities.cities.firstOrNull() ?: return
        val repo = AccountRepository(AccountApi(), KeystoreSessionStore(this))
        runBlocking { repo.registerDevice(city, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        PushNotifications.show(this, message.data)
    }
}

object PushNotifications {
    private const val CHANNEL = "watchlist"
    const val EXTRA_CITY = "com.devcrumbs.cinema.CITY"
    const val EXTRA_FILM = "com.devcrumbs.cinema.FILM"

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, data: Map<String, String>) {
        val title = data["title"]?.takeIf { it.isNotBlank() } ?: return
        if (!canNotify(context)) return
        ensureChannel(context)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_CITY, data["city_slug"].orEmpty())
            .putExtra(EXTRA_FILM, slugify(data["movie_title"].orEmpty()))
        // One notification per film and city: a repeat replaces, another film stacks.
        val id = (data["tag"].orEmpty() + data["city_slug"].orEmpty()).hashCode()
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(data["body"])
            .setStyle(NotificationCompat.BigTextStyle().bigText(data["body"]))
            .setSubText(data["city_name"])
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_watchlist), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_watchlist_description) },
        )
    }
}
