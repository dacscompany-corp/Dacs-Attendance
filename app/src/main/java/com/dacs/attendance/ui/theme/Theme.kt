package com.dacs.attendance.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * LIGHT ONLY, on purpose.
 *
 * This app is used outdoors in direct sunlight, where a light, high
 * contrast surface is far more readable. A dark theme would also break
 * the green/brown/grey status language the design relies on. So the
 * system dark setting is deliberately ignored rather than followed.
 */
private val AttendanceColors = lightColorScheme(
    primary            = Green,
    onPrimary          = Surface,
    primaryContainer   = GreenTint,
    onPrimaryContainer = GreenPressed,

    secondary          = Brown,
    onSecondary        = Surface,

    error              = Danger,
    onError            = Surface,
    errorContainer     = DangerTint,
    onErrorContainer   = Danger,

    background         = Canvas,
    onBackground       = TextPrimary,
    surface            = Surface,
    onSurface          = TextPrimary,
    surfaceVariant     = SurfaceRaised,
    onSurfaceVariant   = TextMuted,
    outline            = BorderDefault,
    outlineVariant     = Hairline
)

@Composable
fun AttendanceTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AttendanceColors,
        typography = AttendanceTypography,
        content = content
    )
}
