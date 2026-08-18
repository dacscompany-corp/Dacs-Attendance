package com.dacs.attendance.domain

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The RPCs raise stable codes precisely so the app can say something
 * useful instead of showing a Postgres string to a man on scaffolding.
 * Every code 0050/0051 can raise is mapped here; an unmapped one is the
 * bug this test exists to catch.
 */
class AttendanceFailureTest {

    @Test
    fun `already timed in is its own message, not a generic error`() {
        // The commonest one in real use: the worker taps TIME IN twice
        // because the first tap had no visible effect on a slow phone.
        assertEquals(
            AttendanceFailure.AlreadyTimedIn,
            AttendanceFailure.of(rpcError("ALREADY_TIMED_IN"))
        )
    }

    @Test
    fun `timing out without timing in is its own message`() {
        assertEquals(
            AttendanceFailure.NotTimedIn,
            AttendanceFailure.of(rpcError("NOT_TIMED_IN"))
        )
    }

    @Test
    fun `the whole documented error surface is mapped`() {
        val expected = mapOf(
            "ALREADY_TIMED_IN" to AttendanceFailure.AlreadyTimedIn,
            "NOT_TIMED_IN" to AttendanceFailure.NotTimedIn,
            "ALREADY_COMPLETE" to AttendanceFailure.AlreadyComplete,
            "TIMEOUT_BEFORE_TIMEIN" to AttendanceFailure.TimeOutBeforeTimeIn,
            "CAPTURED_IN_FUTURE" to AttendanceFailure.DeviceClockWrong,
            "SHIFT_TOO_LONG" to AttendanceFailure.ShiftTooLong,
            "PROJECT_UNAVAILABLE" to AttendanceFailure.ProjectUnavailable,
            "NO_OWNER_ASSIGNED" to AttendanceFailure.NoOwnerAssigned,
            "ACCOUNT_INACTIVE" to AttendanceFailure.AccountInactive,
            "NOT_A_WORKER" to AttendanceFailure.NotAWorker,
            "AUTH_REQUIRED" to AttendanceFailure.SessionExpired,
            "EVENT_ID_CONFLICT" to AttendanceFailure.Unexpected,
            "EVENT_ID_REQUIRED" to AttendanceFailure.Unexpected
        )

        expected.forEach { (code, failure) ->
            assertEquals("code $code", failure, AttendanceFailure.of(rpcError(code)))
        }
    }

    @Test
    fun `no signal is never reported as a rejected submission`() {
        // The difference matters: one means "we saved nothing, try
        // again", the other means "the server refused this".
        assertEquals(AttendanceFailure.NoConnection, AttendanceFailure.of(IOException("dns")))
    }

    @Test
    fun `an unrecognised database error does not masquerade as a known one`() {
        assertEquals(
            AttendanceFailure.Unexpected,
            AttendanceFailure.of(rpcError("SOME_NEW_CODE_FROM_A_LATER_MIGRATION"))
        )
    }

    /** What PostgREST actually surfaces for `raise exception 'X'`. */
    private fun rpcError(code: String) =
        RuntimeException("""{"code":"P0001","message":"$code","details":null,"hint":null}""")
}
