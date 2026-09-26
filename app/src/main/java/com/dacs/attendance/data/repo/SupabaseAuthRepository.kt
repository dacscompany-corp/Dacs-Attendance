package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.ProfileRow
import com.dacs.attendance.data.local.WorkerCache
import com.dacs.attendance.data.remote.SignInApi
import com.dacs.attendance.data.remote.SignInOutcome
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.SessionGate
import com.dacs.attendance.domain.SessionPresence
import com.dacs.attendance.domain.WorkerProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
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
        //
        // The cached id is the fallback because offline the client can
        // report nobody for a session it holds. Without it, a sign-out
        // with no signal left lastSignedInId standing -- and currentWorker()
        // and the home-screen widget both kept resolving the worker who
        // had just signed out.
        (client.auth.currentUserOrNull()?.id ?: workerCache.lastSignedInId)
            ?.let(workerCache::forget)
        runCatchingExceptCancellation { client.auth.signOut() }
        // signOut() only reaches clearSession() if the server call
        // succeeds, and offline it throws first -- the POST to `logout`
        // is a plain network exception, not a RestException, so it
        // propagates before the session is cleared. The local session has
        // to go regardless, because the widget resolves its worker from
        // currentUserOrNull() and a session outliving its sign-out is the
        // last worker's day on a shared phone's home screen.
        runCatchingExceptCancellation { client.auth.clearSession() }
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
     * ── "NO SESSION", "NO SIGNAL" AND "NOT ASKED AS ANYONE" ARE THREE
     *    DIFFERENT ANSWERS.
     *
     *    This used to wrap the whole thing in runCatching and return null
     *    on any failure, which collapsed the first two into one. Offline,
     *    the profile read threw, null came back, and the app concluded the
     *    worker was signed out -- dropping them on a login screen they
     *    could not complete without signal either.
     *
     *    That defeated the entire offline layer: the queue, the record
     *    mirror and the cached project picker all sit behind this check,
     *    and every one of them exists for the morning it locked people
     *    out of.
     *
     *    Separating those two left a THIRD answer misread, and it is the
     *    one that signed workers out of an app they never left. See
     *    [SessionGate]: a read made with no session does not fail, it
     *    succeeds as the anon role and returns nothing, and "nothing" was
     *    being believed. So the session is now settled BEFORE anything is
     *    read, and the read only happens when there is a token to make it
     *    with.
     */
    override suspend fun currentWorker(): WorkerProfile? {
        // The Auth plugin restores the stored session asynchronously.
        // Asking before it settles reports "signed out" for a worker who
        // is not -- the one thing session persistence exists to prevent.
        client.auth.awaitInitialization()

        val lastKnownId = workerCache.lastSignedInId
        val decision = SessionGate.decide(
            presence = sessionPresence(),
            hasLastKnownWorker = lastKnownId != null
        )

        return when (decision) {
            // Confirmed session: the read below carries this worker's own
            // token, so PostgREST answers as them and the answer -- row,
            // or no row -- is the truth.
            SessionGate.Decision.AskTheServer ->
                resolveFromServer(client.auth.currentUserOrNull()?.id ?: lastKnownId ?: return null)

            // Measured on a real device, offline: the session IS restored
            // and the branch above returns the worker. This one is for
            // when the client cannot describe what it loaded -- no
            // signal, or a refresh that failed and has not retried yet.
            SessionGate.Decision.UseLastKnown -> lastKnownId?.let(workerCache::recall)

            SessionGate.Decision.SignedOut -> null
        }
    }

    /**
     * The worker as the server has them, falling back to the last profile
     * this device saw if the read cannot be made.
     */
    private suspend fun resolveFromServer(userId: String): WorkerProfile? =
        runCatchingExceptCancellation { loadWorkerProfile(userId) }.fold(
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
                //
                // It also catches an access token the server rejects
                // (401) while the client still believes in it -- a phone
                // with a badly wrong clock. Same treatment: that is not
                // the worker signing out.
                workerCache.recall(userId)
            }
        )

    /**
     * What the auth client can currently say about the stored session.
     *
     * [SessionStatus.RefreshFailure] is the state this whole mechanism
     * turns on: the client HAS a session, a refresh of it failed, and it
     * waits ten seconds before trying again. Throughout that window
     * `currentUserOrNull()` is null while the refresh token sits safely on
     * disk. It is not a sign-out and must never be read as one.
     */
    private fun sessionPresence(): SessionPresence =
        when (client.auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> SessionPresence.Confirmed
            is SessionStatus.RefreshFailure -> SessionPresence.Unverifiable
            // awaitInitialization() above means this should not be seen.
            // If it ever is, the safe reading is "cannot tell yet", never
            // "signed out".
            is SessionStatus.Initializing -> SessionPresence.Unverifiable
            is SessionStatus.NotAuthenticated -> SessionPresence.Absent
        }

    /**
     * Used on launch, when a session has been restored from encrypted
     * storage. Reads the worker's own profiles row under their own RLS --
     * no service_role involved -- so an account deactivated since the last
     * launch comes back as 'inactive' rather than silently staying in.
     *
     * REFUSES TO RUN WITHOUT A SESSION, like every other read that is
     * scoped to one worker (see RewardRepository.weekProgress). Without
     * this the Supabase client quietly substitutes the anon key, RLS on
     * `profiles` (`auth.uid() IS NOT NULL`) returns an empty result rather
     * than an error, and an unauthenticated read looks exactly like a
     * worker whose row does not exist.
     */
    private suspend fun loadWorkerProfile(userId: String): WorkerProfile? {
        client.requireSession()

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
