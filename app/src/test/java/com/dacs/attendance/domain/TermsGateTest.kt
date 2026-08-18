package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TermsGateTest {

    @Test
    fun `a worker who has accepted nothing must accept`() {
        assertEquals(TermsGate.Decision.MustAccept, TermsGate.decide(emptySet(), "2026-08-v1"))
    }

    @Test
    fun `a worker who accepted the current version goes straight through`() {
        assertEquals(
            TermsGate.Decision.AlreadyAccepted,
            TermsGate.decide(setOf("2026-08-v1"), "2026-08-v1")
        )
    }

    @Test
    fun `an older acceptance does not satisfy a newer version`() {
        // This is the whole point of versioning the table: changing the Terms
        // has to be able to force re-acceptance.
        assertEquals(
            TermsGate.Decision.MustAccept,
            TermsGate.decide(setOf("2026-01-v0"), "2026-08-v1")
        )
    }
}
