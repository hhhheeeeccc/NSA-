package com.example.zinah

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class AdhanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val prayerName = intent.getStringExtra(AdhanForegroundService.EXTRA_PRAYER_NAME)
            ?: intent.getStringExtra("prayer_name")
            ?: "الصلاة"

        setContent {
            AdhanScreen(prayerName = prayerName, onStop = { stopAdhanAndFinish() })
        }
    }

    private fun stopAdhanAndFinish() {
        try {
            val stopIntent = Intent(this, AdhanForegroundService::class.java).apply {
                action = AdhanForegroundService.ACTION_STOP
            }
            startService(stopIntent)
            AdhanPlayer.stop()
        } catch (e: Exception) {}
        finish()
    }
}

@Composable
private fun AdhanScreen(prayerName: String, onStop: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0B3D20)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "حان وقت الصلاة",
                    fontSize = 22.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("الأذان", fontSize = 16.sp, color = Color(0xFFFFD700),
                        fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "صلاة $prayerName",
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .width(60.dp).height(2.dp)
                            .background(Color(0xFFFFD700))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("حي على الصلاة", fontSize = 18.sp, color = Color(0xFFFFD700))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("حي على الفلاح", fontSize = 18.sp, color = Color(0xFFFFD700))
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(130.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Pause, contentDescription = "إيقاف",
                            tint = Color(0xFF0B3D20), modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("إيقاف", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF0B3D20))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("يتم تشغيل الأذان الآن",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
