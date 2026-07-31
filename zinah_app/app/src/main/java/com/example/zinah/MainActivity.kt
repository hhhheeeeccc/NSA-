package com.example.zinah

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit

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

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        askNotificationPermission()

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2E7D32),
                    secondary = Color(0xFFD4AF37),
                    background = Color(0xFFF1F8E9)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        TopAppBar(
                            title = {
                                Text(
                                    "تطبيق زينة للأذكار",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = Color.White
                            )
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Settings Card
                            item {
                                var selectedInterval by remember { mutableStateOf(getSavedInterval()) }
                                var selectedUnit by remember { mutableStateOf(getSavedUnit()) }
                                var customInput by remember { mutableStateOf("") }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("إعدادات التذكير التلقائي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Quick presets
                                        Text("اختيارات سريعة:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Minute presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            listOf(5L, 10L, 15L, 20L, 30L, 45L).forEach { mins ->
                                                Button(
                                                    onClick = {
                                                        selectedInterval = mins
                                                        selectedUnit = TimeUnit.MINUTES
                                                        customInput = ""
                                                        saveInterval(mins, TimeUnit.MINUTES)
                                                        scheduleDhikr(mins, TimeUnit.MINUTES)
                                                    },
                                                    modifier = Modifier.width(55.dp),
                                                    contentPadding = PaddingValues(4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43A047))
                                                ) {
                                                    Text("${mins}د", fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Hour presets
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            listOf(1L, 2L, 3L, 6L, 12L).forEach { hours ->
                                                Button(
                                                    onClick = {
                                                        selectedInterval = hours
                                                        selectedUnit = TimeUnit.HOURS
                                                        customInput = ""
                                                        saveInterval(hours, TimeUnit.HOURS)
                                                        scheduleDhikr(hours, TimeUnit.HOURS)
                                                    },
                                                    modifier = Modifier.width(55.dp),
                                                    contentPadding = PaddingValues(4.dp)
                                                ) {
                                                    Text("${hours}س", fontSize = 11.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Custom interval input
                                        Text("أو أدخل فاصل مخصص:", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.Gray)
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = customInput,
                                                onValueChange = { customInput = it },
                                                modifier = Modifier.width(100.dp),
                                                placeholder = { Text("عدد") },
                                                singleLine = true,
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFF2E7D32)
                                                )
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            // Unit selector
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = selectedUnit == TimeUnit.MINUTES,
                                                        onClick = { selectedUnit = TimeUnit.MINUTES }
                                                    )
                                                    Text("دقيقة")
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = selectedUnit == TimeUnit.HOURS,
                                                        onClick = { selectedUnit = TimeUnit.HOURS }
                                                    )
                                                    Text("ساعة")
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    val value = customInput.toLongOrNull()
                                                    if (value != null && value > 0) {
                                                        selectedInterval = value
                                                        saveInterval(value, selectedUnit)
                                                        scheduleDhikr(value, selectedUnit)
                                                    } else {
                                                        Toast.makeText(
                                                            this@MainActivity,
                                                            "أدخل قيمة صحيحة",
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            ) {
                                                Text("تطبيق")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            "الفاصل الحالي: كل $selectedInterval ${if (selectedUnit == TimeUnit.MINUTES) "دقيقة" else "ساعة"}",
                                            color = Color(0xFF2E7D32),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Stop button
                            item {
                                Button(
                                    onClick = {
                                        WorkManager.getInstance(this@MainActivity).cancelUniqueWork("ZinahPeriodicDhikr")
                                        Toast.makeText(this@MainActivity, "تم إيقاف التذكيرات", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                                ) {
                                    Text("إيقاف التذكيرات", fontWeight = FontWeight.Bold)
                                }
                            }

                            // Adhkar count badge
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Text(
                                            "عدد الأذكار: ${AdhkarData.adhkarList.size}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            "عدد الأدعية: ${AdhkarData.duaaList.size}",
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFD4AF37)
                                        )
                                    }
                                }
                            }

                            // Adhkar section header
                            item {
                                Text(
                                    "الأذكار",
                                    modifier = Modifier.padding(top = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFF2E7D32)
                                )
                            }

                            // Adhkar list
                            items(AdhkarData.adhkarList) { dhikr ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Text(
                                        text = dhikr,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }

                            // Duaa section header
                            item {
                                Text(
                                    "الأدعية",
                                    modifier = Modifier.padding(top = 16.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = Color(0xFFD4AF37)
                                )
                            }

                            // Duaa list
                            items(AdhkarData.duaaList) { duaa ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFAF0)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Text(
                                        text = duaa,
                                        modifier = Modifier.padding(12.dp),
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun saveInterval(interval: Long, timeUnit: TimeUnit) {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putLong("interval", interval)
            putBoolean("isMinutes", timeUnit == TimeUnit.MINUTES)
            apply()
        }
    }

    private fun getSavedInterval(): Long {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("interval", 15L)
    }

    private fun getSavedUnit(): TimeUnit {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        val isMinutes = sharedPref.getBoolean("isMinutes", true)
        return if (isMinutes) TimeUnit.MINUTES else TimeUnit.HOURS
    }

    private fun scheduleDhikr(interval: Long, timeUnit: TimeUnit) {
        val dhikrRequest = PeriodicWorkRequestBuilder<DhikrWorker>(interval, timeUnit)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZinahPeriodicDhikr",
            ExistingPeriodicWorkPolicy.UPDATE,
            dhikrRequest
        )

        val label = if (timeUnit == TimeUnit.MINUTES) "$interval دقيقة" else "$interval ساعة"
        Toast.makeText(this, "تم ضبط التذكير كل $label", Toast.LENGTH_SHORT).show()
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
