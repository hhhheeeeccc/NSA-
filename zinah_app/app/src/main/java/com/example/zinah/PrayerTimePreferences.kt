package com.example.zinah

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences wrapper for all prayer-times settings.
 *
 * Stored in the same "ZinahPrefs" file as the dhikr settings, so the entire app
 * has a single source of truth.
 *
 * Keys:
 *  - prayer_times_enabled        (Boolean) master on/off for the whole feature
 *  - prayer_fajr_enabled         (Boolean) per-prayer adhan toggles
 *  - prayer_dhuhr_enabled
 *  - prayer_asr_enabled
 *  - prayer_maghrib_enabled
 *  - prayer_isha_enabled
 *  - prayer_calc_method          (Int)    Aladhan method id (4 = Umm al-Qura)
 *  - prayer_latitude             (Float)  last cached GPS latitude
 *  - prayer_longitude            (Float)  last cached GPS longitude
 *  - prayer_city_name            (String) last used city label (manual or reverse-geocoded)
 *  - prayer_manual_city          (String) "Makkah,Saudi Arabia" — used when user picks manually
 *  - prayer_use_manual_city      (Boolean) true = use manual city, false = use GPS coords
 *  - prayer_adhan_sound          (Int)    index 0=adhan_makkah (default), 1=adhan_madinah, 2=short_tone
 *  - prayer_full_screen          (Boolean) true = show full-screen AdhanActivity, false = background only
 */
object PrayerTimePreferences {

    private const val PREFS_NAME = "ZinahPrefs"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ---- master toggle ----
    fun isFeatureEnabled(context: Context): Boolean =
        prefs(context).getBoolean("prayer_times_enabled", false)

    fun setFeatureEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("prayer_times_enabled", enabled).apply()
    }

    // ---- per-prayer toggles ----
    fun isPrayerEnabled(context: Context, prayer: PrayerType): Boolean =
        prefs(context).getBoolean("prayer_${prayer.key}_enabled", true)

    fun setPrayerEnabled(context: Context, prayer: PrayerType, enabled: Boolean) {
        prefs(context).edit().putBoolean("prayer_${prayer.key}_enabled", enabled).apply()
    }

    // ---- calculation method (Aladhan method id) ----
    fun getCalculationMethod(context: Context): Int =
        prefs(context).getInt("prayer_calc_method", 4) // 4 = Umm al-Qura

    fun setCalculationMethod(context: Context, methodId: Int) {
        prefs(context).edit().putInt("prayer_calc_method", methodId).apply()
    }

    // ---- cached GPS location ----
    fun saveLocation(context: Context, latitude: Double, longitude: Double, label: String) {
        prefs(context).edit()
            .putFloat("prayer_latitude", latitude.toFloat())
            .putFloat("prayer_longitude", longitude.toFloat())
            .putString("prayer_city_name", label)
            .apply()
    }

    fun getLatitude(context: Context): Double =
        prefs(context).getFloat("prayer_latitude", 21.4225f).toDouble() // default: Makkah

    fun getLongitude(context: Context): Double =
        prefs(context).getFloat("prayer_longitude", 39.8262f).toDouble() // default: Makkah

    fun getCityName(context: Context): String =
        prefs(context).getString("prayer_city_name", "مكة المكرمة") ?: "مكة المكرمة"

    // ---- manual city selection ----
    fun setManualCity(context: Context, cityLabel: String) {
        prefs(context).edit()
            .putString("prayer_manual_city", cityLabel)
            .putBoolean("prayer_use_manual_city", true)
            .apply()
    }

    fun useManualCity(context: Context): Boolean =
        prefs(context).getBoolean("prayer_use_manual_city", false)

    fun setUseManualCity(context: Context, use: Boolean) {
        prefs(context).edit().putBoolean("prayer_use_manual_city", use).apply()
    }

    fun getManualCity(context: Context): String =
        prefs(context).getString("prayer_manual_city", "مكة المكرمة,السعودية")
            ?: "مكة المكرمة,السعودية"

    // ---- adhan sound selection ----
    fun getAdhanSoundIndex(context: Context): Int =
        prefs(context).getInt("prayer_adhan_sound", 0)

    fun setAdhanSoundIndex(context: Context, index: Int) {
        prefs(context).edit().putInt("prayer_adhan_sound", index).apply()
    }

    /** Returns the raw resource ID for the selected adhan sound. */
    fun getAdhanSoundResId(context: Context): Int {
        return when (getAdhanSoundIndex(context)) {
            0 -> R.raw.adhan_makkah
            1 -> R.raw.adhan_madinah
            2 -> R.raw.dhikr_gentle   // short tone fallback
            else -> R.raw.adhan_makkah
        }
    }

    // ---- full-screen toggle ----
    fun isFullScreenAdhan(context: Context): Boolean =
        prefs(context).getBoolean("prayer_full_screen", true)

    fun setFullScreenAdhan(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("prayer_full_screen", enabled).apply()
    }

    // ---- cached prayer timings (JSON) ----
    // We persist the last successfully-fetched timings JSON so that when the user
    // reopens the app, the prayer times screen can display them immediately
    // without forcing the user to press "refresh". The cache is considered stale
    // if it is older than [CACHE_MAX_AGE_HOURS] hours OR if the stored date
    // (gregorianDate) does not match today's date.
    private const val KEY_TIMINGS_JSON = "prayer_timings_json_cache"
    private const val KEY_TIMINGS_CACHED_AT = "prayer_timings_cached_at"
    private const val CACHE_MAX_AGE_HOURS = 12L

    /** Save the raw Aladhan JSON response so it can be restored on next app launch. */
    fun saveCachedTimings(context: Context, json: String) {
        prefs(context).edit()
            .putString(KEY_TIMINGS_JSON, json)
            .putLong(KEY_TIMINGS_CACHED_AT, System.currentTimeMillis())
            .apply()
    }

    /** Returns the cached JSON string, or null if there is no cache. */
    fun getCachedTimingsJson(context: Context): String? =
        prefs(context).getString(KEY_TIMINGS_JSON, null)

    /** Returns the timestamp (millis) when the cache was last written, or 0 if never. */
    fun getCachedTimingsAge(context: Context): Long =
        prefs(context).getLong(KEY_TIMINGS_CACHED_AT, 0L)

    /**
     * Returns true iff the cached timings are still considered fresh enough to
     * display without forcing a re-fetch.
     *
     * Fresh = cached within the last [CACHE_MAX_AGE_HOURS] hours AND the
     * gregorianDate embedded in the JSON matches today's date.
     */
    fun isCacheFresh(context: Context): Boolean {
        val cachedAt = getCachedTimingsAge(context)
        if (cachedAt <= 0L) return false
        val ageMs = System.currentTimeMillis() - cachedAt
        if (ageMs > CACHE_MAX_AGE_HOURS * 60L * 60L * 1000L) return false
        // Also verify the date inside the JSON matches today
        val json = getCachedTimingsJson(context) ?: return false
        return try {
            val root = org.json.JSONObject(json)
            val data = root.getJSONObject("data")
            val gregorian = data.getJSONObject("date").getJSONObject("gregorian")
            val dateStr = gregorian.optString("date", "") // "03-08-2026"
            val today = java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                .format(java.util.Date())
            dateStr == today
        } catch (_: Exception) {
            false
        }
    }

    /** Clears the cached timings (used when the user disables the feature). */
    fun clearCachedTimings(context: Context) {
        prefs(context).edit()
            .remove(KEY_TIMINGS_JSON)
            .remove(KEY_TIMINGS_CACHED_AT)
            .apply()
    }

    /**
     * Available calculation methods (subset of Aladhan's).
     * https://aladhan.com/calculation-methods
     */
    val CALCULATION_METHODS = listOf(
        3 to "رابطة العالم الإسلامي (MWL)",
        4 to "أم القرى (السعودية)",
        5 to "الهيئة المصرية العامة للمساحة",
        8 to "دولة الخليج",
        9 to "معهد الكويت للعلوم",
        10 to "قطر",
        12 to "اتحاد المنظمات الإسلامية في فرنسا",
        13 to "تركيا (ديانت)",
        0 to "شافعي (ISNA - أمريكا الشمالية)"
    )
}
