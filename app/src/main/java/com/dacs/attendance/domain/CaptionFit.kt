package com.dacs.attendance.domain

/**
 * How small the caption may get before shrinking stops. Past this it is
 * unreadable on a phone screen, which is no better than being cut off.
 */
const val MIN_CAPTION_TEXT_SIZE_RATIO = 0.55f

/**
 * The largest text size at or below [preferred] whose rendered width
 * fits [maxWidth].
 *
 * Exists because truncating the project name alone did not stop the
 * caption running off the edge of the photo -- it took the "PM" with it,
 * turning 8:45 PM into an ambiguous 8:45. The timestamp is the part that
 * must never be lost.
 *
 * [measure] is injected so this is testable without a Canvas; in the app
 * it is Paint.measureText.
 */
fun fitTextSize(
    preferred: Float,
    maxWidth: Float,
    measure: (Float) -> Float
): Float {
    if (measure(preferred) <= maxWidth) return preferred

    val floor = preferred * MIN_CAPTION_TEXT_SIZE_RATIO
    var size = preferred
    // Coarse steps: the caption is drawn once per photo, and a binary
    // search would be precision nobody can see.
    while (size > floor) {
        size -= preferred * 0.05f
        if (measure(size) <= maxWidth) return size
    }
    return floor
}
