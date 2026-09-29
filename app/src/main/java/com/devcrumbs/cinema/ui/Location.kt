package com.devcrumbs.cinema.ui

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.devcrumbs.cinema.data.LatLng
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One coarse fix for "Vicino a me", without Google Play services: the platform
 * LocationManager, network provider first. Null when no fix comes in 10 s.
 * The caller must hold ACCESS_COARSE_LOCATION.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): LatLng? {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    val fresh = withTimeoutOrNull(10_000) {
        for (provider in providers) {
            val fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                suspendCancellableCoroutine<Location?> { cont ->
                    lm.getCurrentLocation(provider, null, ContextCompat.getMainExecutor(context)) { cont.resume(it) }
                }
            } else {
                @Suppress("DEPRECATION")
                lm.getLastKnownLocation(provider)
            }
            if (fix != null) return@withTimeoutOrNull fix
        }
        null
    }
    val loc = fresh ?: providers.firstNotNullOfOrNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
    return loc?.let { LatLng(it.latitude, it.longitude) }
}
