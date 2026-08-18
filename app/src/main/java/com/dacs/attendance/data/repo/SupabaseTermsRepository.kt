package com.dacs.attendance.data.repo

import android.os.Build
import com.dacs.attendance.BuildConfig
import com.dacs.attendance.data.remote.AgreementEventRow
import com.dacs.attendance.data.remote.TermsAcceptanceRow
import com.dacs.attendance.data.remote.TermsVersionRow
import com.dacs.attendance.domain.AttendanceTerms
import com.dacs.attendance.domain.WorkerProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseTermsRepository @Inject constructor(
    private val client: SupabaseClient
) : TermsRepository {

    override suspend fun acceptedVersions(workerId: String): Result<Set<String>> =
        runCatchingExceptCancellation {
            client.postgrest
                .from("attendance_terms_acceptances")
                .select(Columns.raw("terms_version")) {
                    filter { eq("worker_id", workerId) }
                }
                .decodeList<TermsVersionRow>()
                .map { it.termsVersion }
                .toSet()
        }

    /**
     * Two rows, in this order, and the order is the point.
     *
     * The EVIDENCE goes first (agreement_events, 0021: append-only even to
     * service_role), then the flag the gate reads. If the second write
     * fails, the worker is asked to accept again and we end up with a
     * duplicate evidence row -- harmless in an append-only log. Written
     * the other way round, a failure leaves a worker marked as having
     * accepted with no record of what they accepted, and that is not
     * recoverable.
     */
    override suspend fun accept(worker: WorkerProfile): Result<Unit> =
        runCatchingExceptCancellation {
            client.postgrest.from("agreement_events").insert(
                AgreementEventRow(
                    userId = worker.id,
                    email = worker.email,
                    audience = AUDIENCE,
                    docType = DOC_TYPE,
                    docTitle = AttendanceTerms.TITLE,
                    docSha256 = AttendanceTerms.sha256(),
                    docText = AttendanceTerms.canonicalText(),
                    userAgent = userAgent()
                )
            )

            try {
                client.postgrest.from("attendance_terms_acceptances").insert(
                    TermsAcceptanceRow(
                        workerId = worker.id,
                        termsVersion = AttendanceTerms.VERSION
                    )
                )
            } catch (error: Exception) {
                // unique (worker_id, terms_version): the worker already
                // accepted this version -- most likely a retry after a
                // dropped response. That is the desired end state, so it
                // is not an error to report.
                if (!error.isUniqueViolation()) throw error
            }
        }

    private fun userAgent(): String =
        "DacsAttendance/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE}; " +
            "${Build.MANUFACTURER} ${Build.MODEL})"

    private companion object {
        const val AUDIENCE = "worker"
        const val DOC_TYPE = "attendance_terms"
    }
}

private fun Throwable.isUniqueViolation(): Boolean {
    val text = (message ?: "") + " " + (cause?.message ?: "")
    return text.contains("23505") || text.contains("duplicate key", ignoreCase = true)
}
