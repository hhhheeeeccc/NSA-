package com.example.zinah

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Wraps FusedLocationProviderClient to fetch the current GPS location.
 *
 * Safety notes (this is what previously caused crashes when the user tapped "GPS"):
 *  1. We check Google Play Services availability BEFORE touching FusedLocationProviderClient
 *     — on devices without Play Services (Huawei / sideloaded ROMs) calling it throws
 *     a runtime exception that crashes the app.
 *  2. We catch ALL Throwable (not just Exception) — some Play Services failures come back
 *     as Error subtypes that bypass Exception.
 *  3. All callbacks resume the coroutine with `null` on failure (never throw) so the
 *     surrounding try/catch can convert it to [Result.Error].
 *  4. The Geocoder call is wrapped in its own try/catch — it can throw IOException on
 *     devices without a network connection, and on Android 13+ it requires a background
 *     thread (we're already on Dispatchers.IO so this is fine).
 *  5. We use `WithContext(Dispatchers.IO)` so none of this runs on the main thread.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val LOCATION_TIMEOUT_MS = 15_000L

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

    /**
     * Check whether Google Play Services is available on this device.
     * FusedLocationProviderClient depends on Play Services — calling it on a device
     * that doesn't have Play Services will throw a runtime exception.
     */
    private fun isPlayServicesAvailable(context: Context): Boolean {
        return try {
            val status = GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            status == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            Log.w(TAG, "Play Services availability check failed: ${e.message}")
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(context: Context): Result = withContext(Dispatchers.IO) {
        // 0) Pre-flight checks
        if (!hasLocationPermission(context)) {
            return@withContext Result.Error("إذن الموقع غير ممنوح — فعّل الإذن من الإعدادات")
        }
        if (!isPlayServicesAvailable(context)) {
            return@withContext Result.Error("خدمات Google Play غير متوفرة على هذا الجهاز — استخدم اختيار المدينة يدويًا")
        }

        val client = try {
            LocationServices.getFusedLocationProviderClient(context)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get FusedLocationProviderClient", e)
            return@withContext Result.Error("تعذّر الوصول إلى خدمات الموقع: ${e.message}")
        }

        // 1) Try the fast path: last known location (no GPS wake-up needed)
        try {
            val last = withTimeoutOrNull(3_000L) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        client.getLastLocation()
                            .addOnSuccessListener { loc ->
                                try { if (cont.isActive) cont.resume(loc) } catch (_: Exception) {}
                            }
                            .addOnFailureListener { e ->
                                Log.w(TAG, "getLastLocation failure: ${e.message}")
                                try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                            }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException in getLastLocation", e)
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in getLastLocation", e)
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    }
                }
            }
            if (last != null && (last.latitude != 0.0 || last.longitude != 0.0)) {
                val label = reverseGeocode(context, last.latitude, last.longitude)
                return@withContext Result.Success(last.latitude, last.longitude, label)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getLastLocation path failed: ${e.message}")
            // Continue to slow path
        }

        // 2) Slow path: request a fresh single location update
        try {
            val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                            .addOnSuccessListener { loc ->
                                try { if (cont.isActive) cont.resume(loc) } catch (_: Exception) {}
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "getCurrentLocation failure: ${e.message}", e)
                                try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                            }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException in getCurrentLocation", e)
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    } catch (e: Exception) {
                        Log.e(TAG, "Exception in getCurrentLocation", e)
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    }
                }
            }
            if (fresh != null && (fresh.latitude != 0.0 || fresh.longitude != 0.0)) {
                val label = reverseGeocode(context, fresh.latitude, fresh.longitude)
                return@withContext Result.Success(fresh.latitude, fresh.longitude, label)
            }
            return@withContext Result.Error(
                "تعذّر تحديد الموقع — تأكد من تفعيل GPS وإعطاء الإذن، أو استخدم اختيار المدينة يدويًا"
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Fresh location fetch failed", e)
            return@withContext Result.Error("خطأ في تحديد الموقع: ${e.message ?: "سبب غير معروف"}")
        }
    }

    /**
     * Reverse-geocode (latitude, longitude) → "City, Country" using Android's built-in Geocoder.
     *
     * On Android 13+ (API 33+) the synchronous [Geocoder.getFromLocation] is deprecated
     * and may return null even on success — we still try it first because it works
     * synchronously and is faster. If it returns null or throws, we fall back to
     * the coordinates string.
     */
    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Use the async API on Android 13+ via blocking wrapper
                suspendCancellableCoroutineWithGeocoder(geocoder, lat, lon)
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(lat, lon, 1)
            }
            val addr = addresses?.firstOrNull()
            when {
                addr == null -> String.format(Locale.US, "%.2f, %.2f", lat, lon)
                !addr.locality.isNullOrBlank() -> "${addr.locality}, ${addr.countryName}"
                !addr.adminArea.isNullOrBlank() -> "${addr.adminArea}, ${addr.countryName}"
                !addr.subAdminArea.isNullOrBlank() -> "${addr.subAdminArea}, ${addr.countryName}"
                else -> addr.countryName ?: String.format(Locale.US, "%.2f, %.2f", lat, lon)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverse geocode failed: ${e.message}")
            String.format(Locale.US, "%.2f, %.2f", lat, lon)
        }
    }

    /**
     * Wrapper around the Android 13+ async Geocoder API that blocks the calling coroutine
     * until the result is available (or a 3-second timeout fires).
     */
    private fun suspendCancellableCoroutineWithGeocoder(
        geocoder: Geocoder,
        lat: Double,
        lon: Double
    ): List<android.location.Address>? {
        return try {
            var result: List<android.location.Address>? = null
            val lock = java.util.concurrent.Semaphore(0)
            geocoder.getFromLocation(lat, lon, 1) { addresses ->
                result = addresses
                lock.release()
            }
            lock.tryAcquire(3, java.util.concurrent.TimeUnit.SECONDS)
            result
        } catch (e: Exception) {
            Log.w(TAG, "Async geocoder failed: ${e.message}")
            null
        }
    }
}
