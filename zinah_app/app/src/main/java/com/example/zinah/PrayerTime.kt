package com.example.zinah

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Represents a single obligatory prayer.
 *
 * @param key       Stable identifier used for SharedPreferences and alarm request codes.
 * @param nameAr    Arabic display name (e.g. "الفجر").
 * @param apiField  Field name returned by the Aladhan API "timings" object (e.g. "Fajr").
 * @param icon      A Compose icon token (kept as String so we don't import icons here).
 */
enum class PrayerType(val key: String, val nameAr: String, val apiField: String, val icon: String) {
    FAJR("fajr", "الفجر", "Fajr", "fajr"),
    DHUHR("dhuhr", "الظهر", "Dhuhr", "dhuhr"),
    ASR("asr", "العصر", "Asr", "asr"),
    MAGHRIB("maghrib", "المغرب", "Maghrib", "maghrib"),
    ISHA("isha", "العشاء", "Isha", "isha");

    companion object {
        /** Request-code offset for AlarmManager PendingIntents (must be unique per prayer). */
        fun alarmRequestCode(prayer: PrayerType): Int = 5000 + prayer.ordinal
    }
}

/**
 * Parsed prayer timings for a single day.
 *
 * Aladhan returns times as "HH:mm" strings (24-hour, local time of the requested timezone).
 * We convert each one to a [Calendar] anchored to today's date so that we can compute
 * "next prayer in X minutes" and schedule AlarmManager triggers.
 */
data class PrayerTimings(
    val fajr: Calendar,
    val dhuhr: Calendar,
    val asr: Calendar,
    val maghrib: Calendar,
    val isha: Calendar,
    val sunrise: Calendar,
    val hijriDate: String,
    val gregorianDate: String
) {

    /** Returns the [Calendar] for the given [prayer]. */
    fun timeFor(prayer: PrayerType): Calendar = when (prayer) {
        PrayerType.FAJR -> fajr
        PrayerType.DHUHR -> dhuhr
        PrayerType.ASR -> asr
        PrayerType.MAGHRIB -> maghrib
        PrayerType.ISHA -> isha
    }

    /**
     * Returns the next upcoming prayer relative to [now], or [PrayerType.FAJR] of tomorrow
     * if all of today's prayers have already passed.
     *
     * @return Pair of (prayer, calendar). The calendar may be tomorrow's Fajr.
     */
    fun nextPrayer(now: Calendar): Pair<PrayerType, Calendar> {
        val ordered = listOf(
            PrayerType.FAJR to fajr,
            PrayerType.DHUHR to dhuhr,
            PrayerType.ASR to asr,
            PrayerType.MAGHRIB to maghrib,
            PrayerType.ISHA to isha
        )
        for ((p, cal) in ordered) {
            if (cal.timeInMillis > now.timeInMillis) {
                return p to cal
            }
        }
        // All today's prayers passed — next is tomorrow's Fajr
        val tomorrowFajr = fajr.clone() as Calendar
        tomorrowFajr.add(Calendar.DAY_OF_YEAR, 1)
        return PrayerType.FAJR to tomorrowFajr
    }

    companion object {
        /**
         * Parse the JSON response of `GET /v1/timings/{timestamp}` from Aladhan API.
         *
         * Expected structure (trimmed):
         * ```
         * {
         *   "code": 200,
         *   "data": {
         *     "timings": { "Fajr":"05:00", "Sunrise":"06:20", "Dhuhr":"12:30", ... },
         *     "date": {
         *       "readable":"02 Aug 2024",
         *       "hijri": { "date":"27-01-1446", "weekday":{ "ar":"الجمعة" }, ... },
         *       "gregorian": { "date":"02-08-2024", ... }
         *     }
         *   }
         * }
         * ```
         */
        fun fromAladhanJson(json: String, anchorDay: Calendar = Calendar.getInstance()): PrayerTimings {
            val root = JSONObject(json)
            val data = root.getJSONObject("data")
            val timings = data.getJSONObject("timings")

            val fmt = SimpleDateFormat("HH:mm", Locale.US)
            fmt.timeZone = TimeZone.getDefault()

            fun parseTime(key: String): Calendar {
                // Aladhan sometimes appends "(EET)" or similar to times — strip everything after space.
                val raw = timings.optString(key).substringBefore(" ").trim()
                val date = fmt.parse(raw)
                val cal = anchorDay.clone() as Calendar
                cal.timeInMillis = date.time
                // Re-anchor to today's date (fmt.parse uses 1970-01-01)
                val today = Calendar.getInstance()
                cal.set(Calendar.YEAR, today.get(Calendar.YEAR))
                cal.set(Calendar.MONTH, today.get(Calendar.MONTH))
                cal.set(Calendar.DAY_OF_MONTH, today.get(Calendar.DAY_OF_MONTH))
                return cal
            }

            // Hijri date string (Arabic weekday + hijri date)
            val hijriDate = try {
                val hijri = data.getJSONObject("date").getJSONObject("hijri")
                val weekdayAr = hijri.getJSONObject("weekday").optString("ar", "")
                val hijriDateStr = hijri.optString("date", "")
                "$weekdayAr $hijriDateStr".trim()
            } catch (e: Exception) {
                ""
            }

            val gregorianDate = try {
                data.getJSONObject("date").getJSONObject("gregorian").optString("date", "")
            } catch (e: Exception) {
                ""
            }

            return PrayerTimings(
                fajr = parseTime("Fajr"),
                dhuhr = parseTime("Dhuhr"),
                asr = parseTime("Asr"),
                maghrib = parseTime("Maghrib"),
                isha = parseTime("Isha"),
                sunrise = parseTime("Sunrise"),
                hijriDate = hijriDate,
                gregorianDate = gregorianDate
            )
        }
    }
}
