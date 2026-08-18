package com.dacs.attendance.domain

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the app does on launch when a session is already on the device.
 *
 * The hard case is the one that happens every morning: a worker opens the
 * app at 07:45 on a site with no signal. The Terms acceptance lives on the
 * server, so the gate cannot be re-checked. Blocking them there would
 * break the one thing the app exists to do.
 */
class StartupGateTest {

    private val current = "2026-08-v1"

    @Test
    fun `a fresh acceptance from the server lets the worker in`() {
        assertEquals(
            StartupGate.Decision.Ready,
            StartupGate.decide(Result.success(setOf(current)), cachedVersion = null, currentVersion = current)
        )
    }

    @Test
    fun `the server saying they have never accepted shows the Terms`() {
        assertEquals(
            StartupGate.Decision.MustAccept,
            StartupGate.decide(Result.success(emptySet()), cachedVersion = null, currentVersion = current)
        )
    }

    @Test
    fun `the server wins over a stale cache`() {
        // If this device cached an acceptance that the server does not
        // have -- a wiped row, a restored backup, a different account --
        // the server is the truth.
        assertEquals(
            StartupGate.Decision.MustAccept,
            StartupGate.decide(Result.success(emptySet()), cachedVersion = current, currentVersion = current)
        )
    }

    @Test
    fun `offline with a matching cached acceptance lets the worker in`() {
        assertEquals(
            StartupGate.Decision.Ready,
            StartupGate.decide(Result.failure(IOException("no signal")), cachedVersion = current, currentVersion = current)
        )
    }

    @Test
    fun `offline with no cached acceptance cannot decide and must not guess`() {
        // Never show the Terms as though the worker had not accepted --
        // accepting again offline cannot be recorded, so it would be a
        // dead end. Tell them the truth instead.
        assertEquals(
            StartupGate.Decision.Unavailable,
            StartupGate.decide(Result.failure(IOException("no signal")), cachedVersion = null, currentVersion = current)
        )
    }

    @Test
    fun `offline with an outdated cached acceptance cannot decide either`() {
        assertEquals(
            StartupGate.Decision.Unavailable,
            StartupGate.decide(Result.failure(IOException("no signal")), cachedVersion = "2026-01-v0", currentVersion = current)
        )
    }
}
