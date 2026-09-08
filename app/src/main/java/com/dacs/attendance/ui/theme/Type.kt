package com.dacs.attendance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dacs.attendance.R

/**
 * v2 calls for TWO families, not three:
 *   Barlow        -> everything that is words, headings included
 *   IBM Plex Mono -> times, totals, worker ID, step counters, section labels
 *
 * The v1 design set headings in Playfair Display. v2 drops it: headings
 * are Barlow ExtraBold at ordinary phone sizes, which is what makes the
 * redesign read as an app rather than a poster. The .ttf stays in
 * res/font (licences ship in assets/licenses/) but nothing references it.
 *
 * BUNDLED, never fetched. The app has to render with no signal, which is
 * the whole reason it is native.
 */
val BodyFamily: FontFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
    // Every heading and every button label in v2 is this weight: they are
    // read at arm's length, outdoors, by someone who is not looking closely.
    Font(R.font.barlow_extrabold, FontWeight.ExtraBold)
)

val MonoFamily: FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

/**
 * The v2 scale. Sizes are the design's own, in sp rather than px: they
 * are already phone-sized, so they scale with the system font setting
 * instead of fighting it.
 */
val AttendanceTypography = Typography(
    // The confirmation headline ("All done!")
    displaySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 27.sp,
        lineHeight = 32.sp
    ),
    // "Welcome back", "Which project today?", "My attendance", the date
    headlineMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 23.sp,
        lineHeight = 28.sp
    ),
    // Terms title, the profile name
    headlineSmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 23.sp
    ),
    // A flow screen's question, the worker name on Home
    titleMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        lineHeight = 22.sp
    ),
    // Times and totals
    titleLarge = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    // Body
    bodyLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // Primary action label
    labelLarge = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp
    ),
    // A row title inside a card ("Change password", "Timed in at 7:45 AM")
    labelMedium = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    // The mono section label: "THIS WEEK", "TAP ONE", "STEP 1 OF 4"
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp
    )
)
