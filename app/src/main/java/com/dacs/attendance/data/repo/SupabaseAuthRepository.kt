package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.ProfileRow
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
    private val signInApi: SignInApi
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
                    worker
                }
            }
        }

    override suspend fun signOut() {
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

    override suspend fun currentWorker(): WorkerProfile? =
        runCatchingExceptCancellation {
            // The Auth plugin restores the stored session asynchronously.
            // Asking before it settles reports "signed out" for a worker
            // who is not -- and sends them back to a login screen every
            // morning, which is the one thing session persistence exists
            // to prevent.
            client.auth.awaitInitialization()
            loadWorkerProfile()
        }.getOrNull()

    /**
     * Used on launch, when a session has been restored from encrypted
     * storage. Reads the worker's own profiles row under their own RLS --
     * no service_role involved -- so an account deactivated since the last
     * launch comes back as 'inactive' rather than silently staying in.
     */
    private suspend fun loadWorkerProfile(): WorkerProfile? {
        val userId = client.auth.currentUserOrNull()?.id ?: return null
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
