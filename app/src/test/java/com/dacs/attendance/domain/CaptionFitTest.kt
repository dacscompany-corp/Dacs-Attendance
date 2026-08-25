package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shrinking the burned-in caption until it fits the photo.
 *
 * Found on a device: "ABC Building Project · 25 Aug 2026 · 8:45 PM" ran
 * off the right edge and lost its "PM". Truncating the project name was
 * not enough, and a timestamp missing AM/PM is ambiguous evidence -- the
 * one thing the caption exists to prevent.
 */
class CaptionFitTest {

    /** Stand-in for Paint.measureText: a fixed width per character. */
    private fun measurer(charWidthAt1pt: Float, text: String): (Float) -> Float =
        { size -> text.length * charWidthAt1pt * size }

    @Test
    fun `text that already fits is left alone`() {
        val size = fitTextSize(
            preferred = 40f,
            maxWidth = 1000f,
            measure = measurer(0.5f, "short")  // 5 * 0.5 * 40 = 100
        )

        assertEquals(40f, size, 0.01f)
    }

    @Test
    fun `text that overflows is shrunk until it fits`() {
        val caption = "ABC Building Project · 25 Aug 2026 · 8:45 PM" // 44 chars
        val measure = measurer(0.6f, caption)

        val size = fitTextSize(preferred = 40f, maxWidth = 600f, measure = measure)

        assertTrue("should have shrunk from 40", size < 40f)
        assertTrue("must actually fit", measure(size) <= 600f)
    }

    @Test
    fun `shrinking stops at a floor rather than becoming unreadable`() {
        // A caption nobody can read is no better than one that is cut
        // off. Below the floor the project name should be truncated
        // further instead -- which photoOverlayCaption already does.
        val size = fitTextSize(
            preferred = 40f,
            maxWidth = 10f,
            measure = measurer(1f, "an extremely long caption that cannot fit")
        )

        assertEquals(MIN_CAPTION_TEXT_SIZE_RATIO * 40f, size, 0.01f)
    }
}
