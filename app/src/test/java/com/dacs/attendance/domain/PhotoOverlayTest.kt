package com.dacs.attendance.domain

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caption burned into the photo.
 *
 * It is drawn onto the bitmap, not overlaid at display time, so it
 * survives export, download and being pasted into a report. That is the
 * point: the photo has to carry its own evidence of when and where.
 */
class PhotoOverlayTest {

    @Test
    fun `the overlay carries project, date and time`() {
        val caption = photoOverlayCaption(
            projectName = "ABC Building Project",
            capturedAt = Instant.parse("2026-08-18T23:45:00Z") // 07:45 Manila, 19th
        )

        assertEquals("ABC Building Project · 19 Aug 2026 · 7:45 AM", caption)
    }

    @Test
    fun `the time is Manila, not the device zone`() {
        // Same instant the record files under 2026-08-19. A photo stamped
        // 18 Aug next to a record dated the 19th is exactly the kind of
        // discrepancy that makes evidence useless in a dispute.
        val caption = photoOverlayCaption("Site", Instant.parse("2026-08-18T23:45:00Z"))

        assertTrue(caption.contains("19 Aug 2026"))
    }

    @Test
    fun `an evening capture reads as PM`() {
        val caption = photoOverlayCaption("Site", Instant.parse("2026-08-19T09:30:00Z"))

        assertTrue(caption, caption.contains("5:30 PM"))
    }

    @Test
    fun `a long project name is truncated rather than overflowing the photo`() {
        // The caption is one line across the bottom of the image. A name
        // that runs off the edge takes the date and time with it.
        val caption = photoOverlayCaption(
            projectName = "A Very Long Construction Project Name That Will Not Fit On One Line",
            capturedAt = Instant.parse("2026-08-19T09:30:00Z")
        )

        assertTrue(caption, caption.startsWith("A Very Long Construction Project"))
        assertTrue(caption, caption.contains("…"))
        assertTrue(caption, caption.contains("5:30 PM"))
    }

    @Test
    fun `the split lines rejoin into exactly the burned-in caption`() {
        // The preview shows two lines; the photo burns one. They must be
        // the same text, or the preview stops being a preview.
        val at = Instant.parse("2026-08-19T09:30:00Z")
        val (name, stamp) = photoOverlayCaptionLines("ABC Building Project", at)

        assertEquals(photoOverlayCaption("ABC Building Project", at), "$name · $stamp")
    }

    @Test
    fun `the timestamp line carries both the date and the time`() {
        // Found on the emulator: the joined caption did not fit across a
        // phone and it was the TIME that got ellipsised away -- the one
        // thing the preview exists to show.
        val (_, stamp) = photoOverlayCaptionLines(
            projectName = "A Very Long Construction Project Name That Will Not Fit",
            capturedAt = Instant.parse("2026-08-19T09:30:00Z")
        )

        assertEquals("19 Aug 2026 · 5:30 PM", stamp)
    }

    @Test
    fun `only the name line is ever truncated`() {
        val (name, stamp) = photoOverlayCaptionLines(
            projectName = "A Very Long Construction Project Name That Will Not Fit",
            capturedAt = Instant.parse("2026-08-19T09:30:00Z")
        )

        assertTrue(name, name.endsWith("…"))
        assertTrue(stamp, !stamp.contains("…"))
    }
}
