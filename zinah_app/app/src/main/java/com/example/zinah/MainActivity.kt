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

                        var selectedInterval by remember { mutableStateOf(getSavedInterval()) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("إعدادات التذكير التلقائي", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    Button(onClick = {
                                        selectedInterval = 1L
                                        saveInterval(1L)
                                        scheduleDhikr(1L, TimeUnit.HOURS)
                                    }) { Text("كل ساعة") }

                                    Button(onClick = {
                                        selectedInterval = 3L
                                        saveInterval(3L)
                                        scheduleDhikr(3L, TimeUnit.HOURS)
                                    }) { Text("كل 3 ساعات") }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("الفاصل الحالي: كل $selectedInterval ساعة", color = Color.Gray)
                            }
                        }

                        Text(
                            "الأذكار المتاحة",
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(AdhkarData.allAdhkar) { dhikr ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Text(
                                        text = dhikr,
                                        modifier = Modifier.padding(16.dp),
                                        fontSize = 18.sp,
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


    private fun saveInterval(interval: Long) {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putLong("interval", interval)
            apply()
        }
    }

    private fun getSavedInterval(): Long {
        val sharedPref = getSharedPreferences("ZinahPrefs", Context.MODE_PRIVATE)
        return sharedPref.getLong("interval", 1L)
    }
private fun scheduleDhikr(interval: Long, timeUnit: TimeUnit) {
        val dhikrRequest = PeriodicWorkRequestBuilder<DhikrWorker>(interval, timeUnit)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "ZinahPeriodicDhikr",
            ExistingPeriodicWorkPolicy.UPDATE,
            dhikrRequest
        )

        Toast.makeText(this, "تم ضبط التذكير بنجاح", Toast.LENGTH_SHORT).show()
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
