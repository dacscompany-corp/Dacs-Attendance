package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sample size decides whether a Time In succeeds or dies of memory on
 * the cheap phones this app exists for. It also must not cost quality:
 * the photo is evidence, and the budget edge is what the office reads.
 */
class PhotoSamplingTest {

    /** The storage budget in the design's section 4.7. */
    private val budget = 1600

    @Test
    fun `a capture already inside the budget is decoded whole`() {
        assertEquals(1, photoSampleSize(1600, 1200, budget))
    }

    @Test
    fun `a capture under the budget is decoded whole`() {
        assertEquals(1, photoSampleSize(800, 600, budget))
    }

    @Test
    fun `orientation does not matter -- the longest edge decides`() {
        assertEquals(
            photoSampleSize(4000, 3000, budget),
            photoSampleSize(3000, 4000, budget)
        )
    }

    @Test
    fun `a 12 MP capture halves once`() {
        // 4000 / 2 = 2000, still above the budget; / 4 = 1000 would fall
        // under it and cost detail the office cannot get back.
        assertEquals(2, photoSampleSize(4000, 3000, budget))
    }

    @Test
    fun `a 32 MP capture quarters`() {
        assertEquals(4, photoSampleSize(6528, 4896, budget))
    }

    @Test
    fun `sampling never takes a capture below the budget`() {
        // The guarantee the caller relies on: whatever comes back is still
        // big enough to scale down to exactly the budget edge, so the
        // filed photo keeps the dimensions it has today.
        for (longest in listOf(1600, 1601, 2400, 3200, 4000, 6528, 9000, 12000)) {
            val sample = photoSampleSize(longest, longest / 2, budget)
            assertTrue(
                "sampling $longest by $sample fell under the budget",
                longest / sample >= budget
            )
        }
    }

    @Test
    fun `the sample is always a power of two`() {
        // BitmapFactory rounds anything else down to one, so returning a
        // non-power-of-two would silently decode larger than intended.
        for (longest in listOf(1600, 2400, 3200, 4000, 6528, 9000, 12000, 20000)) {
            val sample = photoSampleSize(longest, longest, budget)
            assertTrue(
                "$sample is not a power of two",
                sample > 0 && sample and (sample - 1) == 0
            )
        }
    }

    @Test
    fun `a bitmap that could not be measured decodes whole rather than vanishing`() {
        // BitmapFactory reports -1 for a file it could not read. Returning
        // a huge sample there would turn an unreadable photo into a
        // one-pixel one, which uploads and proves nothing.
        assertEquals(1, photoSampleSize(-1, -1, budget))
        assertEquals(1, photoSampleSize(0, 0, budget))
    }
}
