package com.dacs.attendance.domain

/**
 * What the auth client can currently say about the session on this device.
 *
 * Three answers, not two, and the third is the whole point. "I have no
 * session" and "I have a session I cannot check right now" are different
 * facts, and only the first one means the worker is signed out.
 */
enum class SessionPresence {

    /** A session the client can describe. Reads run as this worker. */
    Confirmed,

    /**
     * A session IS on the device, but the client cannot validate it at
     * this moment -- a token refresh that failed and has not yet retried.
     * A statement about the network, never about whether they signed in.
     */
    Unverifiable,

    /** No session on this device: signed out, or the client cleared it. */
    Absent
}

/**
 * Whether a worker with a session on the device is still signed in.
 *
 * ── WHY THIS IS A GATE AND NOT AN `?:` CHAIN.
 *
 *    Workers were being dropped on the login screen having never signed
 *    out. The chain that did it read like this: ask the client who is
 *    signed in, fall back to the id we recorded at sign-in, then read
 *    that worker's `profiles` row and trust what comes back.
 *
 *    The flaw was in the last step. When the client holds a session it
 *    cannot currently validate -- one failed token refresh, and it waits
 *    ten seconds before retrying -- it reports nobody, and the Supabase
 *    client then sends the profile read with the ANON key instead. RLS on
 *    `profiles` is `auth.uid() IS NOT NULL`, and an unauthenticated
 *    SELECT under that policy is not an error: it is a perfectly
 *    successful read of zero rows. So the app asked "who is this worker"
 *    without credentials, was told "nobody", and believed it -- while the
 *    refresh token sat on disk the whole time.
 *
 *    That is why the presence of a session is decided BEFORE anything is
 *    read, and why [Decision.AskTheServer] is only ever reached with a
 *    token in hand. A read made as the worker can be trusted; a read made
 *    as nobody cannot, and must not be made at all.
 *
 * ── THE SAME RULE THE OFFLINE LAYER ALREADY LIVES BY. [StartupGate]
 *    trusts the server when it answers and falls back to what the device
 *    recorded when it does not. This is that rule, applied one step
 *    earlier -- to the question of who the worker is, rather than what
 *    they have accepted.
 */
object SessionGate {

    enum class Decision {
        /** Read the profile as this worker. Its answer is authoritative. */
        AskTheServer,

        /** Cannot verify: stand on the last profile this device saw. */
        UseLastKnown,

        /** Nothing to resolve. The login screen is the correct place. */
        SignedOut
    }

    fun decide(presence: SessionPresence, hasLastKnownWorker: Boolean): Decision =
        when (presence) {
            SessionPresence.Confirmed -> Decision.AskTheServer

            // A session we cannot describe is still a session. Offline
            // this is every morning on site; online it is the ten-second
            // window after a refresh that failed.
            SessionPresence.Unverifiable ->
                if (hasLastKnownWorker) Decision.UseLastKnown else Decision.SignedOut

            // No session at all -- either they signed out, or the client
            // cleared it because the refresh token was revoked. A cached
            // profile does not survive that: there is no session left to
            // act with, so showing the dashboard would show one where
            // every action fails.
            SessionPresence.Absent -> Decision.SignedOut
        }
}
