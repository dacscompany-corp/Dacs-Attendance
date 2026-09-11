package com.dacs.attendance.ui

import com.dacs.attendance.domain.AttendanceFailure
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkerProfile
import com.dacs.attendance.ui.dashboard.DashboardUiState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A widget tap is a request, not a command. The widget may be showing a
 * day that has moved on, and the worker may be halfway through a capture.
 */
class StartFlowRequestTest {

    private val worker = WorkerProfile(
        id = "w1",
        email = "w1@example.com",
        displayName = "Juan",
        position = null,
        workerNo = 42,
        role = "worker",
        status = "active"
    )
    private val signedIn = AppState.SignedIn(worker)

    private fun working() = AttendanceRecord(
        id = "rec-1",
        workDate = "2026-09-11",
        status = AttendanceStatus.WORKING,
        timeInAt = Instant.parse("2026-09-11T00:52:00Z"),
        timeOutAt = null,
        timeInProjectName = "ABC Building Project",
        timeOutProjectName = null,
        totalMinutes = null
    )

    private fun complete() = working().copy(
        status = AttendanceStatus.COMPLETE,
        timeOutAt = Instant.parse("2026-09-11T09:10:00Z")
    )

    private val homeNoRecord = DashboardUiState(loading = false, record = null)

    // ── the extra ──────────────────────────────────────────────────

    @Test
    fun `the extra names a direction exactly`() {
        assertEquals(TimeDirection.IN, startFlowFromExtra("IN"))
        assertEquals(TimeDirection.OUT, startFlowFromExtra("OUT"))
    }

    @Test
    fun `anything else is no request`() {
        assertNull(startFlowFromExtra(null))
        assertNull(startFlowFromExtra(""))
        assertNull(startFlowFromExtra("in"))
        assertNull(startFlowFromExtra("SIDEWAYS"))
    }

    // ── the gate ───────────────────────────────────────────────────

    @Test
    fun `still loading waits`() {
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, AppState.Loading, flowOpen = false, home = null)
        )
    }

    @Test
    fun `signed out, terms, or the offline gate drop the request`() {
        listOf(
            AppState.SignedOut,
            AppState.NeedsTerms(worker),
            AppState.GateUnavailable(worker)
        ).forEach { state ->
            assertEquals(
                StartFlowDecision.Drop,
                resolveStartFlow(TimeDirection.IN, state, flowOpen = false, home = homeNoRecord)
            )
        }
    }

    @Test
    fun `a flow already open is never replaced`() {
        assertEquals(
            StartFlowDecision.Drop,
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = true, home = homeNoRecord)
        )
    }

    // ── Home decides ───────────────────────────────────────────────

    @Test
    fun `Home not read yet waits`() {
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = null)
        )
        assertEquals(
            StartFlowDecision.Wait,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = DashboardUiState(loading = true))
        )
    }

    @Test
    fun `Time In on a fresh day opens the flow`() {
        assertEquals(
            StartFlowDecision.Open(TimeDirection.IN),
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = homeNoRecord)
        )
    }

    @Test
    fun `Time Out on an open day opens the flow`() {
        val home = DashboardUiState(loading = false, record = working())

        assertEquals(
            StartFlowDecision.Open(TimeDirection.OUT),
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = false, home = home)
        )
    }

    @Test
    fun `a stale Time In on an open day stays on Home`() {
        // The widget still said Time In; the day has already started. A
        // photo taken now would only be refused as ALREADY_TIMED_IN.
        val home = DashboardUiState(loading = false, record = working())

        assertEquals(
            StartFlowDecision.StayOnHome,
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = home)
        )
    }

    @Test
    fun `a stale Time Out on a fresh day stays on Home`() {
        assertEquals(
            StartFlowDecision.StayOnHome,
            resolveStartFlow(TimeDirection.OUT, signedIn, flowOpen = false, home = homeNoRecord)
        )
    }

    @Test
    fun `a closed day stays on Home whatever was asked`() {
        val home = DashboardUiState(loading = false, record = complete())

        TimeDirection.entries.forEach { request ->
            assertEquals(
                StartFlowDecision.StayOnHome,
                resolveStartFlow(request, signedIn, flowOpen = false, home = home)
            )
        }
    }

    @Test
    fun `offline with nothing mirrored still opens Time In, as Home does`() {
        val home = DashboardUiState(loading = false, record = null, failure = AttendanceFailure.NoConnection)

        assertEquals(
            StartFlowDecision.Open(TimeDirection.IN),
            resolveStartFlow(TimeDirection.IN, signedIn, flowOpen = false, home = home)
        )
    }
}
