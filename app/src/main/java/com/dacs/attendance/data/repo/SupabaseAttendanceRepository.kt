package com.dacs.attendance.data.repo

import com.dacs.attendance.data.remote.AttendanceRecordRow
import com.dacs.attendance.data.remote.ProjectRow
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
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

private const val PHOTO_BUCKET = "attendance"

@Singleton
class SupabaseProjectRepository @Inject constructor(
    private val client: SupabaseClient
) : ProjectRepository {

    /**
     * RLS does the tenant scoping (0051: `owner_id = attendance_data_owner()`
     * and `is_active`), so this deliberately does not filter by owner --
     * a client-side owner filter would be a second, drifting copy of a
     * rule the database already enforces.
     */
    override suspend fun activeProjects(): Result<List<AttendanceProject>> =
        runCatchingExceptCancellation {
            client.postgrest
                .from("attendance_projects")
                .select(Columns.raw(ProjectRow.COLUMNS)) {
                    filter { eq("is_active", true) }
                    order("name", Order.ASCENDING)
                }
                .decodeList<ProjectRow>()
                .map { it.toDomain() }
        }
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
