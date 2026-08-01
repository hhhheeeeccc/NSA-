package com.example.zinah

import android.Manifest
import android.content.Context
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
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

/**
 * Prayer Times screen — simplified, stable version.
 *
 * Design goals:
 *  - Beautiful but WITHOUT any composable that can crash on older Android:
 *    no Modifier.blur(), no custom Canvas patterns, no infinite animations.
 *  - Uses solid colors + simple gradients only.
 *  - All icons come from material-icons-core (guaranteed available).
 */
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

    // Tick every 30 seconds (less aggressive than 1s, still fresh enough)
    var nowTick by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(30_000L)
            nowTick = System.currentTimeMillis()
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            scope.launch { refreshTimings(context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it })
            }
        }
    }

    LaunchedEffect(Unit) {
        // Do NOT auto-trigger GPS on first launch (would crash if permission not granted)
        if (!useGps) {
            refreshTimings(context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it })
        } else if (LocationHelper.hasLocationPermission(context)) {
            refreshTimings(context, useGps, manualCity, methodIndex,
                setTimings = { timings = it },
                setLoading = { isLoading = it },
                setError = { errorMessage = it },
                setCityName = { cityName = it })
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = ZinahTheme.Cream
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== Hero countdown card =====
            item {
                HeroCountdownCard(
                    featureEnabled = featureEnabled,
                    onToggleFeature = { newValue ->
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
                    timings = timings,
                    nowTick = nowTick,
                    cityName = cityName,
                    hijriDate = timings?.hijriDate ?: ""
                )
            }

            // ===== Location & refresh =====
            item {
                LocationCard(
                    cityName = cityName,
                    useGps = useGps,
                    manualCity = manualCity,
                    onUseGpsChange = { newValue ->
                        useGps = newValue
                        PrayerTimePreferences.setUseManualCity(context, !newValue)
                        if (newValue) {
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
                        }
                    },
                    onManualCityChange = { manualCity = it },
                    onSaveManualCity = {
                        PrayerTimePreferences.setManualCity(context, manualCity)
                        scope.launch {
                            refreshTimings(context, useGps, manualCity, methodIndex,
                                setTimings = { timings = it },
                                setLoading = { isLoading = it },
                                setError = { errorMessage = it },
                                setCityName = { cityName = it })
                        }
                    },
                    onRefresh = {
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
                    }
                )
            }

            // ===== Section header =====
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "مواقيت اليوم",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = ZinahTheme.EmeraldDeep
                    )
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = ZinahTheme.Emerald
                        )
                    }
                }
            }

            // ===== 5 prayer cards =====
            val t = timings
            if (t != null) {
                val now = Calendar.getInstance().apply { timeInMillis = nowTick }
                val next = t.nextPrayer(now).first
                items(PrayerType.entries) { prayer ->
                    PrayerCard(
                        prayer = prayer,
                        time = formatTime(t.timeFor(prayer)),
                        isNext = prayer == next,
                        isEnabled = PrayerTimePreferences.isPrayerEnabled(context, prayer),
                        onToggleEnabled = { newValue ->
                            PrayerTimePreferences.setPrayerEnabled(context, prayer, newValue)
                            if (featureEnabled && newValue) {
                                val cal = t.timeFor(prayer)
                                if (cal.timeInMillis > System.currentTimeMillis()) {
                                    PrayerTimeScheduler.scheduleOne(
                                        context, prayer, cal.timeInMillis
                                    )
                                }
                            } else if (!newValue) {
                                cancelPrayerAlarm(context, prayer)
                            }
                        }
                    )
                }
            } else if (!isLoading) {
                item {
                    EmptyStateCard(onRetry = {
                        scope.launch {
                            refreshTimings(context, useGps, manualCity, methodIndex,
                                setTimings = { timings = it },
                                setLoading = { isLoading = it },
                                setError = { errorMessage = it },
                                setCityName = { cityName = it })
                        }
                    })
                }
            }

            // ===== Advanced settings =====
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "إعدادات متقدمة",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = ZinahTheme.EmeraldDeep
                )
            }

            item {
                AdvancedSettingsCard(
                    methodIndex = methodIndex,
                    adhanSoundIndex = adhanSoundIndex,
                    fullScreen = fullScreen,
                    showMethodDropdown = showMethodDropdown,
                    onShowMethodDropdown = { showMethodDropdown = it },
                    onMethodChange = { idx ->
                        methodIndex = idx
                        PrayerTimePreferences.setCalculationMethod(
                            context, PrayerTimePreferences.CALCULATION_METHODS[idx].first
                        )
                        scope.launch {
                            refreshTimings(context, useGps, manualCity, methodIndex,
                                setTimings = { timings = it },
                                setLoading = { isLoading = it },
                                setError = { errorMessage = it },
                                setCityName = { cityName = it })
                        }
                    },
                    onAdhanSoundChange = { idx ->
                        adhanSoundIndex = idx
                        PrayerTimePreferences.setAdhanSoundIndex(context, idx)
                    },
                    onFullScreenChange = {
                        fullScreen = it
                        PrayerTimePreferences.setFullScreenAdhan(context, it)
                    }
                )
            }

            // ===== Error =====
            if (errorMessage != null) {
                item {
                    ErrorCard(message = errorMessage ?: "")
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

// ============ HERO COUNTDOWN CARD ============

@Composable
private fun HeroCountdownCard(
    featureEnabled: Boolean,
    onToggleFeature: (Boolean) -> Unit,
    timings: PrayerTimings?,
    nowTick: Long,
    cityName: String,
    hijriDate: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = ZinahTheme.EmeraldDeep),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top row: master toggle + gold accent
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "مواقيت الصلاة",
                    color = ZinahTheme.GoldBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = featureEnabled,
                    onCheckedChange = onToggleFeature,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ZinahTheme.GoldBright,
                        checkedTrackColor = ZinahTheme.Gold.copy(alpha = 0.4f),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (!featureEnabled) {
                Text(
                    "☪",
                    fontSize = 48.sp,
                    color = ZinahTheme.GoldBright
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "ميزة مواقيت الصلاة معطّلة",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "فعّل المفتاح في الأعلى لبدء استقبال الأذان",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                val now = Calendar.getInstance().apply { timeInMillis = nowTick }
                val next = timings?.nextPrayer(now)

                Text(
                    "الصلاة القادمة",
                    color = ZinahTheme.GoldBright,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (next != null) {
                    val (prayer, cal) = next
                    Text(
                        prayer.nameAr,
                        color = Color.White,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        formatTime(cal),
                        color = ZinahTheme.GoldBright,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Countdown badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(ZinahTheme.Gold.copy(alpha = 0.18f))
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        Text(
                            "بعد ${formatRemaining(now, cal)}",
                            color = ZinahTheme.GoldBright,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    Text(
                        "جارٍ تحميل المواقيت...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // City + date footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = ZinahTheme.GoldBright,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        cityName,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                    if (hijriDate.isNotBlank()) {
                        Text(
                            "  •  $hijriDate",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

// ============ LOCATION CARD ============

@Composable
private fun LocationCard(
    cityName: String,
    useGps: Boolean,
    manualCity: String,
    onUseGpsChange: (Boolean) -> Unit,
    onManualCityChange: (String) -> Unit,
    onSaveManualCity: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ZinahTheme.EmeraldMist),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = ZinahTheme.Emerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "الموقع الحالي",
                        fontSize = 12.sp,
                        color = ZinahTheme.InkMute
                    )
                    Text(
                        cityName,
                        fontSize = 16.sp,
                        color = ZinahTheme.EmeraldDeep,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ZinahTheme.EmeraldMist)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "تحديث",
                        tint = ZinahTheme.Emerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Source toggle: GPS vs manual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onUseGpsChange(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (useGps) ZinahTheme.Emerald else ZinahTheme.EmeraldMist,
                        contentColor = if (useGps) Color.White else ZinahTheme.Emerald
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("GPS تلقائي", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onUseGpsChange(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!useGps) ZinahTheme.Emerald else ZinahTheme.EmeraldMist,
                        contentColor = if (!useGps) Color.White else ZinahTheme.Emerald
                    ),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("اختيار يدوي", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (!useGps) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = manualCity,
                    onValueChange = onManualCityChange,
                    label = { Text("المدينة,الدولة") },
                    placeholder = { Text("Makkah,Saudi Arabia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        TextButton(onClick = onSaveManualCity) {
                            Text("حفظ", color = ZinahTheme.Emerald, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZinahTheme.Emerald,
                        focusedLabelColor = ZinahTheme.Emerald,
                        cursorColor = ZinahTheme.Emerald
                    )
                )
            }
        }
    }
}

// ============ PRAYER CARD ============

@Composable
private fun PrayerCard(
    prayer: PrayerType,
    time: String,
    isNext: Boolean,
    isEnabled: Boolean,
    onToggleEnabled: (Boolean) -> Unit
) {
    val accentColor = when (prayer) {
        PrayerType.FAJR -> ZinahTheme.FajrColor
        PrayerType.DHUHR -> ZinahTheme.DhuhrColor
        PrayerType.ASR -> ZinahTheme.AsrColor
        PrayerType.MAGHRIB -> ZinahTheme.MaghribColor
        PrayerType.ISHA -> ZinahTheme.IshaColor
    }
    val icon = when (prayer) {
        PrayerType.FAJR -> Icons.Filled.Star
        PrayerType.DHUHR -> Icons.Filled.WbSunny
        PrayerType.ASR -> Icons.Filled.WbSunny
        PrayerType.MAGHRIB -> Icons.Filled.Star
        PrayerType.ISHA -> Icons.Filled.Bedtime
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNext) ZinahTheme.EmeraldDeep else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isNext) 6.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isNext) ZinahTheme.Gold.copy(alpha = 0.2f)
                        else accentColor.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = prayer.nameAr,
                    tint = if (isNext) ZinahTheme.GoldBright else accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    prayer.nameAr,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isNext) Color.White else ZinahTheme.Ink
                )
                if (isNext) {
                    Text(
                        "التالية",
                        fontSize = 11.sp,
                        color = ZinahTheme.GoldBright,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (!isEnabled) {
                    Text(
                        "معطّل",
                        fontSize = 11.sp,
                        color = ZinahTheme.InkMute
                    )
                }
            }

            Text(
                time,
                fontSize = 18.sp,
                color = if (isNext) ZinahTheme.GoldBright else ZinahTheme.EmeraldDeep,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(12.dp))

            Switch(
                checked = isEnabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = if (isNext) ZinahTheme.GoldBright else Color.White,
                    checkedTrackColor = if (isNext) ZinahTheme.Gold.copy(alpha = 0.5f)
                                        else ZinahTheme.Emerald,
                    uncheckedThumbColor = ZinahTheme.InkMute,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

// ============ ADVANCED SETTINGS ============

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvancedSettingsCard(
    methodIndex: Int,
    adhanSoundIndex: Int,
    fullScreen: Boolean,
    showMethodDropdown: Boolean,
    onShowMethodDropdown: (Boolean) -> Unit,
    onMethodChange: (Int) -> Unit,
    onAdhanSoundChange: (Int) -> Unit,
    onFullScreenChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Method
            Text("طريقة الحساب",
                fontSize = 16.sp,
                color = ZinahTheme.EmeraldDeep,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { onShowMethodDropdown(!showMethodDropdown) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ZinahTheme.Cream
                    )
                ) {
                    Text(
                        PrayerTimePreferences.CALCULATION_METHODS[methodIndex].second,
                        modifier = Modifier.weight(1f),
                        color = ZinahTheme.Ink
                    )
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(
                    expanded = showMethodDropdown,
                    onDismissRequest = { onShowMethodDropdown(false) }
                ) {
                    PrayerTimePreferences.CALCULATION_METHODS.forEachIndexed { idx, pair ->
                        DropdownMenuItem(
                            text = { Text(pair.second) },
                            onClick = {
                                onMethodChange(idx)
                                onShowMethodDropdown(false)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Adhan sound
            Text("صوت الأذان",
                fontSize = 16.sp,
                color = ZinahTheme.EmeraldDeep,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("مكة" to 0, "المدينة" to 1, "نغمة قصيرة" to 2).forEach { (label, idx) ->
                    Button(
                        onClick = { onAdhanSoundChange(idx) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (adhanSoundIndex == idx) ZinahTheme.Emerald
                                             else ZinahTheme.EmeraldMist,
                            contentColor = if (adhanSoundIndex == idx) Color.White
                                           else ZinahTheme.Emerald
                        ),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Full-screen toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(ZinahTheme.Sand),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = ZinahTheme.GoldDeep,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("شاشة الأذان الكاملة",
                        fontSize = 14.sp,
                        color = ZinahTheme.Ink,
                        fontWeight = FontWeight.Medium)
                    Text("نافذة منبثقة عند دخول الوقت",
                        fontSize = 11.sp,
                        color = ZinahTheme.InkMute)
                }
                Switch(
                    checked = fullScreen,
                    onCheckedChange = onFullScreenChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = ZinahTheme.Emerald,
                        uncheckedThumbColor = ZinahTheme.InkMute,
                        uncheckedTrackColor = Color(0xFFE0E0E0)
                    )
                )
            }
        }
    }
}

// ============ EMPTY STATE ============

@Composable
private fun EmptyStateCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("☪", fontSize = 48.sp, color = ZinahTheme.Gold)
            Spacer(modifier = Modifier.height(16.dp))
            Text("لم يتم تحميل المواقيت بعد",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = ZinahTheme.Ink)
            Spacer(modifier = Modifier.height(4.dp))
            Text("اضغط لتحديث البيانات",
                fontSize = 12.sp,
                color = ZinahTheme.InkMute)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = ZinahTheme.Emerald)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تحديث المواقيت")
            }
        }
    }
}

// ============ ERROR ============

@Composable
private fun ErrorCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null,
                tint = ZinahTheme.Rose, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(message, color = Color(0xFFB71C1C), fontSize = 13.sp)
        }
    }
}

// ============ helpers ============

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

private fun cancelPrayerAlarm(context: Context, prayer: PrayerType) {
    val am = context.getSystemService(android.content.Context.ALARM_SERVICE)
            as android.app.AlarmManager
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

    val result = try {
        withContext(Dispatchers.IO) {
            if (useGps) {
                if (!LocationHelper.hasLocationPermission(context)) {
                    return@withContext AdhanApiService.Result.Error(
                        "إذن الموقع غير ممنوح — اضغط زر التحديث وامنح الإذن، أو اختر مدينة يدويًا"
                    )
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
                    return@withContext AdhanApiService.Result.Error("أدخل اسم المدينة بصيغة: المدينة,الدولة")
                }
                AdhanApiService.fetchTimingsByCity(manualCity, method)
            }
        }
    } catch (e: Throwable) {
        Log.e("PrayerTimesScreen", "refreshTimings crashed", e)
        AdhanApiService.Result.Error("حدث خطأ غير متوقع: ${e.message ?: "سبب غير معروف"}")
    }

    when (result) {
        is AdhanApiService.Result.Success -> {
            setTimings(result.data)
            try {
                PrayerTimeScheduler.scheduleAll(context, result.data)
            } catch (e: Exception) {
                Log.e("PrayerTimesScreen", "scheduleAll failed", e)
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
