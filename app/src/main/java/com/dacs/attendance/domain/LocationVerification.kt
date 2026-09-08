package com.dacs.attendance.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Where the worker is, judged against where the project is.
 *
 * ── THIS IS A PRE-CHECK, NOT THE VERDICT. The server verifies again in
 *    `attendance_time_in` / `attendance_time_out` (migration 0069) and
 *    its answer is the one that gets stored. This exists so an OFFLINE
 *    worker gets an immediate, honest answer at the shutter instead of
 *    being told "saved" and refused days later when the queue drains.
 *
 *    Every rule below is written to match 0069's `attendance_check_location`
 *    exactly. They are two implementations of one rule, and if they
 *    drift the app will accept things the server then destroys.
 *
 * ── REFUSALS ARE ONLY FOR WHAT IS KNOWN BAD. Uncertainty is recorded
 *    and flagged, never refused. A cheap phone under scaffolding that
 *    cannot hold a fix must still be able to record attendance -- with
 *    the reward attached to a missing day, refusing there would cost a
 *    worker ₱500 for their employer's choice of hardware.
 */
enum class LocationStatus {
    /** Inside the fence, with an accuracy worth trusting. */
    Verified,

    /** Demonstrably somewhere else. The one refusal the worker earns. */
    OutsideRadius,

    /** A fix, but too vague to place. Recorded, flagged. */
    LowAccuracy,

    /** No fix at all, though the permission was given. Recorded, flagged. */
    LocationUnavailable,

    /** A fake provider. Never accidental. */
    MockLocation,

    /** The worker declined to be located. A choice, not a limitation. */
    PermissionDenied,

    /** No fence configured for this project, or it is switched off. */
    ProjectGeofenceUnavailable;

    /** The wire value the RPC and the admin screens use. */
    val wire: String
        get() = when (this) {
            Verified -> "verified"
            OutsideRadius -> "outside_radius"
            LowAccuracy -> "low_accuracy"
            LocationUnavailable -> "location_unavailable"
            MockLocation -> "mock_location"
            PermissionDenied -> "permission_denied"
            ProjectGeofenceUnavailable -> "project_geofence_unavailable"
        }
}

/**
 * A project's fence, as the device last cached it.
 *
 * The server keeps these effective-dated (migration 0068) so that editing
 * a fence cannot retroactively invalidate old attendance. The device only
 * ever holds the CURRENT one, which is why the server checks again: it
 * can resolve the fence that was in force at the moment of capture, and
 * the phone cannot.
 */
data class Geofence(
    val latitude: Double,
    val longitude: Double,
    val radiusMetres: Double,
    val enabled: Boolean = true
)

/** What the device managed to observe at the shutter. */
data class DeviceFix(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyMetres: Double? = null,
    /**
     * The OS reported a mock provider. Client-reported and defeatable on
     * a rooted device, so it catches casual spoofing and not determined
     * spoofing -- the record says what was observed, never that it is
     * certain.
     */
    val isMock: Boolean = false,
    /** The worker declined the permission, as distinct from getting no fix. */
    val permissionDenied: Boolean = false
)

data class LocationCheck(
    val status: LocationStatus,
    /** Null when there was no fix, or no fence to measure against. */
    val distanceMetres: Double? = null,
    /** The fence actually used, for the record's snapshot. */
    val geofence: Geofence? = null
)

/** Mean earth radius, matching `attendance_distance_m` in migration 0068. */
private const val EARTH_RADIUS_M = 6_371_000.0

/**
 * Haversine, in metres.
 *
 * The same formula and the same radius as the SQL, on purpose: two
 * implementations that round differently would put a worker inside the
 * fence on the phone and outside it on the server. At a 150 m fence the
 * spherical approximation is well under a metre out.
 */
fun distanceMetres(
    lat1: Double,
    lng1: Double,
    lat2: Double,
    lng2: Double
): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(a))
}

/**
 * The check, in the same order the server applies it.
 *
 * Distance is computed whenever there is both a fix and a fence, even
 * when the status is already settled -- an admin reviewing a flagged
 * record wants to know how far off it was, not merely that it was
 * uncertain.
 */
fun checkLocation(
    fix: DeviceFix,
    fence: Geofence?,
    minAccuracyMetres: Double
): LocationCheck {
    val distance =
        if (fence != null && fix.latitude != null && fix.longitude != null) {
            distanceMetres(fence.latitude, fence.longitude, fix.latitude, fix.longitude)
        } else {
            null
        }

    val status = when {
        fix.permissionDenied -> LocationStatus.PermissionDenied
        fix.isMock -> LocationStatus.MockLocation
        fix.latitude == null || fix.longitude == null -> LocationStatus.LocationUnavailable
        fix.accuracyMetres == null || fix.accuracyMetres > minAccuracyMetres ->
            LocationStatus.LowAccuracy
        fence == null || !fence.enabled -> LocationStatus.ProjectGeofenceUnavailable
        distance != null && distance > fence.radiusMetres -> LocationStatus.OutsideRadius
        else -> LocationStatus.Verified
    }

    return LocationCheck(status = status, distanceMetres = distance, geofence = fence)
}

/**
 * Whether the app should refuse to record this at all.
 *
 * ── DELIBERATELY THE STRICT RULE, with no offline softening.
 *
 *    The server relaxes `outside_radius` for a QUEUED record, because by
 *    then the worker has already been shown "saved" and destroying it
 *    would be worse than flagging it (0069). That relaxation exists to
 *    cover a DISAGREEMENT -- a fence edited after capture, or a device
 *    that held no fence at all.
 *
 *    It is not licence for the app to queue something it can already see
 *    is wrong. At the shutter, offline or not, the phone knows the
 *    worker is outside the fence, so it says so then and there. Applying
 *    the softened rule here would let the app knowingly queue records it
 *    expects the server to flag, which is how a review queue fills with
 *    work nobody caused.
 */
fun locationRefuses(status: LocationStatus, requireGeofence: Boolean): Boolean =
    when (status) {
        LocationStatus.MockLocation,
        LocationStatus.PermissionDenied,
        LocationStatus.OutsideRadius -> true

        LocationStatus.ProjectGeofenceUnavailable -> requireGeofence

        LocationStatus.Verified,
        LocationStatus.LowAccuracy,
        LocationStatus.LocationUnavailable -> false
    }
