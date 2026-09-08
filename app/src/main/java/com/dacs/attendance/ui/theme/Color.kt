package com.dacs.attendance.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette transcribed from the Claude Design source of truth
 * ("Attendance App - Redesign v2.dc.html").
 *
 * v2 keeps the meaning of colour from v1 and softens every surface:
 *   GREEN  = arriving  (Time In)
 *   BROWN  = leaving   (Time Out)
 *   GREY   = locked, or not done yet
 *   RED    = destructive (log out only)
 *
 * A worker reads state off colour before they read any label, so these
 * roles must not be swapped or "harmonised" later. What DID change in v2
 * is the ground: warm off-whites (#F6F7F4 / #E6E7E1) instead of neutral
 * greys, which is what makes the cards read as raised rather than boxed.
 */

// ── Arriving ────────────────────────────────────────────────────────
val Green        = Color(0xFF1A5C3A)
val GreenPressed = Color(0xFF134428)
/** The far end of the Time In hero's gradient. */
val GreenDeep    = Color(0xFF12482D)
val GreenTint    = Color(0xFFEAF2EC)
val GreenSubtle  = Color(0xFFF4F9F6)
val GreenBorder  = Color(0xFFCBE0D2)

// ── Leaving ─────────────────────────────────────────────────────────
val Brown        = Color(0xFF7C5E2A)
val BrownPressed = Color(0xFF63491F)
/** The Time Out hero's gradient, lighter at the top than [Brown]. */
val BrownLight   = Color(0xFF8A6A2F)
val BrownDeep    = Color(0xFF6B4F22)
val BrownTint    = Color(0xFFF5EFE3)

// ── Destructive ─────────────────────────────────────────────────────
val Danger       = Color(0xFFC0392B)
val DangerBorder = Color(0xFFF2DFDC)
val DangerTint   = Color(0xFFFDF5F4)

// ── Surfaces ────────────────────────────────────────────────────────
val Surface       = Color(0xFFFFFFFF)
/** The ground behind the cards. v2's phone body, and the tab screens. */
val Canvas        = Color(0xFFF6F7F4)
val SurfaceRaised = Color(0xFFF6F7F4)
val Field         = Color(0xFFF4F5F1)
/**
 * A primary action that cannot be taken yet -- NEXT before a project is
 * picked, ACCEPT before the box is ticked. Deliberately flat and
 * colourless: the design uses the ABSENCE of the accent to say "not yet".
 */
val Inert         = Color(0xFFEDEDE8)

// ── Text ────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFF1B1B19)
val TextSecondary = Color(0xFF3A3A37)
/** Field labels ("EMAIL", "PASSWORD"). Darker than [TextMuted]. */
val TextLabel     = Color(0xFF5F5F5B)
val TextMuted     = Color(0xFF7A7A75)
/** The mono section labels: "THIS WEEK", "TAP ONE", "STEP 1 OF 4". */
val TextMeta      = Color(0xFF8A8A86)
val TextDisabled  = Color(0xFFA6A6A1)
/** Chevrons and empty photo slots -- present, but not to be read. */
val TextFaint     = Color(0xFFC9C9C4)

// ── Lines ───────────────────────────────────────────────────────────
val BorderDefault = Color(0xFFE6E7E1)
val BorderSoft    = Color(0xFFE2E2DC)
val Hairline      = Color(0xFFF0F0EB)
/** The "no record" card, which is a filled block rather than an outline. */
val Vacant        = Color(0xFFF0F0EB)

// ── The dark screens ────────────────────────────────────────────────
/**
 * What sits behind the live camera view (step 2). Kept as a SURFACE name
 * because the preview and its burned-in caption are read against it, and
 * lightening it to match some future theme would wash the caption out.
 */
val PreviewBackdrop = Color(0xFF131513)
/** The full-screen photo viewer, one shade darker than the camera. */
val ViewerBackdrop  = Color(0xFF0F110F)
/** The frame the viewed photo sits in, so a portrait shot has edges. */
val ViewerFrame     = Color(0xFF1A1C1A)
