package com.example.zinah

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Zinah design system — Islamic-inspired modern look.
 *
 * Color palette:
 *  - Deep emerald greens (primary brand color, evokes mosques' domes & prayer rugs)
 *  - Warm gold accents (evokes illuminated Qur'anic manuscripts)
 *  - Cream / off-white background (instead of stark white — softer on the eye)
 *  - Soft sand surface tones for cards
 *
 * Typography:
 *  - Large bold display for hero numbers (prayer countdown, dhikr text)
 *  - Medium-weight body for secondary text
 *  - Small semi-bold for labels & captions
 */
object ZinahTheme {

    // ---- Brand palette ----
    val EmeraldDarkest = Color(0xFF052E16)   // deep background gradient end
    val EmeraldDeep    = Color(0xFF0B3D20)   // primary dark
    val Emerald        = Color(0xFF145A32)   // primary
    val EmeraldLight   = Color(0xFF2E7D32)   // primary light
    val EmeraldMist    = Color(0xFFE8F5E9)   // surface tint

    val Gold           = Color(0xFFD4AF37)   // accent (illuminated manuscript gold)
    val GoldBright     = Color(0xFFFFD700)
    val GoldDeep       = Color(0xFFB8860B)

    val Cream          = Color(0xFFFAF8F1)   // background (warm off-white)
    val Sand           = Color(0xFFFFF8E1)   // card surface warm
    val SandDeep       = Color(0xFFFFF3C4)

    val Ink            = Color(0xFF1B1B1B)   // primary text
    val InkSoft        = Color(0xFF4A4A4A)   // secondary text
    val InkMute        = Color(0xFF8A8A8A)   // tertiary text

    val Rose           = Color(0xFFE57373)   // destructive / stop
    val Sky            = Color(0xFF4A90E2)   // info

    // ---- Prayer-specific accent colors ----
    val FajrColor      = Color(0xFF6B5B95)   // pre-dawn purple
    val DhuhrColor     = Color(0xFFF4A261)   // midday sun
    val AsrColor       = Color(0xFFE76F51)   // afternoon orange
    val MaghribColor   = Color(0xFF9C5A3C)   // sunset rust
    val IshaColor      = Color(0xFF3D5A80)   // night blue

    val colorScheme = lightColorScheme(
        primary = Emerald,
        onPrimary = Color.White,
        primaryContainer = EmeraldLight,
        onPrimaryContainer = Color.White,
        secondary = Gold,
        onSecondary = Color(0xFF1B1B1B),
        secondaryContainer = SandDeep,
        onSecondaryContainer = Ink,
        tertiary = GoldDeep,
        background = Cream,
        onBackground = Ink,
        surface = Color.White,
        onSurface = Ink,
        surfaceVariant = Sand,
        onSurfaceVariant = InkSoft,
        outline = Gold,
        outlineVariant = Color(0xFFE0E0E0),
        error = Rose,
        onError = Color.White
    )

    val typography = Typography(
        displayLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            color = Ink
        ),
        displayMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 42.sp,
            lineHeight = 48.sp,
            color = Ink
        ),
        displaySmall = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            color = Ink
        ),
        headlineLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            lineHeight = 34.sp,
            color = EmeraldDeep
        ),
        headlineMedium = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            color = EmeraldDeep
        ),
        headlineSmall = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            color = Emerald
        ),
        titleLarge = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            color = Ink
        ),
        titleMedium = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Ink
        ),
        titleSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = InkSoft
        ),
        bodyLarge = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Ink
        ),
        bodyMedium = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = InkSoft
        ),
        bodySmall = TextStyle(
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = InkMute
        ),
        labelLarge = TextStyle(
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Ink
        ),
        labelMedium = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = InkSoft
        ),
        labelSmall = TextStyle(
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = InkMute
        )
    )

    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp)
    )
}

@Composable
fun ZinahTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ZinahTheme.colorScheme,
        typography = ZinahTheme.typography,
        shapes = ZinahTheme.shapes,
        content = content
    )
}
