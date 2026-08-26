package com.dacs.attendance.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dacs.attendance.R

/**
 * The design calls for three families:
 *   Playfair Display -> screen titles, worker name, confirmation headline
 *   Barlow           -> body and buttons
 *   IBM Plex Mono    -> times, totals, worker ID, step counters
 *
 * BUNDLED, never fetched. The app has to render with no signal, which
 * is the whole reason it is native. Licences ship in assets/licenses/
 * (all three are SIL OFL 1.1, which requires the licence to travel with
 * the fonts).
 *
 * Playfair Display is the VARIABLE font -- Google no longer publishes
 * static cuts of it. Compose applies the weight axis on API 26+; on
 * 24-25 it renders the regular instance, so headings are lighter there
 * rather than missing. That is a deliberate trade for keeping minSdk 24,
 * and it degrades in the one direction that stays legible.
 */
@OptIn(ExperimentalTextApi::class)
val DisplayFamily: FontFamily = FontFamily(
    Font(R.font.playfair_display, FontWeight.Normal),
    Font(
        R.font.playfair_display,
        FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.playfair_display,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

val BodyFamily: FontFamily = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_bold, FontWeight.Bold),
    // The action buttons are ExtraBold: they are read at arm's length,
    // outdoors, by someone who is not looking closely.
    Font(R.font.barlow_extrabold, FontWeight.ExtraBold)
)

val MonoFamily: FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold)
)

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
