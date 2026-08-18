package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.ProfileRow
import com.dacs.attendance.data.remote.SignInApi
import com.dacs.attendance.data.remote.SignInOutcome
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

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
                    client.auth.importSession(
                        UserSession(
                            accessToken = session.accessToken,
                            refreshToken = session.refreshToken,
                            expiresIn = session.expiresIn,
                            tokenType = session.tokenType
                        )
                    )
                    outcome.body.worker.toDomain()
                }
            }
        }

    override suspend fun signOut() {
        runCatchingExceptCancellation { client.auth.signOut() }
    }

    override suspend fun currentWorker(): WorkerProfile? =
        runCatchingExceptCancellation { loadWorkerProfile() }.getOrNull()

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
