package com.example.zinah

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerTimesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }

    // Refresh the countdown every 30 seconds
    LaunchedEffect(Unit) {
        while (true) {
            try {
                kotlinx.coroutines.delay(30_000L)
                nowTick = System.currentTimeMillis()
            } catch (e: Exception) {
                break
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            scope.launch {
                refreshTimings(
                    context, useGps, manualCity, methodIndex,
                    setTimings = { timings = it },
                    setLoading = { isLoading = it },
                    setError = { errorMessage = it },
                    setCityName = { cityName = it }
                )
            }
        }
    }

    // Auto-load on first launch ONLY if we have a saved manual city or already-granted GPS permission
    LaunchedEffect(Unit) {
        if (!useGps && manualCity.isNotBlank()) {
            refreshTimings(
                context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it }
            )
        } else if (useGps && LocationHelper.hasLocationPermission(context)) {
            refreshTimings(
                context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it }
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF1F8E9)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ===== Master toggle + countdown =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "مواقيت الصلاة",
                                color = Color(0xFFFFD700),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Switch(
                                checked = featureEnabled,
                                onCheckedChange = { newValue ->
                                    featureEnabled = newValue
                                    PrayerTimePreferences.setFeatureEnabled(context, newValue)
                                    if (newValue) {
                                        scope.launch {
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                    } else {
                                        PrayerTimeScheduler.cancelAll(context)
                                        timings = null
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color(0xFFFFD700),
                                    checkedTrackColor = Color(0xFFD4AF37),
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = Color.Gray
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))

                        if (featureEnabled) {
                            val now = Calendar.getInstance().apply { timeInMillis = nowTick }
                            val next = timings?.nextPrayer(now)
                            if (next != null) {
                                val (prayer, cal) = next
                                Text("الصلاة القادمة", color = Color(0xFFFFD700), fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    prayer.nameAr,
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    safeFormatTime(cal),
                                    color = Color(0xFFFFD700),
                                    fontSize = 20.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "بعد ${safeFormatRemaining(now, cal)}",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            } else if (isLoading) {
                                Text("جارٍ التحميل...", color = Color.White, fontSize = 16.sp)
                            } else {
                                Text(
                                    "اضغط زر التحديث لجلب المواقيت",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(cityName, color = Color.White, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                "فعّل المفتاح لبدء استقبال الأذان",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ===== Location card =====
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null,
                                tint = Color(0xFF2E7D32), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(cityName, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                if (useGps) {
                                    val perms = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        scope.launch {
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                    } else {
                                        locationPermissionLauncher.launch(perms)
                                    }
                                } else {
                                    scope.launch {
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                }
                            }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    useGps = true
                                    PrayerTimePreferences.setUseManualCity(context, false)
                                    val perms = arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                    if (LocationHelper.hasLocationPermission(context)) {
                                        scope.launch {
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                    } else {
                                        locationPermissionLauncher.launch(perms)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (useGps) Color(0xFF2E7D32) else Color(0xFFE8F5E9),
                                    contentColor = if (useGps) Color.White else Color(0xFF2E7D32)
                                )
                            ) { Text("GPS", fontSize = 12.sp) }
                            Button(
                                onClick = {
                                    useGps = false
                                    PrayerTimePreferences.setUseManualCity(context, true)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!useGps) Color(0xFF2E7D32) else Color(0xFFE8F5E9),
                                    contentColor = if (!useGps) Color.White else Color(0xFF2E7D32)
                                )
                            ) { Text("يدوي", fontSize = 12.sp) }
                        }
                        if (!useGps) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = manualCity,
                                onValueChange = { manualCity = it },
                                label = { Text("المدينة,الدولة") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                trailingIcon = {
                                    TextButton(onClick = {
                                        PrayerTimePreferences.setManualCity(context, manualCity)
                                        scope.launch {
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                    }) { Text("حفظ") }
                                }
                            )
                        }
                    }
                }
            }

            // ===== Prayer list =====
            val t = timings
            if (t != null && featureEnabled) {
                item {
                    Text(
                        "مواقيت اليوم",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }
                val now = Calendar.getInstance().apply { timeInMillis = nowTick }
                val nextPrayer = try { t.nextPrayer(now).first } catch (e: Exception) { null }
                items(PrayerType.entries.toList()) { prayer ->
                    val cal = try { t.timeFor(prayer) } catch (e: Exception) { null }
                    val isNext = prayer == nextPrayer
                    val enabled = PrayerTimePreferences.isPrayerEnabled(context, prayer)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isNext) Color(0xFF1B5E20) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(if (isNext) 4.dp else 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                prayer.nameAr,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNext) Color.White else Color(0xFF1B1B1B),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (cal != null) safeFormatTime(cal) else "--:--",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isNext) Color(0xFFFFD700) else Color(0xFF1B5E20)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = enabled,
                                onCheckedChange = { newValue ->
                                    PrayerTimePreferences.setPrayerEnabled(context, prayer, newValue)
                                    if (featureEnabled && newValue && cal != null) {
                                        try {
                                            if (cal.timeInMillis > System.currentTimeMillis()) {
                                                PrayerTimeScheduler.scheduleOne(
                                                    context, prayer, cal.timeInMillis
                                                )
                                            }
                                        } catch (e: Exception) {
                                            Log.e("PrayerTimes", "scheduleOne failed", e)
                                        }
                                    } else if (!newValue) {
                                        try { cancelPrayerAlarm(context, prayer) }
                                        catch (e: Exception) {}
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF2E7D32),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = Color(0xFFE0E0E0)
                                )
                            )
                        }
                    }
                }
            }

            // ===== Advanced settings =====
            if (featureEnabled) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("إعدادات متقدمة", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20))
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("طريقة الحساب", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { showMethodDropdown = !showMethodDropdown },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(PrayerTimePreferences.CALCULATION_METHODS[methodIndex].second,
                                        modifier = Modifier.weight(1f), fontSize = 12.sp)
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
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
                        refreshTimings(
                            context, useGps, manualCity, methodIndex,
                            setTimings = { timings = it },
                            setLoading = { isLoading = it },
                            setError = { errorMessage = it },
                            setCityName = { cityName = it }
                        )
                    }
                                            }
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("صوت الأذان", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("مكة" to 0, "المدينة" to 1, "قصير" to 2).forEach { (label, idx) ->
                                    Button(
                                        onClick = {
                                            adhanSoundIndex = idx
                                            PrayerTimePreferences.setAdhanSoundIndex(context, idx)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (adhanSoundIndex == idx) Color(0xFF2E7D32)
                                             else Color(0xFFE8F5E9),
                                            contentColor = if (adhanSoundIndex == idx) Color.White
                                             else Color(0xFF2E7D32)
                                        ),
                                        contentPadding = PaddingValues(vertical = 6.dp)
                                    ) { Text(label, fontSize = 11.sp) }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("شاشة الأذان الكاملة",
                                    modifier = Modifier.weight(1f), fontSize = 14.sp)
                                Switch(
                                    checked = fullScreen,
                                    onCheckedChange = {
                                        fullScreen = it
                                        PrayerTimePreferences.setFullScreenAdhan(context, it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2E7D32)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // ===== Error message + action button =====
            if (errorMessage != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                errorMessage!!,
                                color = Color(0xFFB71C1C),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            // Button to open location settings
                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS
                                        ).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback: open app settings
                                        try {
                                            val intent = android.content.Intent(
                                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                                            ).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e2: Exception) {}
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFFB71C1C)
                                )
                            ) {
                                Icon(Icons.Filled.LocationOn, contentDescription = null,
                                    modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فتح إعدادات الموقع", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ===== Helper functions — all wrapped in try/catch =====

private fun safeFormatTime(cal: java.util.Calendar): String {
    return try {
        val fmt = SimpleDateFormat("hh:mm a", Locale.US)
        fmt.format(cal.time)
    } catch (e: Exception) {
        "--:--"
    }
}

private fun safeFormatRemaining(now: java.util.Calendar, target: java.util.Calendar): String {
    return try {
        val diff = target.timeInMillis - now.timeInMillis
        if (diff <= 0) return "الآن"
        val hours = TimeUnit.MILLISECONDS.toHours(diff)
        val mins = TimeUnit.MILLISECONDS.toMinutes(diff) % 60
        when {
            hours > 0 && mins > 0 -> "$hours ساعة و $mins دقيقة"
            hours > 0 -> "$hours ساعة"
            mins > 0 -> "$mins دقيقة"
            else -> "الآن"
        }
    } catch (e: Exception) {
        "قريبًا"
    }
}

private fun cancelPrayerAlarm(context: Context, prayer: PrayerType) {
    val am = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
    val i = android.content.Intent(context, PrayerAlarmReceiver::class.java).apply {
        action = "com.example.zinah.PRAYER_ALARM"
    }
    val pi = android.app.PendingIntent.getBroadcast(
        context,
        PrayerType.alarmRequestCode(prayer),
        i,
        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
    )
    am.cancel(pi)
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

    var newCityName: String? = null

    val result = try {
        withContext(Dispatchers.IO) {
            if (useGps) {
                if (!LocationHelper.hasLocationPermission(context)) {
                    return@withContext AdhanApiService.Result.Error(
                        "إذن الموقع غير ممنوح - اضغط GPS وامنح الإذن"
                    )
                }
                when (val loc = LocationHelper.getCurrentLocation(context)) {
                    is LocationHelper.Result.Success -> {
                        PrayerTimePreferences.saveLocation(context, loc.latitude, loc.longitude, loc.label)
                        newCityName = loc.label
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
    } catch (e: Throwable) {
        Log.e("PrayerTimes", "refreshTimings crashed", e)
        AdhanApiService.Result.Error("خطأ: ${e.message ?: "غير معروف"}")
    }

    // Back on main thread — safe to update state
    newCityName?.let { setCityName(it) }

    when (result) {
        is AdhanApiService.Result.Success -> {
            setTimings(result.data)
            try {
                PrayerTimeScheduler.scheduleAll(context, result.data)
            } catch (e: Exception) {
                Log.e("PrayerTimes", "scheduleAll failed", e)
            }
            setError(null)
        }
        is AdhanApiService.Result.Error -> {
            setError(result.message)
            setTimings(null)
        }
    }
    setLoading(false)
}
