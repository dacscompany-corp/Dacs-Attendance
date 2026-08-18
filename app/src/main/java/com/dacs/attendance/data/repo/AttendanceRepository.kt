package com.dacs.attendance.data.repo

import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.TimeDirection
import java.io.File
import java.time.Instant

/**
 * One submission: everything needed to record a Time In or Time Out.
 *
 * [eventId] is generated once, when the worker first taps SUBMIT, and
 * kept for every retry of THAT submission. The RPCs are idempotent on it,
 * so replaying returns the existing row instead of writing a second one.
 * B4's offline queue persists exactly this object.
 *
 * [capturedAt] is when the SHUTTER fired, not when the request was sent.
 * The photo is the evidence of when the worker was there; sending the
 * upload time would overstate a Time In made after walking back into
 * signal.
 */
data class SubmissionRequest(
    val direction: TimeDirection,
    val projectId: Long,
    val capturedAt: Instant,
    val photo: File,
    val description: String?,
    val eventId: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMetres: Double? = null,
    val wasOffline: Boolean = false
)

interface AttendanceRepository {

    /** Uploads the photo, then calls the matching RPC. */
    suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord>

    /** Today's record for this worker, or null if they have not timed in. */
    suspend fun today(): Result<AttendanceRecord?>
}

interface ProjectRepository {
    /** The active projects of the worker's owner, for the picker. */
    suspend fun activeProjects(): Result<List<AttendanceProject>>
}
