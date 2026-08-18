package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Mirrors the guard inside attendance_time_in / attendance_time_out
 * (0050 section 8). The app checks it at LOGIN so a deactivated worker is
 * told why at the door, instead of getting through and failing four
 * screens later at SUBMIT. The RPC still enforces it -- this is the
 * message, not the security.
 */
class WorkerEligibilityTest {

    @Test
    fun `an active worker may sign in`() {
        assertEquals(Eligibility.Allowed, eligibilityOf(role = "worker", status = "active"))
    }

    @Test
    fun `a team leader is a worker for attendance purposes`() {
        assertEquals(Eligibility.Allowed, eligibilityOf(role = "teamLeader", status = "active"))
    }

    @Test
    fun `a null status counts as active`() {
        // The SQL says coalesce(p.status,'active'). Older profiles rows have
        // no status at all; locking them out would be a bug the RPC does not
        // have.
        assertEquals(Eligibility.Allowed, eligibilityOf(role = "worker", status = null))
    }

    @Test
    fun `a deactivated worker is refused at login`() {
        assertEquals(
            Eligibility.AccountInactive,
            eligibilityOf(role = "worker", status = "inactive")
        )
    }

    @Test
    fun `an owner account is not a worker account`() {
        assertEquals(Eligibility.NotAWorker, eligibilityOf(role = "owner", status = "active"))
    }

    @Test
    fun `a missing profile row is not a worker`() {
        assertEquals(Eligibility.NotAWorker, eligibilityOf(role = null, status = null))
    }
}
