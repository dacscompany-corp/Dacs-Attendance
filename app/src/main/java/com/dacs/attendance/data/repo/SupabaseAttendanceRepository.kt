package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.AttendanceRecordRow
import com.dacs.attendance.data.remote.GeofenceRow
import com.dacs.attendance.data.remote.ProjectRow
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.WorkDate
import com.dacs.attendance.domain.photoPath
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** The private bucket every attendance photo lives in (migration 0050). */
internal const val PHOTO_BUCKET = "attendance"

/** The picker's source since 0059. See [SupabaseProjectRepository]. */
private const val PROJECTS_FOR_WORKER = "attendance_projects_for_worker"

@Singleton
class SupabaseProjectRepository @Inject constructor(
    private val client: SupabaseClient
) : ProjectRepository {

    /**
     * An RPC, NOT a table read.
     *
     * Migration 0059 retired `attendance_projects` and pointed the picker
     * at the two real project systems. Workers deliberately have no
     * select on `folders` -- the budgets and contract values hanging off
     * it are owner-confidential -- so the server exposes exactly three
     * columns through a security-definer function instead. Reading the
     * underlying tables from here would need a policy that hands every
     * mason the contract amounts.
     *
     * Scoping, ordering and the Additional Works exclusion all live in
     * that function. Re-applying any of it here would be a second,
     * drifting copy of a rule the database already enforces.
     */
    override suspend fun activeProjects(): Result<List<AttendanceProject>> =
        runCatchingExceptCancellation {
            client.postgrest
                .rpc(PROJECTS_FOR_WORKER)
                .decodeList<ProjectRow>()
                .mapNotNull { it.toDomain() }
                .let { attachGeofences(it) }
        }

    /**
     * Fences are fetched SEPARATELY and merged, rather than being added
     * to the RPC's three columns.
     *
     * 0059 gave attendance_projects_for_worker() exactly three columns on
     * purpose: workers have no select on `folders`, whose contract values
     * are owner-confidential, and a three-column function cannot leak a
     * fourth. Widening it to carry coordinates would reopen that.
     *
     * A failure here is swallowed: the projects are what the picker
     * needs, and a missing fence costs a pre-check, not the flow. The
     * server still verifies.
     */
    private suspend fun attachGeofences(
        projects: List<AttendanceProject>
    ): List<AttendanceProject> = runCatchingExceptCancellation {
        val newest = client.postgrest
            .from("attendance_project_geofence")
            .select(Columns.raw(GeofenceRow.COLUMNS))
            .decodeList<GeofenceRow>()
            // Append-only history: keep the latest row per project, which
            // is the only one the device can act on.
            .filter { it.projectKey != null }
            .sortedBy { it.effectiveFrom.orEmpty() }
            .associateBy { it.projectKey!! }

        projects.map { project ->
            newest[project.key]?.let { project.copy(geofence = it.toDomain()) } ?: project
        }
    }.getOrDefault(projects)
}

@Singleton
class SupabaseAttendanceRepository @Inject constructor(
    private val client: SupabaseClient
) : AttendanceRepository {

    /**
     * Photo first, then the RPC.
     *
     * That order is deliberate: the RPC stores a photo PATH and does not
     * verify the object exists, so writing the record first could leave a
     * record pointing at a photo that never uploaded -- an attendance row
     * with no evidence. Failing before the record is written just means
     * the worker retries, and the retry carries the same event_id, so it
     * cannot double-record.
     */
    override suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord> =
        runCatchingExceptCancellation {
            val workerId = client.auth.currentUserOrNull()?.id
                ?: error("AUTH_REQUIRED")

            val path = photoPath(
                workerId = workerId,
                workDate = WorkDate.of(request.capturedAt),
                direction = request.direction,
                eventId = request.eventId
            )

            client.storage.from(PHOTO_BUCKET).upload(path, request.photo.readBytes()) {
                // A retry re-uploads over the same path. Without upsert the
                // second attempt fails on "already exists" and the worker
                // is stuck holding a photo they cannot submit.
                upsert = true
            }

            val function = when (request.direction) {
                TimeDirection.IN -> "attendance_time_in"
                TimeDirection.OUT -> "attendance_time_out"
            }

            client.postgrest.rpc(
                function,
                buildJsonObject {
                    // The PAIR, never the id alone: 'pc' ids come from
                    // folders and 'pm' ids from construction_projects, and
                    // the server resolves the name from both together.
                    put("p_project_system", request.projectSystem.wire)
                    put("p_project_id", request.projectId)
                    put("p_captured_at", request.capturedAt.toString())
                    put("p_photo_path", path)
                    put("p_event_id", request.eventId)
                    if (request.description != null) {
                        put("p_description", request.description)
                    } else {
                        put("p_description", JsonNull)
                    }
                    put("p_lat", request.latitude)
                    put("p_lng", request.longitude)
                    put("p_accuracy_m", request.accuracyMetres)
                    put("p_was_offline", request.wasOffline)
                    // New in 0069, and defaulted server-side, so an older
                    // build that omits them still resolves.
                    put("p_is_mock", request.isMock)
                    put("p_permission_denied", request.permissionDenied)
                }
            ).decodeAs<AttendanceRecordRow>().toDomain()
        }

    override suspend fun history(
        fromWorkDate: String,
        toWorkDate: String
    ): Result<List<AttendanceRecord>> =
        runCatchingExceptCancellation {
            client.postgrest
                .from("attendance_records")
                .select(Columns.raw(AttendanceRecordRow.COLUMNS)) {
                    filter {
                        gte("work_date", fromWorkDate)
                        lte("work_date", toWorkDate)
                    }
                    order("work_date", Order.DESCENDING)
                }
                .decodeList<AttendanceRecordRow>()
                .map { it.toDomain() }
        }

    /**
     * Today's record, keyed on the MANILA date -- the same key the RPC
     * derives. Using the device's date would ask for the wrong day on a
     * phone whose zone is off, and the worker would see "no record" while
     * one existed.
     */
    override suspend fun today(): Result<AttendanceRecord?> =
        runCatchingExceptCancellation {
            client.postgrest
                .from("attendance_records")
                .select(Columns.raw(AttendanceRecordRow.COLUMNS)) {
                    filter { eq("work_date", WorkDate.today().toString()) }
                    limit(1)
                }
                .decodeSingleOrNull<AttendanceRecordRow>()
                ?.toDomain()
        }
}
