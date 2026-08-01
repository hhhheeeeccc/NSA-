package com.example.zinah

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
 * Robust location fetcher with multiple fallbacks:
 *
 *  1. Check app permission
 *  2. Check device location services are enabled (different from app permission!)
 *     — many users enable permission but forget to turn on GPS in Android settings
 *  3. Try FusedLocationProviderClient (fastest, most accurate)
 *  4. If that fails, fall back to legacy LocationManager (GPS_PROVIDER / NETWORK_PROVIDER)
 *     — this works even without Google Play Services
 *  5. As a last resort, return the last known location from any provider
 *
 * All callbacks resume with `null` on failure (never throw).
 * All paths are wrapped in try/catch (Throwable) so the app never crashes.
 */
object LocationHelper {

    private const val TAG = "LocationHelper"
    private const val LOCATION_TIMEOUT_MS = 20_000L

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
     * Check whether the device's location services are enabled in Android settings.
     * This is DIFFERENT from app permission — even with permission granted, the user
     * can disable location globally in Settings → Location.
     *
     * On Android 28+ we use Settings.FusedLocationUtil.isLocationModeAvailable.
     * On older versions we check GPS_PROVIDER and NETWORK_PROVIDER directly.
     */
    fun isLocationEnabled(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.isLocationEnabled
            } else {
                val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } catch (e: Exception) {
            Log.w(TAG, "isLocationEnabled check failed: ${e.message}")
            true // assume enabled — let the actual call fail later if it's truly disabled
        }
    }

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

        if (!isLocationEnabled(context)) {
            return@withContext Result.Error(
                "خدمات الموقع معطّلة في الجهاز. افتح إعدادات الأندرويد → الموقع → فعّل الموقع"
            )
        }

        // 1) Try FusedLocationProviderClient if Play Services is available
        if (isPlayServicesAvailable(context)) {
            val fusedResult = tryFusedLocation(context)
            if (fusedResult is Result.Success) {
                return@withContext fusedResult
            }
            Log.w(TAG, "FusedLocation failed, falling back to LocationManager")
        } else {
            Log.w(TAG, "Play Services not available, using LocationManager directly")
        }

        // 2) Fallback: legacy LocationManager (works without Play Services)
        val legacyResult = tryLegacyLocationManager(context)
        if (legacyResult is Result.Success) {
            return@withContext legacyResult
        }

        // 3) Last resort: any cached location from any provider
        val cached = tryCachedLocation(context)
        if (cached is Result.Success) {
            return@withContext cached
        }

        Result.Error(
            "تعذّر تحديد الموقع. تأكد من:\n" +
            "1. تفعيل الموقع في إعدادات الجهاز\n" +
            "2. السماح بالوصول للموقع للتطبيق\n" +
            "3. وجود اتصال إنترنت\n" +
            "أو استخدم اختيار المدينة يدويًا"
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun tryFusedLocation(context: Context): Result {
        return try {
            val client = LocationServices.getFusedLocationProviderClient(context)

            // Fast path: last known location
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
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    } catch (e: Exception) {
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    }
                }
            }
            if (last != null && (last.latitude != 0.0 || last.longitude != 0.0)) {
                val label = reverseGeocode(context, last.latitude, last.longitude)
                return Result.Success(last.latitude, last.longitude, label)
            }

            // Slow path: fresh location request
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
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    } catch (e: Exception) {
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    }
                }
            }
            if (fresh != null && (fresh.latitude != 0.0 || fresh.longitude != 0.0)) {
                val label = reverseGeocode(context, fresh.latitude, fresh.longitude)
                return Result.Success(fresh.latitude, fresh.longitude, label)
            }

            Result.Error("FusedLocation returned null")
        } catch (e: Throwable) {
            Log.e(TAG, "tryFusedLocation crashed", e)
            Result.Error("FusedLocation crashed: ${e.message}")
        }
    }

    /**
     * Legacy fallback using android.location.LocationManager.
     * Works on ALL Android devices including those without Google Play Services.
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryLegacyLocationManager(context: Context): Result {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Pick the best available provider
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) -> LocationManager.PASSIVE_PROVIDER
                else -> return Result.Error("No location provider enabled")
            }
            Log.d(TAG, "Using legacy provider: $provider")

            // Try last known location from this provider first (instant)
            val lastKnown = lm.getLastKnownLocation(provider)
            if (lastKnown != null && (lastKnown.latitude != 0.0 || lastKnown.longitude != 0.0)) {
                val label = reverseGeocode(context, lastKnown.latitude, lastKnown.longitude)
                return Result.Success(lastKnown.latitude, lastKnown.longitude, label)
            }

            // Request a single fresh update with timeout
            val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            try {
                                if (cont.isActive) cont.resume(location)
                            } catch (_: Exception) {}
                            try { lm.removeUpdates(this) } catch (_: Exception) {}
                        }
                        override fun onStatusChanged(p: String?, s: Int, b: Bundle?) {}
                        override fun onProviderEnabled(p: String) {}
                        override fun onProviderDisabled(p: String) {
                            try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                        }
                    }
                    try {
                        lm.requestSingleUpdate(provider, listener, android.os.Looper.getMainLooper())
                    } catch (e: SecurityException) {
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    } catch (e: Exception) {
                        try { if (cont.isActive) cont.resume(null) } catch (_: Exception) {}
                    }
                }
            }
            if (fresh != null && (fresh.latitude != 0.0 || fresh.longitude != 0.0)) {
                val label = reverseGeocode(context, fresh.latitude, fresh.longitude)
                return Result.Success(fresh.latitude, fresh.longitude, label)
            }

            Result.Error("Legacy LocationManager returned null")
        } catch (e: Throwable) {
            Log.e(TAG, "tryLegacyLocationManager crashed", e)
            Result.Error("Legacy crashed: ${e.message}")
        }
    }

    /**
     * Absolute last resort: grab any cached location from any provider.
     */
    @SuppressLint("MissingPermission")
    private suspend fun tryCachedLocation(context: Context): Result {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = lm.allProviders
            for (provider in providers) {
                try {
                    val loc = lm.getLastKnownLocation(provider)
                    if (loc != null && (loc.latitude != 0.0 || loc.longitude != 0.0)) {
                        val label = reverseGeocode(context, loc.latitude, loc.longitude)
                        return Result.Success(loc.latitude, loc.longitude, label)
                    }
                } catch (e: SecurityException) {
                    continue
                }
            }
            Result.Error("No cached location available")
        } catch (e: Throwable) {
            Result.Error("Cached crashed: ${e.message}")
        }
    }

    private fun reverseGeocode(context: Context, lat: Double, lon: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
