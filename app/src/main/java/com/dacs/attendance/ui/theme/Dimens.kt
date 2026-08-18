package com.dacs.attendance.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Sizing from the design. These numbers are an ACCESSIBILITY decision,
 * not a style preference: the app is used outdoors, in daylight, by
 * workers who may be wearing gloves and are often in a hurry.
 *
 * Do not "tighten these up" to fit more on screen. One primary action
 * per screen is the design, and it needs the room.
 */
object Dimens {
    /** Primary action button. Material's 48dp minimum is not enough here. */
    val ActionHeight   = 76.dp
    /** The dashboard's big TIME IN / TIME OUT target. */
    val HeroMaxHeight  = 230.dp
    /** Text fields. */
    val FieldHeight    = 62.dp
    /** A row in the project picker. */
    val ProjectRow     = 74.dp
    /** Secondary actions (Retake, profile rows). */
    val SecondaryRow   = 66.dp

    val RadiusSmall  = 12.dp
    val RadiusMedium = 14.dp
    val RadiusLarge  = 16.dp
    val RadiusHero   = 22.dp

    val ScreenPadding = 24.dp
    val GapSmall      = 8.dp
    val GapMedium     = 16.dp
    val GapLarge      = 26.dp
}
