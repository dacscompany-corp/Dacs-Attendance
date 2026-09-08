package com.dacs.attendance.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A submission that has not reached the server yet.
 *
 * This table IS the worker's day until the upload succeeds. Losing a row
 * here loses a Time In that the worker was told was recorded -- so it is
 * written before the confirmation screen is shown, never after.
 *
 * [eventId] is the primary key because it is also the RPC's idempotency
 * key: the same row replayed any number of times produces exactly one
 * server record.
 */
@Entity(tableName = "pending_submission")
data class PendingSubmissionEntity(
    @PrimaryKey val eventId: String,
    /**
     * WHOSE submission this is. Phones are shared on a site, and the RPC
     * files the record against auth.uid() -- so a row uploaded under a
     * different session would attribute one worker's attendance to
     * another. Never send a row that is not the signed-in worker's.
     */
    val workerId: String = "",
    /** "IN" or "OUT" -- stored as text so the table is readable in a dump. */
    val direction: String,
    /**
     * "pc" or "pm" -- WHICH project list [projectId] belongs to. Empty on
     * rows queued before 0059, which can never be sent: the project list
     * they referenced no longer exists. Those rows are flagged failed by
     * the v2->v3 migration rather than left to retry forever.
     */
    val projectSystem: String = "",
    /** A uuid since 0059, and only unique within [projectSystem]. */
    val projectId: String,
    /** Snapshotted so the queue can render without the project list. */
    val projectName: String,
    /** Epoch millis of the SHUTTER, not of the upload. */
    val capturedAt: Long,
    val photoLocalPath: String,
    val description: String?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMetres: Double?,
    /** True when the device had no connection at capture time. Admin-facing only. */
    val wasOffline: Boolean,
    /**
     * What the device observed about being located, at the SHUTTER.
     *
     * Only [isMock] and [permissionDenied] are sent: the server cannot
     * see either (both arrive as null coordinates) and only the device
     * can tell them apart. Everything else about the location -- the
     * distance, the fence, the verdict -- the server recomputes from its
     * own effective-dated geofence, and that answer is the one stored.
     *
     * [locationStatus] is therefore LOCAL ONLY, kept so a queued row can
     * explain itself on screen. Deliberately not uploaded: a second copy
     * of a verdict the server also computes is a copy that can disagree.
     */
    val isMock: Boolean = false,
    val permissionDenied: Boolean = false,
    val locationStatus: String? = null,
    val attempts: Int = 0,
    /** The last refusal, kept so a permanently failed row can explain itself. */
    val lastError: String?= null,
    /** Set when the queue has given up; the worker must be told. */
    val failedPermanently: Boolean = false,
    val createdAt: Long
)

/**
 * The worker's own attendance rows, mirrored locally.
 *
 * Without this the dashboard is blank with no signal, which is when the
 * worker most needs to know whether they have timed in.
 */
@Entity(tableName = "cached_record", primaryKeys = ["workerId", "workDate"])
data class CachedRecordEntity(
    /** The mirror is per worker: the next person to sign in on this
     *  phone must not see the last one's attendance. */
    val workerId: String = "",
    val workDate: String,
    val id: String?,
    val status: String,
    val timeInAt: Long?,
    val timeOutAt: Long?,
    val timeInProjectName: String?,
    val timeOutProjectName: String?,
    val totalMinutes: Int?,
    /** True while a submission for this day is still queued. */
    val pending: Boolean = false
)

/**
 * The last-known active project list.
 *
 * Without it the picker is empty offline and the entire flow is dead at
 * step 1 -- the spec calls this out explicitly.
 */
@Entity(tableName = "cached_project", primaryKeys = ["workerId", "system", "id"])
data class CachedProjectEntity(
    /** Projects belong to a worker's OWNER, so they are cached per
     *  worker too -- a different worker may have a different owner. */
    val workerId: String = "",
    /**
     * "pc" (folders) or "pm" (construction_projects). Part of the key,
     * not a label: the two systems are separate id spaces, so an id is
     * only unique alongside the system it came from.
     */
    val system: String,
    val id: String,
    val name: String,
    /**
     * The project's fence, cached so the device can pre-check at the
     * shutter with no signal.
     *
     * The device only ever holds the CURRENT fence. The server keeps them
     * effective-dated (migration 0068) and re-checks against whichever
     * one was in force at capture, which is why its answer is the
     * authoritative one and this is only a pre-check.
     *
     * Null means no fence configured. That is not a refusal on its own --
     * see LocationVerification.locationRefuses.
     */
    val geofenceLat: Double? = null,
    val geofenceLng: Double? = null,
    val geofenceRadiusM: Double? = null,
    val geofenceEnabled: Boolean = true
)
