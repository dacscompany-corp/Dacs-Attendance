package com.dacs.attendance.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Sizing from the v2 design.
 *
 * v1 shouted: a 76dp primary action and a 230dp hero. v2 says "normal
 * phone type sizes, softer surfaces" and brings the button down to 58dp
 * -- still well clear of Material's 48dp minimum, and still tappable
 * with a work glove, but no longer the only thing on the screen.
 *
 * The hero did NOT shrink. It became a card with its own content instead
 * of a slab with a label, which is what buys the room back.
 */
object Dimens {
    /** The one primary action on a screen. */
    val ActionHeight    = 58.dp
    /** ACCEPT & CONTINUE, which sits under a checkbox rather than alone. */
    val ActionCompact   = 56.dp
    /** Retake, and any action that is the second of a pair. */
    val SecondaryHeight = 52.dp
    /** Text fields. */
    val FieldHeight     = 52.dp
    /** The square back button, and the notification bell beside the name. */
    val IconButton      = 38.dp
    /** A leading icon tile inside a row (project picker, terms clause). */
    val IconTile        = 38.dp
    /** The camera shutter. */
    val Shutter         = 78.dp
    /** The week/month segmented control, and a photo-viewer footer button. */
    val SegmentHeight   = 44.dp
    /** A history row's photo thumbnail. */
    val Thumb           = 36.dp

    val RadiusPill    = 999.dp
    val RadiusHero    = 26.dp
    /** A photo, in the camera, the review and the viewer. */
    val RadiusPhoto   = 24.dp
    /** The profile portrait card and the confirmation receipt. */
    val RadiusPanel   = 22.dp
    val RadiusCard    = 20.dp
    val RadiusRow     = 18.dp
    val RadiusButton  = 16.dp
    val RadiusField   = 14.dp
    val RadiusTile    = 13.dp
    val RadiusSmall   = 12.dp
    val RadiusThumb   = 11.dp
    val RadiusCheck   = 7.dp

    /** The gutter on a tab screen. Flow screens use the same. */
    val ScreenPadding = 20.dp
    /** Login and Terms, which have no cards to indent from the edge. */
    val SheetPadding  = 24.dp
    /** Above the home indicator, so the action is not on the gesture bar. */
    val BottomPadding = 26.dp

    val GapSmall  = 8.dp
    val GapMedium = 14.dp
    val GapLarge  = 20.dp

    /** The 4-step progress bar under a flow header. */
    val ProgressHeight = 5.dp
    /** The hours-so-far bar on Home, which is thicker and reads as a gauge. */
    val MeterHeight    = 7.dp
}
