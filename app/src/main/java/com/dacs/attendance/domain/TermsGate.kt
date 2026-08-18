package com.dacs.attendance.domain

/**
 * Decides whether a signed-in worker sees the Terms screen.
 *
 * Deliberately a pure function over the acceptance rows. This gate is the
 * one thing standing between a worker and the app, so it should be
 * provable without a network or a database.
 */
object TermsGate {

    enum class Decision { MustAccept, AlreadyAccepted }

    fun decide(acceptedVersions: Set<String>, currentVersion: String): Decision =
        if (currentVersion in acceptedVersions) Decision.AlreadyAccepted else Decision.MustAccept
}
