package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.ProfileRow
import com.dacs.attendance.domain.Eligibility
import com.dacs.attendance.domain.LoginFailure
import com.dacs.attendance.domain.WorkerProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val client: SupabaseClient
) : AuthRepository {

    override suspend fun signIn(
        email: String,
        password: String,
        captchaToken: String?
    ): Result<WorkerProfile> =
        runCatchingExceptCancellation {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = password
                this.captchaToken = captchaToken
            }

            val worker = loadWorkerProfile()
                ?: throw LoginRejected(LoginFailure.NotAWorker)

            when (worker.eligibility()) {
                Eligibility.Allowed -> worker
                // A refused account must not be left holding a live
                // session on the device: the RPCs would reject it anyway,
                // but a signed-in worker who can never time in is a
                // support call, not a security boundary.
                Eligibility.AccountInactive -> {
                    signOut()
                    throw LoginRejected(LoginFailure.AccountInactive)
                }
                Eligibility.NotAWorker -> {
                    signOut()
                    throw LoginRejected(LoginFailure.NotAWorker)
                }
            }
        }

    override suspend fun signOut() {
        runCatchingExceptCancellation { client.auth.signOut() }
    }

    override suspend fun currentWorker(): WorkerProfile? =
        runCatchingExceptCancellation { loadWorkerProfile() }.getOrNull()

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
