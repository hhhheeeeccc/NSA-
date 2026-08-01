package com.example.zinah

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Compose screen for the "مواقيت الصلاة" tab.
 *
 * Contains:
 *  - Master toggle: enable/disable the entire prayer-times feature
 *  - City selector: "use GPS" toggle + manual city input
 *  - Calculation method dropdown
 *  - Adhan sound selector (Makkah / Madinah / short tone)
 *  - Full-screen adhan toggle
 *  - Per-prayer switches (Fajr / Dhuhr / Asr / Maghrib / Isha)
 *  - Next prayer countdown
 *  - Refresh button to fetch fresh timings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ---- state ----
    var featureEnabled by remember {
        mutableStateOf(PrayerTimePreferences.isFeatureEnabled(context))
    }
    var useGps by remember { mutableStateOf(!PrayerTimePreferences.useManualCity(context)) }
    var manualCity by remember { mutableStateOf(PrayerTimePreferences.getManualCity(context)) }
    var cityName by remember { mutableStateOf(PrayerTimePreferences.getCityName(context)) }
    var methodIndex by remember {
        mutableStateOf(PrayerTimePreferences.CALCULATION_METHODS.indexOfFirst {
            it.first == PrayerTimePreferences.getCalculationMethod(context)
        }.coerceAtLeast(0))
    }
    var adhanSoundIndex by remember {
        mutableStateOf(PrayerTimePreferences.getAdhanSoundIndex(context))
    }
    var fullScreen by remember { mutableStateOf(PrayerTimePreferences.isFullScreenAdhan(context)) }
    var timings by remember { mutableStateOf<PrayerTimings?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showMethodDropdown by remember { mutableStateOf(false) }

    // tick every 30 seconds for the countdown
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowTick = System.currentTimeMillis()
        }
    }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.any { it }
        if (granted) {
            scope.launch { refreshTimings(context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it })
            }
        }
    }

    // ---- auto-load on first launch ----
    LaunchedEffect(Unit) {
        refreshTimings(context, useGps, manualCity, methodIndex,
            setTimings = { timings = it },
            setLoading = { isLoading = it },
            setError = { errorMessage = it },
            setCityName = { cityName = it })
    }

    Scaffold(
        containerColor = Color(0xFFF1F8E9)
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== Master toggle card =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32)),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "مواقيت الصلاة",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "تشغيل الأذان تلقائيًا عند دخول الوقت",
                                    color = Color.White.copy(alpha = 0.85f),
                                    fontSize = 13.sp
                                )
                            }
                            Switch(
                                checked = featureEnabled,
                                onCheckedChange = { newValue ->
                                    featureEnabled = newValue
                                    PrayerTimePreferences.setFeatureEnabled(context, newValue)
                                    if (newValue) {
                                        scope.launch {
                                            refreshTimings(context, useGps, manualCity, methodIndex,
                                                setTimings = { timings = it },
                                                setLoading = { isLoading = it },
                                                setError = { errorMessage = it },
                                                setCityName = { cityName = it })
                                        }
                                    } else {
                                        PrayerTimeScheduler.cancelAll(context)
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFD700),
                                    checkedTrackColor = Color.White.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }
            }

            // ===== City & location =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "الموقع: $cityName",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                if (useGps) {
                                    val perms = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        scope.launch {
                                            refreshTimings(context, useGps, manualCity, methodIndex,
                                                setTimings = { timings = it },
                                                setLoading = { isLoading = it },
                                                setError = { errorMessage = it },
                                                setCityName = { cityName = it })
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(perms)
                                    }
                                } else {
                                    scope.launch {
                                        refreshTimings(context, useGps, manualCity, methodIndex,
                                            setTimings = { timings = it },
                                            setLoading = { isLoading = it },
                                            setError = { errorMessage = it },
                                            setCityName = { cityName = it })
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = useGps,
                                onClick = {
                                    useGps = true
                                    PrayerTimePreferences.setUseManualCity(context, false)
                                    val perms = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        scope.launch {
                                            refreshTimings(context, useGps, manualCity, methodIndex,
                                                setTimings = { timings = it },
                                                setLoading = { isLoading = it },
                                                setError = { errorMessage = it },
                                                setCityName = { cityName = it })
                                        }
                                    } else {
                                        locationPermissionLauncher.launch(perms)
                                    }
                                },
                                label = { Text("GPS تلقائي", fontSize = 12.sp) }
                            )
                            FilterChip(
                                selected = !useGps,
                                onClick = { useGps = false },
                                label = { Text("اختيار يدوي", fontSize = 12.sp) }
                            )
                        }
                        if (!useGps) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualCity,
                                onValueChange = { manualCity = it },
                                label = { Text("المدينة,الدولة", fontSize = 12.sp) },
                                placeholder = { Text("Makkah,Saudi Arabia") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = {
                                        PrayerTimePreferences.setManualCity(context, manualCity)
                                        scope.launch {
                                            refreshTimings(context, useGps, manualCity, methodIndex,
                                                setTimings = { timings = it },
                                                setLoading = { isLoading = it },
                                                setError = { errorMessage = it },
                                                setCityName = { cityName = it })
                                        }
                                    }) { Text("حفظ", fontSize = 12.sp) }
                                }
                            )
                        }
                    }
                }
            }

            // ===== Next prayer countdown =====
            item {
                val now = Calendar.getInstance().apply { timeInMillis = nowTick }
                val next = timings?.nextPrayer(now)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("الصلاة القادمة", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(4.dp))
                        if (next != null) {
                            val (prayer, cal) = next
                            Text(
                                "${prayer.nameAr} • ${formatTime(cal)}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1B5E20)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "بعد ${formatRemaining(now, cal)}",
                                fontSize = 14.sp,
                                color = Color(0xFF5D4037)
                            )
                        } else {
                            Text("...", fontSize = 24.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // ===== Today's 5 prayers list =====
            val t = timings
            if (t != null) {
                items(PrayerType.entries) { prayer ->
                    PrayerRow(
                        prayer = prayer,
                        cal = t.timeFor(prayer),
                        isNext = t.nextPrayer(Calendar.getInstance().apply {
                            timeInMillis = nowTick
                        }).first == prayer
                    )
                }
            }

            // ===== Per-prayer toggles =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "تفعيل الأذان لكل صلاة",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color(0xFF1B5E20)
                )
            }
            items(PrayerType.entries) { prayer ->
                var enabled by remember {
                    mutableStateOf(PrayerTimePreferences.isPrayerEnabled(context, prayer))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            prayer.nameAr,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                enabled = newValue
                                PrayerTimePreferences.setPrayerEnabled(context, prayer, newValue)
                                if (featureEnabled && timings != null && newValue) {
                                    val cal = timings!!.timeFor(prayer)
                                    if (cal.timeInMillis > System.currentTimeMillis()) {
                                        PrayerTimeScheduler.scheduleOne(
                                            context, prayer, cal.timeInMillis
                                        )
                                    }
                                } else if (!newValue) {
                                    // cancel this prayer's alarm
                                    val am = context.getSystemService(android.content.Context.ALARM_SERVICE)
                                            as android.app.AlarmManager
                                    val i = android.content.Intent(context, PrayerAlarmReceiver::class.java).apply {
                                        action = "com.example.zinah.PRAYER_ALARM"
                                    }
                                    val pi = android.app.PendingIntent.getBroadcast(
                                        context,
                                        PrayerType.alarmRequestCode(prayer),
                                        i,
                                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                            android.app.PendingIntent.FLAG_IMMUTABLE
                                    )
                                    am.cancel(pi)
                                }
                            }
                        )
                    }
                }
            }

            // ===== Advanced settings =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("إعدادات متقدمة", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Calculation method dropdown
                        Box {
                            OutlinedButton(onClick = { showMethodDropdown = true }) {
                                Text(
                                    "طريقة الحساب: ${PrayerTimePreferences.CALCULATION_METHODS[methodIndex].second}",
                                    fontSize = 12.sp
                                )
                            }
                            DropdownMenu(
                                expanded = showMethodDropdown,
                                onDismissRequest = { showMethodDropdown = false }
                            ) {
                                PrayerTimePreferences.CALCULATION_METHODS.forEachIndexed { idx, pair ->
                                    DropdownMenuItem(
                                        text = { Text(pair.second, fontSize = 13.sp) },
                                        onClick = {
                                            methodIndex = idx
                                            PrayerTimePreferences.setCalculationMethod(context, pair.first)
                                            showMethodDropdown = false
                                            scope.launch {
                                                refreshTimings(context, useGps, manualCity, methodIndex,
                                                    setTimings = { timings = it },
                                                    setLoading = { isLoading = it },
                                                    setError = { errorMessage = it },
                                                    setCityName = { cityName = it })
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Adhan sound selector
                        Text("صوت الأذان:", fontSize = 13.sp, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("مكة" to 0, "المدينة" to 1, "نغمة قصيرة" to 2).forEach { (label, idx) ->
                                FilterChip(
                                    selected = adhanSoundIndex == idx,
                                    onClick = {
                                        adhanSoundIndex = idx
                                        PrayerTimePreferences.setAdhanSoundIndex(context, idx)
                                    },
                                    label = { Text(label, fontSize = 12.sp) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Full-screen adhan toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("إظهار شاشة الأذان كاملة", fontSize = 14.sp)
                                Text(
                                    "نافذة منبثقة عند دخول الوقت مع زر إيقاف",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            Switch(
                                checked = fullScreen,
                                onCheckedChange = {
                                    fullScreen = it
                                    PrayerTimePreferences.setFullScreenAdhan(context, it)
                                }
                            )
                        }
                    }
                }
            }

            // ===== Error / loading =====
            if (isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1))
                    ) {
                        Text(
                            "جاري جلب المواقيت...",
                            modifier = Modifier.padding(16.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Text(
                            errorMessage!!,
                            modifier = Modifier.padding(16.dp),
                            color = Color(0xFFB71C1C),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ===== Hijri date =====
            val hijri = timings?.hijriDate
            if (!hijri.isNullOrBlank()) {
                item {
                    Text(
                        "التاريخ الهجري: $hijri",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(64.dp)) } // bottom padding for nav
        }
    }
}

@Composable
private fun PrayerRow(prayer: PrayerType, cal: java.util.Calendar, isNext: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) Color(0xFF2E7D32) else Color.White
        ),
        elevation = CardDefaults.cardElevation(if (isNext) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                prayer.nameAr,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isNext) Color.White else Color(0xFF1B5E20)
            )
            Text(
                formatTime(cal),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = if (isNext) Color(0xFFFFD700) else Color.Black
            )
        }
    }
}

// ----- helpers -----

private fun formatTime(cal: java.util.Calendar): String {
    val fmt = SimpleDateFormat("hh:mm a", Locale("ar"))
    return fmt.format(cal.time)
}

private fun formatRemaining(now: java.util.Calendar, target: java.util.Calendar): String {
    val diff = target.timeInMillis - now.timeInMillis
    if (diff <= 0) return "الآن"
    val hours = TimeUnit.MILLISECONDS.toHours(diff)
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
    return when {
        hours > 0 && mins > 0 -> "$hours ساعة و $mins دقيقة"
        hours > 0 -> "$hours ساعة"
        else -> "$mins دقيقة"
    }
}

private suspend fun refreshTimings(
    context: Context,
    useGps: Boolean,
    manualCity: String,
    methodIndex: Int,
    setTimings: (PrayerTimings?) -> Unit,
    setLoading: (Boolean) -> Unit,
    setError: (String?) -> Unit,
    setCityName: (String) -> Unit
) {
    if (!PrayerTimePreferences.isFeatureEnabled(context)) {
        setError(null)
        return
    }
    setLoading(true)
    setError(null)
    val method = PrayerTimePreferences.CALCULATION_METHODS[methodIndex].first

    val result = withContext(Dispatchers.IO) {
        if (useGps) {
            if (!LocationHelper.hasLocationPermission(context)) {
                return@withContext AdhanApiService.Result.Error("إذن الموقع غير ممنوح — اختر مدينة يدويًا")
            }
            when (val loc = LocationHelper.getCurrentLocation(context)) {
                is LocationHelper.Result.Success -> {
                    PrayerTimePreferences.saveLocation(context, loc.latitude, loc.longitude, loc.label)
                    setCityName(loc.label)
                    AdhanApiService.fetchTimingsByCoordinates(loc.latitude, loc.longitude, method)
                }
                is LocationHelper.Result.Error ->
                    AdhanApiService.Result.Error(loc.message)
            }
        } else {
            if (manualCity.isBlank()) {
                return@withContext AdhanApiService.Result.Error("أدخل اسم المدينة")
            }
            AdhanApiService.fetchTimingsByCity(manualCity, method)
        }
    }

    when (result) {
        is AdhanApiService.Result.Success -> {
            setTimings(result.data)
            PrayerTimeScheduler.scheduleAll(context, result.data)
            setError(null)
        }
        is AdhanApiService.Result.Error -> {
            setError(result.message)
            setTimings(null)
        }
    }
    setLoading(false)
}
