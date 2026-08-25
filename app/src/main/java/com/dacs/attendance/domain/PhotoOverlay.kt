package com.dacs.attendance.domain

import java.time.Instant
import java.time.format.DateTimeFormatter

private val OverlayDate = DateTimeFormatter.ofPattern("d MMM yyyy")
private val OverlayTime = DateTimeFormatter.ofPattern("h:mm a")

/** Beyond this the caption stops fitting on one line across the photo. */
private const val MAX_PROJECT_CHARS = 32

/**
 * The caption burned into the attendance photo:
 *
 *     ABC Building Project · 19 Aug 2026 · 7:45 AM
 *
 * Drawn ONTO the bitmap, not overlaid at display time, so it survives
 * export, download, and being pasted into a report. The photo has to
 * carry its own evidence of when and where.
 *
 * The time is Manila, matching the work_date the record files under. A
 * photo stamped with the device's zone would read 18 Aug beside a record
 * dated the 19th -- exactly the discrepancy that makes evidence useless.
 */
fun photoOverlayCaption(projectName: String, capturedAt: Instant): String {
    val local = capturedAt.atZone(AttendanceZone)
    val project = if (projectName.length <= MAX_PROJECT_CHARS) {
        projectName
    } else {
        // Truncate the NAME, never the timestamp: a long project title
        // must not push the date and time off the edge of the image.
        projectName.take(MAX_PROJECT_CHARS).trimEnd() + "…"
    }

    return "$project · ${local.format(OverlayDate)} · ${local.format(OverlayTime)}"
}
