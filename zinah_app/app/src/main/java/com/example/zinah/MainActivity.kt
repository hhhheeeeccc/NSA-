package com.example.zinah

import android.Manifest
import android.app.AlarmManager
import android.net.Uri
import android.os.PowerManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.res.Resources
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "تم تفعيل الإشعارات بنجاح", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "لا يمكن إرسال التذكيرات بدون إذن الإشعارات", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveInterval(interval: Long) {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putLong("interval", interval)
            apply()
        }
    }

    private fun getSavedInterval(): Long {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("interval", 15L)
    }

    private fun getCustomAdhkar(): MutableList<String> {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val count = sharedPref.getInt("customAdhkarCount", 0)
        val list = mutableListOf<String>()
        for (i in 0 until count) {
            val text = sharedPref.getString("customDhikr_$i", "") ?: ""
            if (text.isNotEmpty()) list.add(text)
        }
        return list
    }

    private fun saveCustomAdhkar(list: List<String>) {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("customAdhkarCount", list.size)
            for (i in list.indices) {
                putString("customDhikr_$i", list[i])
            }
            apply()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()
        checkExactAlarmPermission()
        requestSystemAlertWindowPermission()
        requestBatteryOptimization()
        // Initialize selected sound
        val prefs = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val savedSound = prefs.getInt("soundChoice", 0)
        // Start foreground service for guaranteed background execution
        DhikrForegroundService.start(this)
        // Update notification channel with saved sound
        updateNotificationSound(savedSound)

        setContent {
            ZinahTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZinahTheme.Cream
                ) {
                    var selectedTab by remember { mutableStateOf(0) }

                    // Read intent extra to allow prayer notification to deep-link into the prayer tab
                    LaunchedEffect(Unit) {
                        if (intent.getStringExtra("open_tab") == "prayer_times") {
                            selectedTab = 1
                        }
                    }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (selectedTab == 0) {
                                            Icon(
                                                imageVector = Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = ZinahTheme.GoldBright,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.Schedule,
                                                contentDescription = null,
                                                tint = ZinahTheme.GoldBright,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }
                                        Text(
                                            if (selectedTab == 0) "تطبيق زينة للأذكار" else "مواقيت الصلاة",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = ZinahTheme.EmeraldDeep,
                                    titleContentColor = Color.White
                                )
                            )
                        },
                        bottomBar = {
                            NavigationBar(
                                containerColor = Color.White,
                                contentColor = ZinahTheme.Emerald,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 0) Icons.Filled.Star
                                                          else Icons.Filled.Star,
                                            contentDescription = "الأذكار"
                                        )
                                    },
                                    label = { Text("الأذكار") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ZinahTheme.Emerald,
                                        selectedTextColor = ZinahTheme.Emerald,
                                        indicatorColor = ZinahTheme.EmeraldMist
                                    )
                                )
                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            imageVector = if (selectedTab == 1) Icons.Filled.Schedule
                                                          else Icons.Filled.Schedule,
                                            contentDescription = "مواقيت الصلاة"
                                        )
                                    },
                                    label = { Text("مواقيت الصلاة") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = ZinahTheme.Emerald,
                                        selectedTextColor = ZinahTheme.Emerald,
                                        indicatorColor = ZinahTheme.EmeraldMist
                                    )
                                )
                            }
                        }
                    ) { innerPadding ->
                        when (selectedTab) {
                            0 -> AdhkarContent(modifier = Modifier.padding(innerPadding))
                            1 -> Box(modifier = Modifier.padding(innerPadding)) {
                                PrayerTimesScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AdhkarContent(modifier: Modifier = Modifier) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                            // ===== Hero Card =====
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                                    colors = CardDefaults.cardColors(containerColor = ZinahTheme.EmeraldDeep),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("☪", fontSize = 28.sp, color = ZinahTheme.GoldBright)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                "أذكار وأدعية",
                                                color = ZinahTheme.GoldBright,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "${AdhkarData.allAdhkar.size} ذكر ودعاء",
                                            color = Color.White,
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "تذكيرات تلقائية تساعدك على ذكر الله",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            // ===== Settings Card =====
                            item {
                                var selectedInterval by remember { mutableStateOf(getSavedInterval()) }
                                var inputText by remember { mutableStateOf(getSavedInterval().toString()) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                                    .background(ZinahTheme.EmeraldMist),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Notifications,
                                                    contentDescription = null,
                                                    tint = ZinahTheme.Emerald,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text("إعدادات التذكير التلقائي",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = ZinahTheme.EmeraldDeep,
                                                fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text("اختيارات سريعة (دقائق):",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZinahTheme.InkMute,
                                            modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Minute presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1L, 2L, 5L, 10L, 15L, 20L, 30L, 45L).forEach { mins ->
                                                val isSelected = selectedInterval == mins
                                                Button(
                                                    onClick = {
                                                        selectedInterval = mins
                                                        inputText = mins.toString()
                                                        saveInterval(mins)
                                                        scheduleExactDhikrAlarm(mins)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) ZinahTheme.Emerald
                                                                          else ZinahTheme.EmeraldMist,
                                                        contentColor = if (isSelected) Color.White else ZinahTheme.Emerald
                                                    )
                                                ) {
                                                    Text("${mins}د", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Text("ساعات:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZinahTheme.InkMute,
                                            modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Hour presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf(1L, 2L, 3L, 6L, 12L).forEach { hours ->
                                                val mins = hours * 60
                                                val isSelected = selectedInterval == mins
                                                Button(
                                                    onClick = {
                                                        selectedInterval = mins
                                                        inputText = mins.toString()
                                                        saveInterval(mins)
                                                        scheduleExactDhikrAlarm(mins)
                                                    },
                                                    modifier = Modifier.weight(1f),
                                                    contentPadding = PaddingValues(vertical = 8.dp),
                                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isSelected) ZinahTheme.Emerald
                                                                          else ZinahTheme.EmeraldMist,
                                                        contentColor = if (isSelected) Color.White else ZinahTheme.Emerald
                                                    )
                                                ) {
                                                    Text("${hours}س", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text("أو أدخل فاصلًا مخصصًا:",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = ZinahTheme.InkMute,
                                            modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(8.dp))

                                        OutlinedTextField(
                                            value = inputText,
                                            onValueChange = { inputText = it },
                                            label = { Text("أدخل الدقائق (أقل شيء 1 دقيقة)") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(0.8f)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(onClick = {
                                            val minutes = inputText.toLongOrNull()
                                            if (minutes != null) {
                                                if (minutes >= 1) {
                                                    selectedInterval = minutes
                                                    saveInterval(minutes)
                                                    scheduleExactDhikrAlarm(minutes)
                                                } else {
                                                    Toast.makeText(this@MainActivity, "أقل مدة مسموحة هي 1 دقيقة", Toast.LENGTH_LONG).show()
                                                }
                                            } else {
                                                Toast.makeText(this@MainActivity, "الرجاء إدخال رقم صحيح", Toast.LENGTH_SHORT).show()
                                            }
                                        }) {
                                            Text("تطبيق التعديل")
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "الفاصل الحالي: كل $selectedInterval دقيقة",
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // ===== Sound Selection Section =====
                            item {
                                var mediaPlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "صوت التذكير",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "تم تفعيل صوت 'صلي على محمد' بصوت بشري حقيقي",
                                            fontSize = 14.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        Button(
                                            onClick = {
                                                try {
                                                    mediaPlayer?.stop()
                                                    mediaPlayer?.release()
                                                    mediaPlayer = android.media.MediaPlayer.create(this@MainActivity, R.raw.sali_ala_mohammad).also { mp ->
                                                        mp.setVolume(1.0f, 1.0f)
                                                        mp.start()
                                                        mp.setOnCompletionListener { mp.release() }
                                                    }
                                                } catch (e: Exception) {
                                                    Toast.makeText(this@MainActivity, "خطأ في تشغيل الصوت", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD4AF37))
                                        ) {
                                            Text("🔊 تجربة الصوت البشري", fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            // ===== Stop Button =====
                            item {
                                Button(
                                    onClick = {
                                        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                        val intent = Intent(this@MainActivity, DhikrAlarmReceiver::class.java)
                                        val pendingIntent = PendingIntent.getBroadcast(
                                            this@MainActivity,
                                            0,
                                            intent,
                                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                        )
                                        alarmManager.cancel(pendingIntent)
                                        Toast.makeText(this@MainActivity, "تم إيقاف التذكيرات", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) {
                                    Text("إيقاف التذكيرات", fontWeight = FontWeight.Bold)
                                }
                            }

                            // ===== CREATE CUSTOM DHIKR/DOA SECTION =====
                            item {
                                var newDhikrText by remember { mutableStateOf("") }
                                var customList by remember { mutableStateOf(getCustomAdhkar()) }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            "أضف ذكر أو دعاء خاص بك",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 18.sp,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            "اكتب الذكر أو الدعاء اللي تبغاه وراح يوصلك كتذكير مع باقي الأذكار",
                                            fontSize = 13.sp,
                                            color = Color.Gray,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))

                                        OutlinedTextField(
                                            value = newDhikrText,
                                            onValueChange = { newDhikrText = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = { Text("اكتب الذكر أو الدعاء هنا...") },
                                            maxLines = 4,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = Color(0xFF2E7D32)
                                            )
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))

                                        Button(
                                            onClick = {
                                                if (newDhikrText.trim().isNotEmpty()) {
                                                    customList = (customList + newDhikrText.trim()).toMutableList()
                                                    saveCustomAdhkar(customList)
                                                    newDhikrText = ""
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "تم إضافة الذكر/الدعاء بنجاح",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "اكتب ذكر أو دعاء أولاً",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("إضافة الذكر/الدعاء", fontWeight = FontWeight.Bold)
                                        }

                                        // Show saved custom items
                                        if (customList.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                "أذكاري المخصصة (${customList.size}):",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = Color.Gray
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            customList.forEachIndexed { index, text ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = text,
                                                            modifier = Modifier.weight(1f),
                                                            fontSize = 15.sp,
                                                            textAlign = TextAlign.Center
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                customList = customList.toMutableList().apply { removeAt(index) }
                                                                saveCustomAdhkar(customList)
                                                                Toast.makeText(
                                                                    this@MainActivity,
                                                                    "تم حذف الذكر",
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        ) {
                                                            Text("✕", color = Color(0xFFD32F2F), fontSize = 14.sp)
                                                        }
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // ===== Adhkar count badge =====
                            item {
                                val customCount = getCustomAdhkar().size
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Text(
                                            "أذكار وأدعية: ${AdhkarData.allAdhkar.size}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        if (customCount > 0) {
                                            Text(
                                                "مخصصة: $customCount",
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF1565C0)
                                            )
                                        }
                                    }
                                }
                            }

                            // ===== Adhkar list header =====
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(4.dp)
                                            .height(24.dp)
                                            .background(ZinahTheme.Gold)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "الأذكار والأدعية",
                                        style = MaterialTheme.typography.headlineMedium,
                                        color = ZinahTheme.EmeraldDeep
                                    )
                                }
                            }

                            // ===== Custom adhkar list =====
                            val customItems = getCustomAdhkar()
                            if (customItems.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(20.dp)
                                                .background(ZinahTheme.Sky)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            "أذكاري المخصصة:",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = ZinahTheme.Sky,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                items(customItems) { text ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(40.dp)
                                                    .background(ZinahTheme.Sky)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = text,
                                                modifier = Modifier.weight(1f),
                                                style = MaterialTheme.typography.bodyLarge,
                                                textAlign = TextAlign.Right
                                            )
                                        }
                                    }
                                }
                            }

                            // ===== Default Adhkar list =====
                            items(AdhkarData.allAdhkar) { dhikr ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(40.dp)
                                                .background(ZinahTheme.Gold)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = dhikr,
                                            modifier = Modifier.weight(1f),
                                            style = MaterialTheme.typography.bodyLarge,
                                            textAlign = TextAlign.Right
                                        )
                                    }
                                }
                            }
                        }
    }

    private fun scheduleExactDhikrAlarm(intervalMinutes: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, DhikrAlarmReceiver::class.java)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val triggerTime = System.currentTimeMillis() + (intervalMinutes * 60 * 1000)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
            Toast.makeText(this, "تم ضبط التذكير كل $intervalMinutes دقيقة", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(this, "لا يوجد إذن لضبط التنبيهات الدقيقة", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                startActivity(intent)
            }
        }
    }

    private fun requestSystemAlertWindowPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun updateNotificationSound(soundIndex: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "zinah_dhikr_channel_exact"
            
            val soundResId = R.raw.sali_ala_mohammad
            
            val soundUri = "android.resource://$packageName/$soundResId"
            
            val channel = NotificationChannel(
                channelId,
                "إشعارات الأذكار المباشرة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "قناة إشعارات تطبيق زينة"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 200)
                lockscreenVisibility = 1
                setBypassDnd(true)
                setShowBadge(true)
                setSound(
                    android.net.Uri.parse(soundUri),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
