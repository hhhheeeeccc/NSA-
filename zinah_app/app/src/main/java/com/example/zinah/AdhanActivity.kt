package com.example.zinah

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Premium full-screen adhan activity.
 *
 * Visual treatment:
 *  - Deep emerald-to-black gradient background
 *  - Animated pulsing radial glow behind the prayer name
 *  - 8-pointed Islamic star decorations (gold, semi-transparent)
 *  - Geometric pattern overlay (subtle)
 *  - Large circular STOP button with gold gradient
 */
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
            AdhanScreen(
                prayerName = prayerName,
                onStop = { stopAdhanAndFinish() }
            )
        }
    }

    private fun stopAdhanAndFinish() {
        val stopIntent = Intent(this, AdhanForegroundService::class.java).apply {
            action = AdhanForegroundService.ACTION_STOP
        }
        startService(stopIntent)
        AdhanPlayer.stop()
        finish()
    }
}

@Composable
private fun AdhanScreen(prayerName: String, onStop: () -> Unit) {
    ZinahTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PremiumEmeraldGradient)
        ) {
            // Pulsing glow behind content
            val infiniteTransition = rememberInfiniteTransition(label = "glow")
            val glowScale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "glowScale"
            )
            Box(
                modifier = Modifier
                    .size(400.dp)
                    .align(Alignment.Center)
                    .scale(glowScale)
                    .blur(80.dp)
                    .background(ZinahTheme.Gold.copy(alpha = 0.18f))
            )

            // Decorative 8-pointed stars (corners)
            EightPointStar(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(24.dp),
                size = 80.dp,
                color = ZinahTheme.Gold.copy(alpha = 0.35f),
                strokeWidth = 1.5.dp
            )
            EightPointStar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp),
                size = 60.dp,
                color = ZinahTheme.Gold.copy(alpha = 0.25f),
                strokeWidth = 1.5.dp
            )
            EightPointStar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp),
                size = 60.dp,
                color = ZinahTheme.Gold.copy(alpha = 0.25f),
                strokeWidth = 1.5.dp
            )
            EightPointStar(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                size = 80.dp,
                color = ZinahTheme.Gold.copy(alpha = 0.35f),
                strokeWidth = 1.5.dp
            )

            // Subtle geometric pattern overlay
            GeometricPatternBackground(
                modifier = Modifier.fillMaxSize(),
                color = ZinahTheme.Gold.copy(alpha = 0.05f),
                starSize = 32.dp,
                spacing = 64.dp
            )

            // Main content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp, vertical = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top: crescent + label
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CrescentMoon(size = 72.dp, primaryColor = ZinahTheme.GoldBright)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        "حان وقت الصلاة",
                        fontSize = 22.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Medium
                    )
                }

                // Center: prayer name card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White.copy(alpha = 0.08f)
                    ),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "الأذان",
                            fontSize = 16.sp,
                            color = ZinahTheme.GoldBright,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 4.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "صلاة $prayerName",
                            fontSize = 56.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        // Gold divider
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(2.dp)
                                .background(GoldGradient)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "حي على الصلاة",
                            fontSize = 18.sp,
                            color = ZinahTheme.GoldBright,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "حي على الفلاح",
                            fontSize = 18.sp,
                            color = ZinahTheme.GoldBright,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Bottom: STOP button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.size(140.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(GoldGradient)
                                .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.Pause,
                                    contentDescription = "إيقاف",
                                    tint = ZinahTheme.EmeraldDeep,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "إيقاف",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ZinahTheme.EmeraldDeep
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "يتم تشغيل الأذان الآن",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
