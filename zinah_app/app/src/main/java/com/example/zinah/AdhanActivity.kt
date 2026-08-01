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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-screen activity shown when a prayer time arrives.
 *
 * Mirrors the behavior of [NotificationActivity] for dhikr:
 *  - Shows on the lock screen (showWhenLocked + turnScreenOn)
 *  - Uses singleTask launch mode (declared in AndroidManifest)
 *  - Displays the prayer name and a STOP button that stops the adhan and closes the activity.
 *
 * The actual audio playback is owned by [AdhanForegroundService]; this activity only
 * shows the UI and offers a way to stop playback. If the activity is swiped away, the
 * adhan keeps playing in the foreground service.
 */
class AdhanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on, show on lock screen, turn screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        val prayerName = intent.getStringExtra(AdhanForegroundService.EXTRA_PRAYER_NAME)
            ?: intent.getStringExtra("prayer_name")
            ?: "الصلاة"

        setContent {
            AdhanScreen(
                prayerName = prayerName,
                onStop = { stopAdhanAndFinish() }
            )
        }
    }

    private fun stopAdhanAndFinish() {
        // Stop the foreground service (which stops the audio)
        val stopIntent = Intent(this, AdhanForegroundService::class.java).apply {
            action = AdhanForegroundService.ACTION_STOP
        }
        startService(stopIntent)
        // Also stop locally as a safety net
        AdhanPlayer.stop()
        finish()
    }
}

@Composable
private fun AdhanScreen(prayerName: String, onStop: () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF1B5E20),
            secondary = Color(0xFFFFD700),
            background = Color(0xFF0D3D14)
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1B5E20),
                                Color(0xFF0D3D14),
                                Color(0xFF05240A)
                            )
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 48.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top decoration: crescent + label
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "☪", fontSize = 80.sp, color = Color(0xFFFFD700))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "حان وقت الصلاة",
                            fontSize = 22.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Center: prayer name + adhan label
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20).copy(alpha = 0.6f)),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "الأذان",
                                fontSize = 18.sp,
                                color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "صلاة $prayerName",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "حي على الصلاة، حي على الفلاح",
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Bottom: STOP button (large circular)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onStop,
                            modifier = Modifier
                                .size(120.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFFD700),
                                contentColor = Color(0xFF1B5E20)
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "إيقاف",
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "إيقاف",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "يتم تشغيل الأذان الآن",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
