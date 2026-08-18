package com.dacs.attendance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The design calls for three families:
 *   Playfair Display -> screen titles, worker name, confirmation headline
 *   Barlow           -> body and buttons
 *   IBM Plex Mono    -> times, totals, worker ID, step counters
 *
 * They are mapped to platform families for now. When the real .ttf files
 * are bundled into res/font/, swap ONLY these three declarations and
 * every call site follows -- nothing else references a family directly.
 *
 * The fonts must be BUNDLED, never fetched. The app has to render with
 * no signal, which is the whole reason it is native.
 */
val DisplayFamily: FontFamily = FontFamily.Serif      // -> Playfair Display
val BodyFamily: FontFamily    = FontFamily.SansSerif  // -> Barlow
val MonoFamily: FontFamily    = FontFamily.Monospace  // -> IBM Plex Mono

val AttendanceTypography = Typography(
    // Confirmation headline ("Time In recorded")
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 39.sp
    ),
    // Screen titles, worker name
    headlineMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 29.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    // The Tagalog line under a label
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 18.sp
    ),
    // Primary action label
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 22.sp
    ),
    // Times and totals
    titleLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp
    )
)
