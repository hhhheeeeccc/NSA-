package com.example.zinah

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wraps FusedLocationProviderClient to fetch the current GPS location.
 *
 * Behavior:
 *  - Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
 *  - Returns the last known location if available (fast path).
 *  - Otherwise requests a fresh single location update with a 10s timeout.
 *  - On success also does a reverse-geocode to get a human-readable city name.
 *
 * Returns null on any failure (permission denied, timeout, no GPS fix) —
 * callers should fall back to [PrayerTimePreferences.getManualCity] in that case.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val LOCATION_TIMEOUT_MS = 10_000L

    sealed class Result {
        data class Success(val latitude: Double, val longitude: Double, val label: String) : Result()
        data class Error(val message: String) : Result()
    }

    fun hasLocationPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) {
            return@withContext Result.Error("إذن الموقع غير ممنوح")
        }

        val client = LocationServices.getFusedLocationProviderClient(context)

        // 1) Try the fast path: last known location
        try {
            val last = withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    client.getLastLocation()
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
            if (last != null) {
                val label = reverseGeocode(context, last.latitude, last.longitude)
                return@withContext Result.Success(last.latitude, last.longitude, label)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getLastLocation failed: ${e.message}")
        }

        // 2) Slow path: request a fresh location
        try {
            val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                        .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "getCurrentLocation failed", e)
                            if (cont.isActive) cont.resume(null)
                        }
                }
            }
            if (fresh != null) {
                val label = reverseGeocode(context, fresh.latitude, fresh.longitude)
                return@withContext Result.Success(fresh.latitude, fresh.longitude, label)
            }
            return@withContext Result.Error("تعذّر تحديد الموقع (تأكد من تفعيل GPS)")
        } catch (e: Exception) {
            Log.e(TAG, "Fresh location fetch failed", e)
            return@withContext Result.Error("خطأ في تحديد الموقع: ${e.message}")
        }
    }

    /**
     * Reverse-geocode (latitude, longitude) → "City, Country" using Android's built-in Geocoder.
     * Falls back to a string like "21.42, 39.83" if geocoding fails.
     */
    private fun reverseGeocode(context: Context, lat: Double, lon: String): String =
        reverseGeocode(context, lat, lon.toDouble())

    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            val addr = addresses?.firstOrNull()
            when {
                addr == null -> String.format(Locale.US, "%.2f, %.2f", lat, lon)
                !addr.locality.isNullOrBlank() -> "${addr.locality}, ${addr.countryName}"
                !addr.adminArea.isNullOrBlank() -> "${addr.adminArea}, ${addr.countryName}"
                else -> addr.countryName ?: String.format(Locale.US, "%.2f, %.2f", lat, lon)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}")
            String.format(Locale.US, "%.2f, %.2f", lat, lon)
        }
    }
}
