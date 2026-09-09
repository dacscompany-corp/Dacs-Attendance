package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `the shipped terms have seven clauses in design order`() {
        // Screens 01-02 of the design listed five. The sixth, the location
        // check, was added when migrations 0068/0069 made location a
        // condition of recording work -- terms that never mentioned it
        // would have been describing an app that no longer exists.
        //
        // Adding a clause is allowed, but it MUST come with a VERSION
        // bump, or workers who accepted the old text are never re-prompted
        // and their acceptance row claims wording they never saw.
        assertEquals(7, AttendanceTerms.clauses.size)
        assertEquals("Recording attendance.", AttendanceTerms.clauses.first().heading)
    }

    @Test
    fun `the location clause states it is not continuous tracking`() {
        // The single most likely fear, and the MVP terms are explicit that
        // this must not become a worker-monitoring system. If that promise
        // ever leaves the text, it should fail here rather than quietly.
        val clause = AttendanceTerms.clauses.single { it.heading == "Location check." }

        assertTrue(clause.english.contains("does not follow you"))
        assertTrue(clause.tagalog.contains("hindi ka sinusundan"))
    }

    @Test
    fun `the location clause says a poor signal does not cost the day`() {
        // Uncertainty is recorded and flagged, never refused (0069). The
        // terms have to say so, or they read far harsher than the software
        // behaves -- and a worker would reasonably stop trying on a bad
        // signal day.
        val clause = AttendanceTerms.clauses.single { it.heading == "Location check." }

        assertTrue(clause.english.contains("still"))
        assertTrue(clause.english.contains("marked for the Admin"))
    }

    @Test
    fun `the version was bumped away from the pre-location wording`() {
        // The gate keys on this string. Editing clauses without moving it
        // leaves every past acceptance claiming text nobody was shown.
        assertNotEquals("2026-08-v2", AttendanceTerms.VERSION)
    }

    @Test
    fun `the reward clause names no amount and no cutoff time`() {
        // Both are configuration the Owner can change -- the amount in
        // attendance_config, the start time per project since 0065. This
        // text is hashed into agreement_events as evidence, so a number
        // here would mean binding text that goes quietly wrong the day it
        // changes, or re-prompting every worker over a raise.
        val clause = AttendanceTerms.clauses.single { it.heading == "Weekly attendance reward." }

        assertFalse(clause.english.contains("500"))
        assertFalse(clause.tagalog.contains("500"))
        assertFalse(clause.english.contains("9:00"))
        assertFalse(clause.english.contains("9 AM"))
    }

    @Test
    fun `the reward clause promises a closed day is not held against the worker`() {
        // The rule that stops a weekday holiday disqualifying everyone,
        // eight to ten times a year, through no fault of theirs. If it
        // ever leaves the software this should fail alongside it.
        val clause = AttendanceTerms.clauses.single { it.heading == "Weekly attendance reward." }

        assertTrue(clause.english.contains("closed are not counted against you"))
        assertTrue(clause.tagalog.contains("sarado"))
    }

    @Test
    fun `the reward clause does not contradict the pay clause`() {
        // Clause 5 states these records are not what pay is computed from
        // -- DAC's labour is pakyaw, capped by contract. A reward clause
        // that read as wages would be a false statement in binding text,
        // and the sentence a worker would quote back in a dispute.
        val reward = AttendanceTerms.clauses.single { it.heading == "Weekly attendance reward." }

        assertTrue(reward.english.contains("separate from your pay"))
        assertTrue(reward.english.contains("not computed from your hours"))
    }
}
