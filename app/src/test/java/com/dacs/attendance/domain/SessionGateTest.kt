package com.dacs.attendance.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whether a worker with a session on the device is still signed in.
 *
 * The bug this exists to prevent: workers were being dropped on the login
 * screen without having signed out. A token refresh that fails once --
 * one blip of signal at 07:45, one 5xx from GoTrue -- leaves the auth
 * client holding a session it cannot currently VALIDATE, and for the ten
 * seconds before it retries it reports nobody. The profile read then went
 * out with the anon key, RLS answered it with an empty result rather than
 * an error, and an empty result was read as "no such worker".
 *
 * So the rule under test: only a device with NO session is signed out. A
 * session that merely cannot be confirmed right now falls back to the last
 * profile this device saw, exactly as an offline launch does.
 */
class SessionGateTest {

    @Test
    fun `a confirmed session asks the server who this is`() {
        // The read runs under the worker's own token, so whatever it says
        // -- including that the row is gone -- is the truth.
        assertEquals(
            SessionGate.Decision.AskTheServer,
            SessionGate.decide(SessionPresence.Confirmed, hasLastKnownWorker = true)
        )
    }

    @Test
    fun `a confirmed session asks the server even with nothing cached`() {
        assertEquals(
            SessionGate.Decision.AskTheServer,
            SessionGate.decide(SessionPresence.Confirmed, hasLastKnownWorker = false)
        )
    }

    @Test
    fun `a session that cannot be verified keeps the worker signed in`() {
        // THE REGRESSION. This is the failed-refresh window, and it used
        // to end at the login screen with the refresh token still sitting
        // on disk -- a worker signed out of an app they never left.
        assertEquals(
            SessionGate.Decision.UseLastKnown,
            SessionGate.decide(SessionPresence.Unverifiable, hasLastKnownWorker = true)
        )
    }

    @Test
    fun `no session on the device means signed out`() {
        assertEquals(
            SessionGate.Decision.SignedOut,
            SessionGate.decide(SessionPresence.Absent, hasLastKnownWorker = false)
        )
    }

    @Test
    fun `a cleared session means signed out even if a profile is still cached`() {
        // Sign-out clears the cache before the session, but a session the
        // client cleared ITSELF -- a revoked refresh token -- leaves the
        // cache standing. That worker has to sign in again: there is no
        // session left to do anything with, and pretending otherwise puts
        // them on a dashboard where every action fails.
        assertEquals(
            SessionGate.Decision.SignedOut,
            SessionGate.decide(SessionPresence.Absent, hasLastKnownWorker = true)
        )
    }

    @Test
    fun `an unverifiable session with nothing cached cannot be resolved`() {
        // Nothing to fall back to, so there is no worker to show. Rare:
        // it needs a session stored without this device ever having
        // recorded the sign-in that created it.
        assertEquals(
            SessionGate.Decision.SignedOut,
            SessionGate.decide(SessionPresence.Unverifiable, hasLastKnownWorker = false)
        )
    }
}
