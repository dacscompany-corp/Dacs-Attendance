package com.dacs.attendance.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette transcribed from the Claude Design source of truth
 * ("MVP terms application design" / Attendance App.dc.html).
 *
 * Colour carries meaning in this app and is not decoration:
 *   GREEN  = arriving  (Time In)
 *   BROWN  = leaving   (Time Out)
 *   GREY   = locked, or not done yet
 *   RED    = destructive (log out only)
 *
 * A worker reads state off colour before they read any label, so these
 * roles must not be swapped or "harmonised" later.
 */

// ── Arriving ────────────────────────────────────────────────────────
val Green        = Color(0xFF1A5C3A)
val GreenPressed = Color(0xFF134428)
val GreenTint    = Color(0xFFEAF2EC)
val GreenSubtle  = Color(0xFFF7FBF8)
val GreenBorder  = Color(0xFFCBE0D2)

// ── Leaving ─────────────────────────────────────────────────────────
val Brown        = Color(0xFF7C5E2A)
val BrownPressed = Color(0xFF63491F)

// ── Destructive ─────────────────────────────────────────────────────
val Danger       = Color(0xFFC0392B)
val DangerBorder = Color(0xFFF0DAD7)
val DangerTint   = Color(0xFFFDF5F4)

// ── Surfaces ────────────────────────────────────────────────────────
val Surface      = Color(0xFFFFFFFF)
val SurfaceRaised = Color(0xFFF7F7F5)
val Canvas       = Color(0xFFF0F0EE)
val Field        = Color(0xFFFAFAFA)

// ── Text ────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFF1C1C1E)
val TextSecondary = Color(0xFF3A3A3C)
val TextMuted     = Color(0xFF6C6C70)
val TextDisabled  = Color(0xFF9A9A9E)

// ── Lines ───────────────────────────────────────────────────────────
val BorderDefault = Color(0xFFE5E5E5)
val Hairline      = Color(0xFFF0F0EE)
