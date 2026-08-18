package com.dacs.attendance.data.remote

import com.dacs.attendance.domain.WorkerProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes. Column names are snake_case here and camelCase nowhere --
 * the web's shim does that translation for the browser, the app talks to
 * PostgREST directly and so speaks the database's own names.
 */
@Serializable
data class ProfileRow(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val position: String? = null,
    @SerialName("worker_no") val workerNo: Int? = null,
    val role: String? = null,
    val status: String? = null
) {
    fun toDomain() = WorkerProfile(
        id = id,
        email = email,
        displayName = displayName,
        position = position,
        workerNo = workerNo,
        role = role,
        status = status
    )

    companion object {
        const val COLUMNS = "id,email,display_name,position,worker_no,role,status"
    }
}

@Serializable
data class TermsAcceptanceRow(
    @SerialName("worker_id") val workerId: String,
    @SerialName("terms_version") val termsVersion: String
)

@Serializable
data class TermsVersionRow(
    @SerialName("terms_version") val termsVersion: String
)

/**
 * The evidence row (0021). Append-only even to service_role.
 *
 * `ip` is deliberately absent: the device cannot know its public address,
 * and a client-supplied one would be evidence of nothing. The audit trail
 * carries what the device can honestly attest to -- the exact text, its
 * hash, and the app build that showed it.
 */
@Serializable
data class AgreementEventRow(
    @SerialName("user_id") val userId: String,
    val email: String? = null,
    val audience: String,
    @SerialName("doc_type") val docType: String,
    @SerialName("doc_title") val docTitle: String,
    @SerialName("doc_sha256") val docSha256: String,
    @SerialName("doc_text") val docText: String,
    @SerialName("user_agent") val userAgent: String
)
