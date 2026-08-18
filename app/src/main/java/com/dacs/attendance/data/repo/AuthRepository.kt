package com.dacs.attendance.data.repo

import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile

/**
 * Signing in, and the identity that comes back with it.
 *
 * An interface rather than the Supabase class directly, so the login
 * state machine can be tested without a network -- the login screen is
 * the one screen every worker sees every time the app is reinstalled.
 */
interface AuthRepository {

    /**
     * Signs in and resolves the worker's profile.
     *
     * Fails with [LoginRejected] carrying the reason the server gave --
     * wrong password, deactivated account, not a worker account, or too
     * many attempts. Those decisions are made server-side, so a refused
     * account never receives a session at all.
     */
    suspend fun signIn(email: String, password: String): Result<WorkerProfile>

    suspend fun signOut()

    /** The persisted session's worker, or null when nobody is signed in. */
    suspend fun currentWorker(): WorkerProfile?
}

/** A sign-in refused for a reason the worker can be told about. */
class LoginRejected(val failure: LoginFailure) : Exception(failure.name)
