package com.dacs.attendance.domain

/**
 * The worker's profiles row, as the app needs it. Mirrors exactly what
 * attendance_time_in reads before it will write anything (0050 section 8).
 */
data class WorkerProfile(
    val id: String,
    val email: String?,
    val displayName: String?,
    val position: String?,
    val workerNo: Int?,
    val role: String?,
    val status: String?
) {
    /** "W-0042" -- the format the design's Profile screen shows. */
    val workerIdLabel: String
        get() = workerNo?.let { "W-%04d".format(it) } ?: "--"

    /**
     * "JD" for Juan dela Cruz -- the avatar on the dashboard and the
     * profile. First letters of the first two words, so the two screens
     * cannot disagree about who this is.
     */
    val initials: String
        get() {
            val parts = (displayName ?: firstName).trim()
                .split(" ")
                .filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(1).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }

    /** "Mason · W-0042" -- the line under the name on both screens. */
    val positionAndId: String
        get() = listOfNotNull(position?.takeIf { it.isNotBlank() }, workerIdLabel)
            .joinToString(" · ")

    /** First name only: the dashboard greets "Magandang umaga, Juan". */
    val firstName: String
        get() = displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() }
            ?: email?.substringBefore('@')
            ?: "Worker"

    fun eligibility(): Eligibility = eligibilityOf(role, status)
}

enum class Eligibility { Allowed, AccountInactive, NotAWorker }

/**
 * The same rule the RPCs enforce, applied at LOGIN so a deactivated
 * worker is told at the door rather than after walking four screens to
 * SUBMIT. The database stays the authority -- this is the message, not
 * the security.
 */
fun eligibilityOf(role: String?, status: String?): Eligibility = when {
    role !in WORKER_ROLES -> Eligibility.NotAWorker
    // SQL says coalesce(p.status,'active'). Older profiles rows carry no
    // status at all, and locking those workers out would be a bug the RPC
    // does not have.
    (status ?: "active") != "active" -> Eligibility.AccountInactive
    else -> Eligibility.Allowed
}

private val WORKER_ROLES = setOf("worker", "teamLeader")
