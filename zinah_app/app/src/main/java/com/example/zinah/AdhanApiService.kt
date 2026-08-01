package com.example.zinah

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

/**
 * Aladhan API client — uses HttpURLConnection instead of OkHttp for maximum compatibility.
 *
 * HttpURLConnection is built into Android since API 1 and does not require any external
 * dependency. It works reliably on all Android versions (7.0+) and all device manufacturers.
 *
 * Endpoints used:
 *   - GET https://api.aladhan.com/v1/timings/{timestamp}?latitude=..&longitude=..&method=..
 *   - GET https://api.aladhan.com/v1/timingsByCity?city=..&country=..&method=..
 */
object AdhanApiService {

    private const val TAG = "AdhanApiService"
    private const val BASE_URL = "https://api.aladhan.com/v1"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 20_000

    /** Result wrapper — caller decides what to do on failure. */
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val message: String, val cause: Throwable? = null) : Result<Nothing>()
    }

    suspend fun fetchTimingsByCoordinates(
        latitude: Double,
        longitude: Double,
        method: Int
    ): Result<PrayerTimings> = withContext(Dispatchers.IO) {
        try {
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val url = "$BASE_URL/timings/$timestamp" +
                    "?latitude=$latitude" +
                    "&longitude=$longitude" +
                    "&method=$method"
            executeRequest(url)
        } catch (e: Throwable) {
            Log.e(TAG, "fetchTimingsByCoordinates crashed", e)
            Result.Error("تعذّر جلب المواقيت: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    suspend fun fetchTimingsByCity(cityLabel: String, method: Int): Result<PrayerTimings> =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: Throwable) {
                Log.e(TAG, "fetchTimingsByCity crashed", e)
                Result.Error("تعذّر جلب المواقيت: ${e.message ?: "خطأ غير معروف"}")
            }
        }

    private fun executeRequest(urlString: String): Result<PrayerTimings> {
        var connection: HttpURLConnection? = null
        return try {
            Log.d(TAG, "Requesting: $urlString")
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/json")
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode != 200) {
                Log.e(TAG, "HTTP error: $responseCode")
                return Result.Error("فشل الاتصال بالخدمة: HTTP $responseCode")
            }

            val body = connection.inputStream?.bufferedReader()?.use { it.readText() }
                ?: return Result.Error("استجابة فارغة من الخادم")

            Log.d(TAG, "Response length: ${body.length}")
            val parsed = PrayerTimings.fromAladhanJson(body, Calendar.getInstance())
            Result.Success(parsed)
        } catch (e: Throwable) {
            Log.e(TAG, "executeRequest failed", e)
            Result.Error("تعذّر جلب المواقيت: ${e.message ?: "خطأ في الشبكة"}", e)
        } finally {
            try {
                connection?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "disconnect failed: ${e.message}")
            }
        }
    }
}
