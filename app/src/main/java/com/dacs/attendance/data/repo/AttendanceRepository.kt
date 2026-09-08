package com.dacs.attendance.data.repo

import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.ProjectSystem
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
    /**
     * WHICH list [projectId] belongs to. Since 0059 the id alone does not
     * identify a project -- 'pc' ids live in folders and 'pm' ids in
     * construction_projects -- so both RPCs take the pair.
     */
    val projectSystem: ProjectSystem,
    val projectId: String,
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

    /**
     * Every record between two work dates, inclusive.
     *
     * Serves the mirror when the server cannot be reached: History is one
     * of the two screens the spec names as having to render with no
     * signal, because a worker checking whether last Tuesday was recorded
     * is often standing somewhere without bars.
     */
    suspend fun history(fromWorkDate: String, toWorkDate: String): Result<List<AttendanceRecord>>
}

interface ProjectRepository {
    /** The active projects of the worker's owner, for the picker. */
    suspend fun activeProjects(): Result<List<AttendanceProject>>
}
