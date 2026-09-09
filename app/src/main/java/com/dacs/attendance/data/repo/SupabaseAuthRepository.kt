package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.ProfileRow
import com.dacs.attendance.data.local.WorkerCache
import com.dacs.attendance.data.remote.SignInApi
import com.dacs.attendance.data.remote.SignInOutcome
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

private const val AUDIENCE = "authenticated"

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient,
    private val signInApi: SignInApi,
    private val workerCache: WorkerCache
) : AuthRepository {

    /**
     * Sign-in runs inside the `attendance-signin` Edge Function, which
     * checks the credentials AND the worker's eligibility before handing
     * back any tokens. What arrives here is therefore either a session
     * belonging to an active worker or a refusal -- there is no state in
     * between where the device holds a session it should not have.
     *
     * The session is then imported into the Supabase client, so every
     * later PostgREST call runs as that worker under their own RLS and the
     * session persists to encrypted storage like any other.
     */
    override suspend fun signIn(email: String, password: String): Result<WorkerProfile> =
        runCatchingExceptCancellation {
            when (val outcome = signInApi.signIn(email, password)) {
                is SignInOutcome.Refused -> throw LoginRejected(LoginFailure.forCode(outcome.code))

                is SignInOutcome.Success -> {
                    val session = outcome.body.session
                    val worker = outcome.body.worker.toDomain()
                    client.auth.importSession(
                        UserSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            expiresIn = session.expiresIn,
                            tokenType = session.tokenType,
                            // The user MUST be attached. It is what
                            // currentUserOrNull() returns, and that id is
                            // both the storage path prefix every photo
                            // upload is authorised against and the way a
                            // restored session identifies its worker. An
                            // imported session without it looks signed in
                            // to PostgREST and signed out to the app.
                            user = UserInfo(id = worker.id, aud = AUDIENCE)
                        )
                    )

                    // CACHED HERE, not only in currentWorker(). Sign-in is
                    // the one moment a worker is guaranteed to have signal,
                    // and it is also the only path that never calls
                    // currentWorker() -- so without this the cache stays
                    // empty until the second launch, and the FIRST offline
                    // launch after signing in still lands on the login
                    // screen with nothing to fall back to. Which is
                    // precisely the morning this whole mechanism is for.
                    workerCache.remember(worker)
                    worker
                }
            }
        }

    override suspend fun signOut() {
        // Cleared BEFORE the session goes, while the id is still readable.
        // Site phones are shared, and a cached profile outliving its
        // session would greet the next worker with the last one's name.
        client.auth.currentUserOrNull()?.id?.let(workerCache::forget)
        runCatchingExceptCancellation { client.auth.signOut() }
    }

    /**
     * The session stays valid afterwards: GoTrue reissues tokens for the
     * same user on a password update. The worker is NOT signed out, which
     * matters because they may be mid-shift with a queued Time In that
     * still has to upload under this session.
     */
    override suspend fun changePassword(newPassword: String): Result<Unit> =
        runCatchingExceptCancellation {
            client.auth.awaitInitialization()
            client.auth.updateUser { password = newPassword }
            Unit
        }

    /**
     * ── "NO SESSION" AND "NO SIGNAL" ARE DIFFERENT ANSWERS.
     *
     *    This used to wrap the whole thing in runCatching and return null
     *    on any failure, which collapsed the two into one. Offline, the
     *    profile read threw, null came back, and the app concluded the
     *    worker was signed out -- dropping them on a login screen they
     *    could not complete without signal either.
     *
     *    That defeated the entire offline layer: the queue, the record
     *    mirror and the cached project picker all sit behind this check,
     *    and every one of them exists for the morning it locked people
     *    out of.
     *
     *    So the session is read first, on its own. Only a genuinely
     *    absent session means signed out; a session that cannot be
     *    *described* falls back to the last profile this device saw.
     */
    override suspend fun currentWorker(): WorkerProfile? {
        // The Auth plugin restores the stored session asynchronously.
        // Asking before it settles reports "signed out" for a worker who
        // is not -- the one thing session persistence exists to prevent.
        client.auth.awaitInitialization()

        // ── WHY THIS DOES NOT TRUST currentUserOrNull() ALONE.
        //
        //    Verified on a real device: reopening the app WITH signal
        //    restores the session and lands on the dashboard, and
        //    reopening WITHOUT signal lands on the login screen. So the
        //    session persists correctly -- the auth client simply reports
        //    nobody when it cannot reach the network to validate or
        //    refresh what it loaded.
        //
        //    That is a fact about the network. Reading it as "signed out"
        //    is what locked workers out of the entire offline layer.
        //
        //    So: the client's answer wins when it has one, and our own
        //    record of the sign-in we witnessed stands in when it does
        //    not. A worker who actually signed out has neither.
        // Measured on a real device, offline: the session IS restored and
        // this returns the worker. The fallback below is defence for the
        // case where it does not -- it was not what fixed the offline
        // lockout, and assuming it was cost three wrong attempts.
        val userId = client.auth.currentUserOrNull()?.id
            ?: workerCache.lastSignedInId
            ?: return null

        return runCatchingExceptCancellation { loadWorkerProfile(userId) }.fold(
            onSuccess = { profile ->
                // The server answered. Its answer wins, and is kept for
                // the next launch that has no signal.
                profile?.also { workerCache.remember(it) }
            },
            onFailure = {
                // THE ACTUAL OFFLINE BUG LIVED HERE. This read throws
                // HttpRequestException with no signal, the original code
                // swallowed it to null, and RootViewModel read null as
                // "signed out" -- locking a worker out of the queue, the
                // mirrors and the cached picker, all of which exist for
                // precisely that morning.
                workerCache.recall(userId)
            }
        )
    }

    /**
     * Used on launch, when a session has been restored from encrypted
     * storage. Reads the worker's own profiles row under their own RLS --
     * no service_role involved -- so an account deactivated since the last
     * launch comes back as 'inactive' rather than silently staying in.
     */
    private suspend fun loadWorkerProfile(userId: String): WorkerProfile? {
        return client.postgrest
            .from("profiles")
            .select(Columns.raw(ProfileRow.COLUMNS)) {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeSingleOrNull<ProfileRow>()
            ?.toDomain()
    }
}
