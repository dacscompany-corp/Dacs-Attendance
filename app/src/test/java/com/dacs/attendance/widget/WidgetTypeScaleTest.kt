package com.dacs.attendance.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Glance text does not shrink to fit and the title is drawn on one line,
 * so a title too big for the cell is not squeezed -- it is ellipsised.
 * On an API 27 device the widget's own default card showed
 * "DACs Atten..." at the shipped 250x110dp size, which no unit test could
 * see because the size maths was private and unreachable.
 *
 * These pin the geometry instead of the pixels: the longest title the
 * widget can show has to fit the content width at the size chosen for it.
 */
class WidgetTypeScaleTest {

    /** The horizontal padding [typeScale] works inside, from the card. */
    private val cardPadding = 32f

    /**
     * A conservative average advance for bold sans-serif digits and
     * letters, in ems. Deliberately tighter than the figure the
     * implementation uses: the test asserts the title fits, so production
     * is free to be more cautious, never less.
     */
    private val perCharEm = 0.55f

    /** The longest title in strings.xml: "DACs Attendance". */
    private val longestTitle = "DACs Attendance"

    private fun drawnWidth(fontSizeSp: Float, title: String) =
        fontSizeSp * title.length * perCharEm

    @Test
    fun `the longest title fits the card at the shipped widget size`() {
        val size = DpSize(250.dp, 110.dp)

        val type = typeScale(size, longestTitle.length)

        val available = size.width.value - cardPadding
        val drawn = drawnWidth(type.title.value, longestTitle)
        assertTrue(
            "\"$longestTitle\" at ${type.title.value}sp draws ${drawn}dp wide, " +
                "which does not fit ${available}dp and would ellipsise",
            drawn <= available
        )
    }

    @Test
    fun `a short title is allowed to grow larger than a long one on the same card`() {
        val size = DpSize(250.dp, 110.dp)

        val short = typeScale(size, "Time In".length).title.value
        val long = typeScale(size, longestTitle.length).title.value

        assertTrue(
            "a 7-character title got ${short}sp, no more than the " +
                "15-character title's ${long}sp -- the size ignores the title",
            short > long
        )
    }

    @Test
    fun `a long title on a narrow card still stops at the legibility floor`() {
        val type = typeScale(DpSize(100.dp, 110.dp), 40)

        assertTrue(
            "a 40-character title shrank to ${type.title.value}sp, " +
                "below the 18sp floor the card is meant to hold",
            type.title.value >= 18f
        )
    }
}
