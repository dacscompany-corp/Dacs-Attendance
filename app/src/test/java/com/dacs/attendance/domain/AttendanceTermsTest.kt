package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Terms text is EVIDENCE, not copy. Its sha256 is written into
 * agreement_events (0021) alongside the full text, and that table is
 * append-only even to service_role. So the hash has to be a real sha256
 * of exactly what the worker was shown -- these tests pin both halves.
 */
class AttendanceTermsTest {

    @Test
    fun `sha256 matches the published test vector`() {
        // NIST's own single-block vector. If this fails the digest helper is
        // wrong, and every acceptance row we ever wrote is unverifiable.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc")
        )
    }

    @Test
    fun `canonical text carries the version and every clause`() {
        val text = AttendanceTerms.canonicalText()

        assertTrue(text.startsWith("DAC's Attendance"))
        assertTrue(text.contains(AttendanceTerms.VERSION))
        AttendanceTerms.clauses.forEach { clause ->
            assertTrue("missing: ${clause.english}", text.contains(clause.english))
            assertTrue("missing: ${clause.tagalog}", text.contains(clause.tagalog))
        }
    }

    @Test
    fun `document hash is the hash of the canonical text`() {
        assertEquals(sha256Hex(AttendanceTerms.canonicalText()), AttendanceTerms.sha256())
    }

    @Test
    fun `the shipped terms have five clauses in design order`() {
        // Screens 01-02 of the design list these five and no others. Adding a
        // clause is allowed -- but it MUST come with a VERSION bump, or
        // workers who accepted the old text are never re-prompted.
        assertEquals(5, AttendanceTerms.clauses.size)
        assertEquals("Recording attendance.", AttendanceTerms.clauses.first().heading)
    }
}
