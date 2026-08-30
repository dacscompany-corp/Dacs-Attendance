package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The description quick chips (design screen 07).
 *
 * The rule these guard: a chip NEVER destroys text the worker typed. The
 * description is the only free-form thing a worker contributes to their
 * own record, and losing it to a mis-tap is not recoverable -- the photo
 * is already taken and the day is already being submitted.
 */
class DescriptionChipsTest {

    @Test
    fun `tapping a chip on an empty box fills it`() {
        assertEquals("Masonry", toggleDescriptionChip("", "Masonry"))
    }

    @Test
    fun `a second chip is added, not substituted`() {
        // A day is usually more than one job.
        assertEquals(
            "Masonry, Concrete pouring",
            toggleDescriptionChip("Masonry", "Concrete pouring")
        )
    }

    @Test
    fun `tapping a chip that is already there removes it`() {
        assertEquals(
            "Concrete pouring",
            toggleDescriptionChip("Masonry, Concrete pouring", "Masonry")
        )
    }

    @Test
    fun `removing the only chip empties the box`() {
        assertEquals("", toggleDescriptionChip("Masonry", "Masonry"))
    }

    @Test
    fun `typed text survives a chip being added`() {
        assertEquals(
            "Gate 2, may delivery, Masonry",
            toggleDescriptionChip("Gate 2, may delivery", "Masonry")
        )
    }

    @Test
    fun `typed text survives a chip being removed`() {
        assertEquals(
            "Gate 2, may delivery",
            toggleDescriptionChip("Gate 2, may delivery, Masonry", "Masonry")
        )
    }

    @Test
    fun `a newline stays inside its part instead of splitting it`() {
        // Workers do press enter. Splitting there would turn one note
        // into two and re-join it with a comma the worker never typed.
        val typed = "Gate 2\nmay delivery"

        assertEquals(listOf(typed), descriptionParts(typed))
        assertEquals("$typed, Masonry", toggleDescriptionChip(typed, "Masonry"))
    }

    @Test
    fun `a chip already typed by hand is not duplicated`() {
        // Typed "masonry", then tapped the chip. One thing, not two.
        assertTrue(isChipSelected("masonry", "Masonry"))
        assertEquals("", toggleDescriptionChip("masonry", "Masonry"))
    }

    @Test
    fun `selection only matches a whole part`() {
        // "Masonry work" is not the "Masonry" chip -- tapping the chip
        // must add it rather than silently deleting what was typed.
        assertFalse(isChipSelected("Masonry work", "Masonry"))
        assertEquals(
            "Masonry work, Masonry",
            toggleDescriptionChip("Masonry work", "Masonry")
        )
    }

    @Test
    fun `stray separators do not become empty parts`() {
        assertEquals(listOf("Masonry"), descriptionParts("Masonry, , "))
        assertEquals("", toggleDescriptionChip("Masonry, , ", "Masonry"))
    }
}
