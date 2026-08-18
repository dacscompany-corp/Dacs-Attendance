package com.dacs.attendance.domain

/**
 * What to show a worker who already has a session on the device.
 *
 * The Terms acceptance lives on the server, so this has to cope with the
 * app being opened at 07:45 on a site with no signal. The rule is: trust
 * the server when it answers, fall back to what this device recorded when
 * it does not, and say so plainly when neither can decide -- never guess
 * "not accepted", because accepting again offline cannot be written
 * anywhere and would be a dead end.
 */
object StartupGate {

    enum class Decision {
        /** Straight to the dashboard. */
        Ready,

        /** Show the Terms screen. */
        MustAccept,

        /** No signal and nothing cached: tell the worker, offer retry. */
        Unavailable
    }

    fun decide(
        acceptedVersions: Result<Set<String>>,
        cachedVersion: String?,
        currentVersion: String
    ): Decision = acceptedVersions.fold(
        onSuccess = { versions ->
            // The server is the truth whenever it answers -- a cached
            // acceptance the server does not have (wiped row, restored
            // backup, different account) does not count.
            when (TermsGate.decide(versions, currentVersion)) {
                TermsGate.Decision.AlreadyAccepted -> Decision.Ready
                TermsGate.Decision.MustAccept -> Decision.MustAccept
            }
        },
        onFailure = {
            if (cachedVersion == currentVersion) Decision.Ready else Decision.Unavailable
        }
    )
}
