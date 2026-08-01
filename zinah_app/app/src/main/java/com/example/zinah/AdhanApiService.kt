package com.example.zinah

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Aladhan API client.
 *
 * Endpoints used:
 *   - GET https://api.aladhan.com/v1/timings/{timestamp}?latitude=..&longitude=..&method=..
 *   - GET https://api.aladhan.com/v1/timingsByCity?city=..&country=..&method=..
 *
 * Documentation: https://aladhan.com/prayer-times-api
 *
 * The API is free, no key required, and returns prayer times in the timezone of the
 * requested location (we re-anchor the parsed "HH:mm" to today's date in [PrayerTimings]).
 */
object AdhanApiService {

    private const val TAG = "AdhanApiService"
    private const val BASE_URL = "https://api.aladhan.com/v1"

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Result wrapper — caller decides what to do on failure (e.g. use cached timings). */
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    }

    /**
     * Fetch today's prayer timings for the given GPS coordinates.
     *
     * @param latitude  WGS84 latitude
     * @param longitude WGS84 longitude
     * @param method    Aladhan calculation method id (see [PrayerTimePreferences.CALCULATION_METHODS])
     */
    suspend fun fetchTimingsByCoordinates(
        latitude: Double,
        longitude: Double,
        method: Int
    ): Result<PrayerTimings> = withContext(Dispatchers.IO) {
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val url = "$BASE_URL/timings/$timestamp" +
                "?latitude=$latitude" +
                "&longitude=$longitude" +
                "&method=$method"

        executeRequest(url)
    }

    /**
     * Fetch today's prayer timings for a city/country pair.
     *
     * @param cityLabel  e.g. "Makkah,Saudi Arabia" — split on the first comma.
     */
    suspend fun fetchTimingsByCity(cityLabel: String, method: Int): Result<PrayerTimings> =
        withContext(Dispatchers.IO) {
            val parts = cityLabel.split(",", limit = 2)
            if (parts.size != 2) {
                return@withContext Result.Error("صيغة المدينة غير صحيحة (يجب أن تكون: المدينة,الدولة)")
            }
            val city = URLEncoder.encode(parts[0].trim(), "UTF-8")
            val country = URLEncoder.encode(parts[1].trim(), "UTF-8")
            val url = "$BASE_URL/timingsByCity" +
                    "?city=$city" +
                    "&country=$country" +
                    "&method=$method"
            executeRequest(url)
        }

    private fun executeRequest(url: String): Result<PrayerTimings> {
        return try {
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.Error("فشل الاتصال بالخدمة: HTTP ${response.code}")
                }
                val body = response.body?.string()
                    ?: return Result.Error("استجابة فارغة من الخادم")
                val parsed = PrayerTimings.fromAladhanJson(body, Calendar.getInstance())
                Result.Success(parsed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Aladhan API call failed", e)
            Result.Error("تعذّر جلب المواقيت: ${e.message}", e)
        }
    }
}
