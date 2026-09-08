package com.dacs.attendance.data.repo

import com.dacs.attendance.data.local.CachedProjectEntity
import com.dacs.attendance.data.local.Connectivity
import com.dacs.attendance.data.local.CachedRecordEntity
import com.dacs.attendance.data.local.PendingSubmissionEntity
import com.dacs.attendance.data.local.PhotoStore
import com.dacs.attendance.data.local.AttendanceDatabase
import com.dacs.attendance.domain.AttendanceProject
import com.dacs.attendance.domain.AttendanceRecord
import com.dacs.attendance.domain.AttendanceStatus
import com.dacs.attendance.domain.Geofence
import com.dacs.attendance.domain.ProjectSystem
import com.dacs.attendance.domain.TimeDirection
import com.dacs.attendance.domain.TodayDecision
import com.dacs.attendance.domain.reconcileToday
import com.dacs.attendance.domain.WorkDate
import com.dacs.attendance.work.SubmissionScheduler
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The offline-first attendance repository.
 *
 * **The UI never waits on the network to confirm attendance.** SUBMIT
 * writes the photo, queues the submission, updates the local mirror and
 * returns -- all on the device. The upload happens in WorkManager,
 * afterwards, and may take until the worker walks back into signal.
 *
 * This is the whole reason the app is native rather than a PWA.
 */
@Singleton
class OfflineAttendanceRepository @Inject constructor(
    private val database: AttendanceDatabase,
    private val photos: PhotoStore,
    private val scheduler: SubmissionScheduler,
    private val remote: SupabaseAttendanceRepository,
    private val connectivity: Connectivity,
    private val client: SupabaseClient
) : AttendanceRepository {

    /**
     * The signed-in worker, and the scope for EVERY local read and write.
     *
     * Empty when there is no session. That is deliberately useless as a
     * key: with no session we must not read or write anyone's mirror,
     * because we cannot know whose it would be.
     */
    private fun currentWorkerId(): String = client.auth.currentUserOrNull()?.id.orEmpty()

    /**
     * Returns as soon as the submission is durably on disk. The record
     * the caller gets back is the LOCAL mirror -- optimistic, marked
     * pending, and reconciled to the server's own row once the upload
     * lands.
     */
    override suspend fun submit(request: SubmissionRequest): Result<AttendanceRecord> =
        runCatchingExceptCancellation {
            val workerId = currentWorkerId()
            val workDate = WorkDate.of(request.capturedAt).toString()
            // Matched on the PAIR. Since 0059 an id alone is ambiguous:
            // 'pc' ids come from folders and 'pm' ids from
            // construction_projects, and nothing stops the two colliding.
            val projectName = database.cachedProjects().all(workerId)
                .firstOrNull {
                    it.id == request.projectId && it.system == request.projectSystem.wire
                }?.name
                ?: ""

            // Caption burned in and compressed BEFORE the row is queued:
            // a pending row must point at the exact bytes that will be
            // uploaded, not at a cache file Android may evict.
            val stored = photos.prepare(
                source = request.photo,
                eventId = request.eventId,
                projectName = projectName,
                capturedAt = request.capturedAt
            )

            database.pendingSubmissions().insert(
                PendingSubmissionEntity(
                    eventId = request.eventId,
                    workerId = workerId,
                    direction = request.direction.name,
                    projectSystem = request.projectSystem.wire,
                    projectId = request.projectId,
                    projectName = projectName,
                    capturedAt = request.capturedAt.toEpochMilli(),
                    photoLocalPath = stored.absolutePath,
                    description = request.description,
                    latitude = request.latitude,
                    longitude = request.longitude,
                    accuracyMetres = request.accuracyMetres,
                    // Decided HERE, at capture time -- not by the worker
                    // that uploads it later, when signal is back by
                    // definition. Getting this wrong makes an offline
                    // record look like a tampered clock to the admin.
                    wasOffline = !connectivity.isOnline(),
                    // Frozen at the shutter, exactly like capturedAt. The
                    // upload happens later and from wherever the worker
                    // happens to be standing then; re-reading the location
                    // at send time would record the wrong place entirely.
                    isMock = request.isMock,
                    permissionDenied = request.permissionDenied,
                    locationStatus = request.locationStatus,
                    createdAt = System.currentTimeMillis()
                )
            )

            val mirror = mirrorAfter(workerId, request, workDate, projectName)
            database.cachedRecords().upsert(mirror)

            // KEEP semantics inside: enqueuing twice for one event id is
            // a no-op, so a double tap cannot produce two uploads.
            scheduler.enqueue(request.eventId)

            mirror.toDomain()
        }

    /**
     * Today, from the mirror. The network is consulted opportunistically
     * and only to CORRECT the mirror -- never to decide whether there is
     * anything to show, because with no signal the answer would always
     * be "nothing", which is a lie the worker acts on.
     */
    override suspend fun today(): Result<AttendanceRecord?> {
        val workerId = currentWorkerId()
        val workDate = WorkDate.today().toString()
        val cached = database.cachedRecords().forDate(workerId, workDate)
        val hasPending = database.pendingSubmissions().sendable(workerId).any {
            WorkDate.of(Instant.ofEpochMilli(it.capturedAt)).toString() == workDate
        }
        val fresh = remote.today()

        return when (val decision = reconcileToday(fresh, cached?.toDomain(), hasPending)) {
            is TodayDecision.Use -> {
                // Only write back what the SERVER said; a mirror rewritten
                // from itself just churns the disk.
                if (fresh.isSuccess && fresh.getOrNull() != null) {
                    database.cachedRecords().upsert(decision.record.toEntity(workerId, hasPending))
                }
                Result.success(decision.record.copy(pending = hasPending))
            }

            TodayDecision.Clear -> {
                database.cachedRecords().clear(workerId, workDate)
                Result.success(null)
            }

            // Unknown: report the failure rather than inventing an empty
            // day the worker would act on.
            TodayDecision.Unknown -> fresh
        }
    }

    /**
     * History, mirrored as it is fetched.
     *
     * Every successful fetch writes the days into cached_record, so the
     * next time the worker opens History with no signal the same list is
     * still there. That is what the mirror is FOR -- it is not a cache of
     * convenience, it is the offline copy of the worker's own record.
     */
    override suspend fun history(
        fromWorkDate: String,
        toWorkDate: String
    ): Result<List<AttendanceRecord>> {
        val workerId = currentWorkerId()
        val fresh = remote.history(fromWorkDate, toWorkDate)

        fresh.getOrNull()?.let { records ->
            records.forEach {
                database.cachedRecords().upsert(it.toEntity(workerId, pending = false))
            }
            return Result.success(records)
        }

        val cached = database.cachedRecords()
            .between(workerId, fromWorkDate, toWorkDate)
            .map { it.toDomain() }
        // An empty mirror and an unreachable server are different answers:
        // returning success(emptyList) here would tell a worker they never
        // worked this week.
        return if (cached.isNotEmpty()) Result.success(cached) else fresh
    }

    private suspend fun mirrorAfter(
        workerId: String,
        request: SubmissionRequest,
        workDate: String,
        projectName: String
    ): CachedRecordEntity {
        val existing = database.cachedRecords().forDate(workerId, workDate)
        return when (request.direction) {
            TimeDirection.IN -> CachedRecordEntity(
                workerId = workerId,
                workDate = workDate,
                id = existing?.id,
                status = AttendanceStatus.WORKING.name.lowercase(),
                timeInAt = request.capturedAt.toEpochMilli(),
                timeOutAt = null,
                timeInProjectName = projectName,
                timeOutProjectName = null,
                totalMinutes = null,
                pending = true
            )

            TimeDirection.OUT -> {
                val timeIn = existing?.timeInAt
                existing?.copy(
                    status = AttendanceStatus.COMPLETE.name.lowercase(),
                    timeOutAt = request.capturedAt.toEpochMilli(),
                    timeOutProjectName = projectName,
                    // Optimistic, and replaced by the server's own
                    // calculation the moment the upload lands. The
                    // server stays the authority on hours.
                    totalMinutes = timeIn?.let {
                        ((request.capturedAt.toEpochMilli() - it) / 60_000).toInt()
                    },
                    pending = true
                ) ?: CachedRecordEntity(
                    workerId = workerId,
                    workDate = workDate,
                    id = null,
                    status = AttendanceStatus.COMPLETE.name.lowercase(),
                    timeInAt = null,
                    timeOutAt = request.capturedAt.toEpochMilli(),
                    timeInProjectName = null,
                    timeOutProjectName = projectName,
                    totalMinutes = null,
                    pending = true
                )
            }
        }
    }
}

/**
 * Projects, cached. The picker being empty offline would kill the flow
 * at step 1, so the last-known list is kept and served whenever the
 * network cannot answer.
 */
@Singleton
class OfflineProjectRepository @Inject constructor(
    private val database: AttendanceDatabase,
    private val remote: SupabaseProjectRepository,
    private val client: SupabaseClient
) : ProjectRepository {

    override suspend fun activeProjects(): Result<List<AttendanceProject>> {
        // Projects belong to the worker's OWNER, so the cache is per
        // worker: a different worker on this phone may have a different
        // owner, and must not inherit this one's project list.
        val workerId = client.auth.currentUserOrNull()?.id.orEmpty()
        val fresh = remote.activeProjects()
        fresh.getOrNull()?.let { projects ->
            if (projects.isNotEmpty()) {
                database.cachedProjects().replaceAll(
                    workerId,
                    projects.map {
                        CachedProjectEntity(
                            workerId = workerId,
                            system = it.system.wire,
                            id = it.id,
                            name = it.name,
                            geofenceLat = it.geofence?.latitude,
                            geofenceLng = it.geofence?.longitude,
                            geofenceRadiusM = it.geofence?.radiusMetres,
                            geofenceEnabled = it.geofence?.enabled ?: true
                        )
                    }
                )
            }
            return Result.success(projects)
        }

        // A cached row whose system this build no longer knows is dropped
        // rather than guessed at: offering a project the flow cannot
        // submit against just moves the failure four screens later.
        val cached = database.cachedProjects().all(workerId)
            .mapNotNull { row ->
                ProjectSystem.of(row.system)?.let { system ->
                    AttendanceProject(
                        system = system,
                        id = row.id,
                        name = row.name,
                        // Rebuilt only when all three parts are present.
                        // A half-cached fence would be a circle nobody
                        // drew, and the pre-check would judge against it.
                        geofence = if (row.geofenceLat != null &&
                            row.geofenceLng != null &&
                            row.geofenceRadiusM != null
                        ) {
                            Geofence(
                                latitude = row.geofenceLat,
                                longitude = row.geofenceLng,
                                radiusMetres = row.geofenceRadiusM,
                                enabled = row.geofenceEnabled
                            )
                        } else {
                            null
                        }
                    )
                }
            }
        return if (cached.isNotEmpty()) Result.success(cached) else fresh
    }
}

// ── mapping ─────────────────────────────────────────────────────────

internal fun CachedRecordEntity.toDomain() = AttendanceRecord(
    id = id ?: workDate,
    workDate = workDate,
    status = AttendanceStatus.parse(status),
    timeInAt = timeInAt?.let(Instant::ofEpochMilli),
    timeOutAt = timeOutAt?.let(Instant::ofEpochMilli),
    timeInProjectName = timeInProjectName,
    timeOutProjectName = timeOutProjectName,
    totalMinutes = totalMinutes,
    pending = pending
)

internal fun AttendanceRecord.toEntity(workerId: String, pending: Boolean) = CachedRecordEntity(
    workerId = workerId,
    workDate = workDate,
    id = id,
    status = status.name.lowercase(),
    timeInAt = timeInAt?.toEpochMilli(),
    timeOutAt = timeOutAt?.toEpochMilli(),
    timeInProjectName = timeInProjectName,
    timeOutProjectName = timeOutProjectName,
    totalMinutes = totalMinutes,
    pending = pending
)
