package com.dacs.attendance.data.local

import android.content.Context
import com.dacs.attendance.domain.WorkerProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last profile row this device saw the server return, per worker.
 *
 * ── WHY THIS EXISTS. The session restores from encrypted storage with no
 *    network at all, but the PROFILE behind it was a live read of the
 *    `profiles` table. On a site with no signal that read failed, came
 *    back as null, and the app could not tell "this worker is signed out"
 *    from "the server is unreachable" -- so it sent them to the login
 *    screen.
 *
 *    Which broke the one promise the whole offline layer is built on. A
 *    worker at the gate with no bars could not reach the dashboard, could
 *    not start a flow, and could not reach the submission queue that
 *    exists for exactly that morning. The queue, the mirrors and the
 *    cached picker were all unreachable behind a login form they could
 *    not complete without signal either.
 *
 * ── A CACHE OF A SERVER FACT, NEVER THE SOURCE OF ONE. Same rule as
 *    [TermsCache]: whenever the server answers, its answer wins and this
 *    is overwritten.
 *
 * ── THE DEACTIVATION TRADE, stated plainly. The live read is also what
 *    catches an account switched off since the last launch. Falling back
 *    to this cache means a deactivated worker can still open the app
 *    while offline. That is accepted, and it is bounded: every write goes
 *    through an RPC that re-checks eligibility server-side, so their
 *    queued records are refused with ACCOUNT_INACTIVE on sync. They can
 *    walk through the flow; they cannot actually record anything.
 *
 *    The alternative -- locking every worker out whenever the signal
 *    drops, to catch the rare deactivated one -- is far worse, and it is
 *    the behaviour this class replaces.
 */
@Singleton
class WorkerCache @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs =
        context.getSharedPreferences("dacs_attendance_worker", Context.MODE_PRIVATE)

    /**
     * WHO is signed in, independent of whether the auth client can
     * currently confirm it.
     *
     * `currentUserOrNull()` answers null on an offline launch: the stored
     * session loads, cannot be validated or refreshed without a network,
     * and the client reports nobody. That is a statement about the
     * NETWORK, not about whether this worker signed in -- but every
     * caller read it as the latter, so the app decided they were signed
     * out and sent them to a login form they could not complete either.
     *
     * This is set at sign-in and cleared at sign-out, and nowhere else.
     * It is the app's own record of an event it witnessed.
     */
    var lastSignedInId: String?
        get() = prefs.getString("last_signed_in_id", null)
        private set(value) {
            prefs.edit().apply {
                if (value == null) remove("last_signed_in_id") else putString("last_signed_in_id", value)
            }.apply()
        }

    fun remember(worker: WorkerProfile) {
        lastSignedInId = worker.id
        prefs.edit()
            .putString(key(worker.id, "email"), worker.email)
            .putString(key(worker.id, "display_name"), worker.displayName)
            .putString(key(worker.id, "position"), worker.position)
            .putInt(key(worker.id, "worker_no"), worker.workerNo ?: -1)
            .putString(key(worker.id, "role"), worker.role)
            .putString(key(worker.id, "status"), worker.status)
            .putBoolean(key(worker.id, "present"), true)
            .apply()
    }

    /** Null when this device has never seen a profile for [workerId]. */
    fun recall(workerId: String): WorkerProfile? {
        if (!prefs.getBoolean(key(workerId, "present"), false)) return null
        return WorkerProfile(
            id = workerId,
            email = prefs.getString(key(workerId, "email"), null),
            displayName = prefs.getString(key(workerId, "display_name"), null),
            position = prefs.getString(key(workerId, "position"), null),
            workerNo = prefs.getInt(key(workerId, "worker_no"), -1).takeIf { it >= 0 },
            role = prefs.getString(key(workerId, "role"), null),
            status = prefs.getString(key(workerId, "status"), null)
        )
    }

    /**
     * Dropped on sign out. The next worker on a shared phone -- and site
     * phones ARE shared -- must never be met with the last one's name.
     */
    fun forget(workerId: String) {
        lastSignedInId = null
        prefs.edit()
            .remove(key(workerId, "email"))
            .remove(key(workerId, "display_name"))
            .remove(key(workerId, "position"))
            .remove(key(workerId, "worker_no"))
            .remove(key(workerId, "role"))
            .remove(key(workerId, "status"))
            .remove(key(workerId, "present"))
            .apply()
    }

    private fun key(workerId: String, field: String) = "worker_${workerId}_$field"
}
